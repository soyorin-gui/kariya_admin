package org.lbl.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.lbl.auth.model.*;
import org.lbl.auth.session.*;
import org.lbl.common.exception.BusinessException;
import org.lbl.common.exception.TooManyRequestsException;
import org.lbl.common.exception.UnauthorizedException;
import org.lbl.system.log.support.RequestInfo;
import org.lbl.security.jwt.JwtService;
import org.lbl.system.log.support.LogResult;
import org.lbl.system.log.service.LoginLogService;
import org.lbl.system.user.entity.UserEntity;
import org.lbl.system.user.mapper.UserMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Locale;

@Service
public class AuthService {
    private final UserMapper users;
    private final PasswordEncoder passwords;
    private final SessionService sessions;
    private final JwtService jwt;
    private final StringRedisTemplate redis;
    private final LoginLogService loginLogs;

    public AuthService(UserMapper users, PasswordEncoder passwords, SessionService sessions, JwtService jwt, StringRedisTemplate redis, LoginLogService loginLogs) {
        this.users = users;
        this.passwords = passwords;
        this.sessions = sessions;
        this.jwt = jwt;
        this.redis = redis;
        this.loginLogs = loginLogs;
    }

    @Transactional
    public LoginResult login(LoginRequest request) {
        String username = request.username().trim();
        String loginKey = normalizeUsername(username);
        String sourceKey = sourceKey(RequestInfo.rateLimitIp());
        if (Boolean.TRUE.equals(redis.hasKey(ipLockKey(sourceKey))) || Boolean.TRUE.equals(redis.hasKey(accountSourceLockKey(loginKey, sourceKey)))) {
            loginLogs.record(username, null, LogResult.LOCKED, "登录尝试触发频率限制，拒绝登录尝试");
            throw new TooManyRequestsException("登录尝试过于频繁，请 15 分钟后再试");
        }
        UserEntity user = users.selectOne(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername, username));
        if (user == null || user.getStatus() != 1 || !passwords.matches(request.password(), user.getPasswordHash())) {
            boolean locked = fail(loginKey, sourceKey);
            loginLogs.record(username, user == null ? null : user.getId(),
                    locked ? LogResult.LOCKED : LogResult.FAILURE,
                    locked ? "连续失败次数过多，账户已锁定 15 分钟" : failureReason(user));
            if (locked) throw new TooManyRequestsException("登录尝试过于频繁，请 15 分钟后再试");
            throw new BusinessException("用户名或密码错误");
        }
        redis.delete(accountSourceFailKey(loginKey, sourceKey));
        // 来源级计数也要清：办公网/出口 NAT 下所有人共享一个来源，若某个同事反复输错密码，
        // 计数攒满 20 次就会把整个出口 IP 锁 15 分钟。登录成功说明这个来源此刻是正常的，
        // 应当放行；账号级计数已在上面单独清除，不会因此削弱对目标账号的保护。
        redis.delete(ipFailKey(sourceKey));
        loginLogs.record(username, user.getId(), LogResult.SUCCESS,
                user.getPasswordChangeRequired() == 1 ? "登录成功，需先修改初始密码" : "登录成功");
        String sid = sessions.create(user.getId(), user.getUsername(), user.getAuthVersion(), request.rememberMe());
        return new LoginResult(jwt.issue(sid, user.getUsername(), user.getAuthVersion()),
                new LoginResult.UserProfile(user.getId(), user.getUsername(), user.getRealName(), user.getPasswordChangeRequired() == 1));
    }

    /**
     * 退出登录：清理服务端会话并记录一条登录日志。
     * <p>
     * 放在 Service 而不是 Controller：Controller 手上只有 Cookie 里的 sid，
     * 而"记下是谁退出的"需要先按 sid 取回会话里的用户名。
     */
    public void logout(String sid) {
        if (sid == null) return;
        LoginSession session = sessions.find(sid);
        sessions.remove(sid);
        if (session != null) loginLogs.record(session.username(), session.userId(), LogResult.SUCCESS, "主动退出登录");
    }

    /**
     * 对外统一返回"用户名或密码错误"，避免账号枚举；但日志里必须区分具体原因，
     * 否则排查"某人说登不上"时根本看不出是密码错、账号被停用还是用户名打错了。
     */
    private String failureReason(UserEntity user) {
        if (user == null) return "用户名不存在";
        if (user.getStatus() != 1) return "账号已被停用";
        return "密码错误";
    }

    public String refresh(String sid) {
        LoginSession s = sessions.find(sid);
        if (s == null) throw new UnauthorizedException("登录状态已失效");
        UserEntity user = users.selectById(s.userId());
        if (user == null || user.getStatus() != 1 || !user.getAuthVersion().equals(s.authVersion())) {
            sessions.remove(sid);
            throw new UnauthorizedException("登录状态已失效");
        }
        if (!sessions.touch(sid)) throw new UnauthorizedException("登录状态已失效");
        return jwt.issue(sid, user.getUsername(), user.getAuthVersion());
    }

    public void touch(String sid) {
        LoginSession session = sessions.find(sid);
        if (session == null) throw new UnauthorizedException("登录状态已失效");
        UserEntity user = users.selectById(session.userId());
        if (user == null || user.getStatus() != 1 || !user.getAuthVersion().equals(session.authVersion()) || !sessions.touch(sid)) {
            sessions.remove(sid);
            throw new UnauthorizedException("登录状态已失效");
        }
    }

    /**
     * A per-account lock alone lets anyone lock a known employee account. We therefore combine
     * a stricter account+source limit with a broader source-wide limit for password spraying.
     */
    private boolean fail(String username, String source) {
        Long accountCount = incrementInWindow(accountSourceFailKey(username, source));
        Long sourceCount = incrementInWindow(ipFailKey(source));
        boolean accountLocked = accountCount != null && accountCount >= 5;
        boolean sourceLocked = sourceCount != null && sourceCount >= 20;
        if (accountLocked) {
            redis.opsForValue().set(accountSourceLockKey(username, source), "1", Duration.ofMinutes(15));
            redis.delete(accountSourceFailKey(username, source));
        }
        if (sourceLocked) {
            redis.opsForValue().set(ipLockKey(source), "1", Duration.ofMinutes(15));
            redis.delete(ipFailKey(source));
        }
        return accountLocked || sourceLocked;
    }

    private Long incrementInWindow(String key) {
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) redis.expire(key, Duration.ofMinutes(15));
        return count;
    }

    private String accountSourceFailKey(String username, String source) {
        return "auth:login:fail:account-source:" + username + ":" + source;
    }

    private String accountSourceLockKey(String username, String source) {
        return "auth:login:lock:account-source:" + username + ":" + source;
    }

    private String ipFailKey(String source) {
        return "auth:login:fail:source:" + source;
    }

    private String ipLockKey(String source) {
        return "auth:login:lock:source:" + source;
    }

    /** Redis rate-limit keys must follow the same account identity regardless of input casing. */
    private String normalizeUsername(String username) {
        return username.toLowerCase(Locale.ROOT);
    }

    /** Do not keep directly identifying IP values in Redis rate-limit key names. */
    private String sourceKey(String ip) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(ip.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
