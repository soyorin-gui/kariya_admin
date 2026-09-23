package org.lbl.auth.session;

import org.lbl.config.SecurityProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;

@Service
public class SessionService {
    private final StringRedisTemplate redis;
    private final SecurityProperties security;
    private final SecureRandom random = new SecureRandom();

    public SessionService(StringRedisTemplate redis, SecurityProperties security) {
        this.redis = redis;
        this.security = security;
    }

    public String create(Long userId, String username, long authVersion, boolean rememberMe) {
        String sid = token();
        Duration ttl = ttl(rememberMe);
        String data = userId + "|" + username + "|" + authVersion + "|" + rememberMe;
        redis.opsForValue().set(sessionKey(sid), data, ttl);
        if (rememberMe) redis.opsForValue().set(absoluteKey(sid), "1", absoluteTtl());
        redis.opsForSet().add(userKey(userId), sid);
        extendUserIndex(userId, rememberMe ? absoluteTtl() : ttl);
        return sid;
    }

    public LoginSession find(String sid) {
        String data = redis.opsForValue().get(sessionKey(sid));
        if (data == null) return null;
        String[] p = data.split("\\|", -1);
        if (p.length != 4) {
            redis.delete(sessionKey(sid));
            return null;
        }
        LoginSession session = new LoginSession(Long.valueOf(p[0]), p[1], Long.parseLong(p[2]), Boolean.parseBoolean(p[3]));
        // Existing remembered sessions have no absolute-expiry marker. They are deliberately
        // invalidated after this upgrade rather than guessing a creation time and extending them.
        if (session.rememberMe() && Boolean.FALSE.equals(redis.hasKey(absoluteKey(sid)))) {
            redis.delete(sessionKey(sid));
            redis.opsForSet().remove(userKey(session.userId()), sid);
            return null;
        }
        return session;
    }

    /**
     * Extends only the idle timeout. A remembered session additionally has an immutable
     * absolute-expiry marker, so frequent activity can never turn it into a permanent login.
     */
    public boolean touch(String sid) {
        LoginSession session = find(sid);
        if (session == null) return false;
        Duration ttl = ttl(session.rememberMe());
        if (session.rememberMe()) {
            Long seconds = redis.getExpire(absoluteKey(sid));
            if (seconds == null || seconds <= 0) {
                remove(sid);
                return false;
            }
            ttl = Duration.ofSeconds(Math.min(ttl.toSeconds(), seconds));
            extendUserIndex(session.userId(), Duration.ofSeconds(seconds));
        } else {
            extendUserIndex(session.userId(), ttl);
        }
        redis.expire(sessionKey(sid), ttl);
        return true;
    }

    public void remove(String sid) {
        LoginSession s = find(sid);
        redis.delete(sessionKey(sid));
        redis.delete(absoluteKey(sid));
        if (s != null) redis.opsForSet().remove(userKey(s.userId()), sid);
    }

    public void removeAll(Long userId) {
        Set<String> ids = redis.opsForSet().members(userKey(userId));
        if (ids != null && !ids.isEmpty()) {
            redis.delete(ids.stream().map(this::sessionKey).toList());
            redis.delete(ids.stream().map(this::absoluteKey).toList());
        }
        redis.delete(userKey(userId));
    }

    public String refreshToken() {
        return token();
    }

    private Duration ttl(boolean remember) {
        return Duration.ofHours(remember ? security.rememberedIdleDays() * 24 : security.idleHours());
    }

    private Duration absoluteTtl() {
        return Duration.ofDays(security.rememberedAbsoluteDays());
    }

    /** Never shorten the per-user index when that user has sessions with different TTLs. */
    private void extendUserIndex(Long userId, Duration desired) {
        Long remaining = redis.getExpire(userKey(userId));
        if (remaining == null || remaining < desired.toSeconds()) redis.expire(userKey(userId), desired);
    }

    private String token() {
        byte[] bytes = new byte[48];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sessionKey(String sid) {
        return "auth:session:" + sid;
    }

    private String userKey(Long id) {
        return "auth:user:sessions:" + id;
    }

    private String absoluteKey(String sid) {
        return "auth:session:absolute:" + sid;
    }
}
