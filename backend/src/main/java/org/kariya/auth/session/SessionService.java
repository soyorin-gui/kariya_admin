package org.kariya.auth.session;

import org.kariya.config.SecurityProperties;
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
        redis.opsForSet().add(userKey(userId), sid);
        redis.expire(userKey(userId), ttl);
        return sid;
    }

    public LoginSession find(String sid) {
        String data = redis.opsForValue().get(sessionKey(sid));
        if (data == null) return null;
        String[] p = data.split("\\|", -1);
        return new LoginSession(Long.valueOf(p[0]), p[1], Long.parseLong(p[2]), Boolean.parseBoolean(p[3]));
    }

    public void touch(String sid) {
        LoginSession session = find(sid);
        if (session != null) {
            Duration ttl = ttl(session.rememberMe());
            redis.expire(sessionKey(sid), ttl);
            redis.expire(userKey(session.userId()), ttl);
        }
    }

    public void remove(String sid) {
        LoginSession s = find(sid);
        redis.delete(sessionKey(sid));
        if (s != null) redis.opsForSet().remove(userKey(s.userId()), sid);
    }

    public void removeAll(Long userId) {
        Set<String> ids = redis.opsForSet().members(userKey(userId));
        if (ids != null && !ids.isEmpty()) redis.delete(ids.stream().map(this::sessionKey).toList());
        redis.delete(userKey(userId));
    }

    public String refreshToken() {
        return token();
    }

    private Duration ttl(boolean remember) {
        return Duration.ofHours(remember ? security.rememberedIdleDays() * 24 : security.idleHours());
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
}
