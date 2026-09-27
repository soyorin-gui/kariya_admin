package org.lbl.auth.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.lbl.config.SecurityProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.lbl.realtime.RealtimeGateway;

import java.security.SecureRandom;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;

@Service
public class SessionService {
    private static final Duration ONBOARDING_IDLE_TTL = Duration.ofMinutes(30);
    private static final Duration ONBOARDING_ABSOLUTE_TTL = Duration.ofHours(2);
    private final StringRedisTemplate redis;
    private final SecurityProperties security;
    private final ObjectMapper json;
    private final SecureRandom random = new SecureRandom();
    private final RealtimeGateway realtime;

    public SessionService(StringRedisTemplate redis, SecurityProperties security, ObjectMapper json, RealtimeGateway realtime) {
        this.redis = redis;
        this.security = security;
        this.json = json;
        this.realtime = realtime;
    }

    /** 创建本地密码登录的会话。 */
    public String create(Long userId, String username, long authVersion, boolean rememberMe,
                         boolean passwordChangeRequired) {
        return createMember(userId, username, authVersion, rememberMe, "PASSWORD", "local", passwordChangeRequired);
    }

    public String createMember(Long userId, String username, long authVersion, boolean rememberMe,
                               String authMethod, String providerKey) {
        return createMember(userId, username, authVersion, rememberMe, authMethod, providerKey, false);
    }

    public String createMember(Long userId, String username, long authVersion, boolean rememberMe,
                               String authMethod, String providerKey, boolean passwordChangeRequired) {
        String sid = token();
        Duration ttl = ttl(rememberMe);
        LoginSession session = LoginSession.member(userId, username, authVersion, rememberMe, authMethod, providerKey,
                passwordChangeRequired);
        redis.opsForValue().set(sessionKey(sid), serialize(session), ttl);
        if (rememberMe) redis.opsForValue().set(absoluteKey(sid), "1", absoluteTtl());
        redis.opsForSet().add(userKey(userId), sid);
        extendUserIndex(userId, rememberMe ? absoluteTtl() : ttl);
        return sid;
    }

    /**
     * 创建开户确认态会话。
     * <p>
     * {@code rememberMe} 只是把用户在第三方登录入口的选择<b>带过</b>这一跳（见
     * {@link LoginSession#onboarding}），供创建/绑定账号时决定正式会话的有效期。
     * 这里的两个 TTL <b>故意不随 rememberMe 变化</b>：开户态自身的语义是
     * "没注册、没绑定就失效"，勾了记住我也不该延长它。
     */
    public String createOnboarding(String providerKey, String issuer, String subject, String displayName,
                              String email, String employeeNo, boolean rememberMe) {
        String sid = token();
        LoginSession session = LoginSession.onboarding(token(), providerKey, issuer, subject, displayName, email,
                employeeNo, rememberMe);
        redis.opsForValue().set(sessionKey(sid), serialize(session), ONBOARDING_IDLE_TTL);
        redis.opsForValue().set(absoluteKey(sid), "1", ONBOARDING_ABSOLUTE_TTL);
        return sid;
    }

    public LoginSession find(String sid) {
        String data = redis.opsForValue().get(sessionKey(sid));
        if (data == null) return null;
        LoginSession session = deserialize(data);
        if (session == null) {
            redis.delete(sessionKey(sid));
            return null;
        }
        if ((session.rememberMe() || session.onboarding()) && Boolean.FALSE.equals(redis.hasKey(absoluteKey(sid)))) {
            redis.delete(sessionKey(sid));
            if (!session.onboarding()) redis.opsForSet().remove(userKey(session.userId()), sid);
            return null;
        }
        return session;
    }

    /** 只延长空闲超时，绝对有效期不会被交互续长。 */
    public boolean touch(String sid) {
        LoginSession session = find(sid);
        if (session == null) return false;
        Duration ttl = session.onboarding() ? ONBOARDING_IDLE_TTL : ttl(session.rememberMe());
        if (session.rememberMe() || session.onboarding()) {
            Long seconds = redis.getExpire(absoluteKey(sid));
            if (seconds == null || seconds <= 0) {
                remove(sid);
                return false;
            }
            ttl = Duration.ofSeconds(Math.min(ttl.toSeconds(), seconds));
            if (!session.onboarding()) extendUserIndex(session.userId(), Duration.ofSeconds(seconds));
        } else {
            extendUserIndex(session.userId(), ttl);
        }
        redis.opsForValue().set(sessionKey(sid), serialize(session.touch()), ttl);
        return true;
    }

    public void updatePasswordChangeRequired(String sid, boolean required) {
        LoginSession session = find(sid);
        if (session == null || session.onboarding()) return;
        Long seconds = redis.getExpire(sessionKey(sid));
        if (seconds == null || seconds <= 0) return;
        redis.opsForValue().set(sessionKey(sid), serialize(session.withPasswordChangeRequired(required)),
                Duration.ofSeconds(seconds));
    }

    public List<SessionView> listMemberSessions(Long userId, String currentSid) {
        Set<String> ids = redis.opsForSet().members(userKey(userId));
        if (ids == null || ids.isEmpty()) return List.of();
        List<SessionView> result = new ArrayList<>();
        for (String sid : ids) {
            LoginSession session = find(sid);
            if (session == null || session.onboarding() || !userId.equals(session.userId())) {
                redis.opsForSet().remove(userKey(userId), sid);
                continue;
            }
            result.add(new SessionView(reference(sid), sid.equals(currentSid), session.createdTime(),
                    session.lastActiveTime(), session.loginIp(), session.userAgent(), session.rememberMe(),
                    session.authMethod(), session.providerKey()));
        }
        return result.stream().sorted((a, b) -> compareTime(b.lastActiveTime(), a.lastActiveTime())).toList();
    }

    public boolean removeByReference(Long userId, String reference) {
        Set<String> ids = redis.opsForSet().members(userKey(userId));
        if (ids == null) return false;
        for (String sid : ids) {
            if (reference(sid).equals(reference)) {
                remove(sid);
                return true;
            }
        }
        return false;
    }

    public int removeAllExcept(Long userId, String retainedSid) {
        Set<String> ids = redis.opsForSet().members(userKey(userId));
        if (ids == null || ids.isEmpty()) return 0;
        int removed = 0;
        for (String sid : ids) {
            if (sid.equals(retainedSid)) continue;
            remove(sid);
            removed++;
        }
        return removed;
    }

    public void remove(String sid) {
        LoginSession session = find(sid);
        redis.delete(sessionKey(sid));
        redis.delete(absoluteKey(sid));
        if (session != null && !session.onboarding()) redis.opsForSet().remove(userKey(session.userId()), sid);
        realtime.closeSession(sid);
    }

    public void removeAll(Long userId) {
        Set<String> ids = redis.opsForSet().members(userKey(userId));
        if (ids != null && !ids.isEmpty()) {
            ids.forEach(realtime::closeSession);
            redis.delete(ids.stream().map(this::sessionKey).toList());
            redis.delete(ids.stream().map(this::absoluteKey).toList());
        }
        redis.delete(userKey(userId));
    }

    private String serialize(LoginSession session) {
        try {
            return json.writeValueAsString(session);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("无法序列化登录会话", ex);
        }
    }

    private LoginSession deserialize(String data) {
        try {
            if (data.startsWith("{")) return json.readValue(data, LoginSession.class);
            String[] parts = data.split("\\|", -1);
            if (parts.length == 4) {
                return LoginSession.member(Long.valueOf(parts[0]), parts[1], Long.parseLong(parts[2]),
                        Boolean.parseBoolean(parts[3]), "PASSWORD", "local", false);
            }
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    private Duration ttl(boolean remember) {
        return Duration.ofHours(remember ? security.rememberedIdleDays() * 24 : security.idleHours());
    }

    private Duration absoluteTtl() { return Duration.ofDays(security.rememberedAbsoluteDays()); }

    private void extendUserIndex(Long userId, Duration desired) {
        Long remaining = redis.getExpire(userKey(userId));
        if (remaining == null || remaining < desired.toSeconds()) redis.expire(userKey(userId), desired);
    }

    private String token() {
        byte[] bytes = new byte[48];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String reference(String sid) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(sid.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).substring(0, 22);
        } catch (Exception ex) {
            throw new IllegalStateException("无法生成会话标识", ex);
        }
    }

    private int compareTime(java.time.LocalDateTime first, java.time.LocalDateTime second) {
        if (first == null && second == null) return 0;
        if (first == null) return -1;
        if (second == null) return 1;
        return first.compareTo(second);
    }

    private String sessionKey(String sid) { return "auth:session:" + sid; }
    private String userKey(Long id) { return "auth:user:sessions:" + id; }
    private String absoluteKey(String sid) { return "auth:session:absolute:" + sid; }
}
