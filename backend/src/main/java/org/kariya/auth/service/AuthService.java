package org.kariya.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.kariya.auth.model.*;
import org.kariya.auth.session.*;
import org.kariya.common.exception.BusinessException;
import org.kariya.common.exception.UnauthorizedException;
import org.kariya.security.jwt.JwtService;
import org.kariya.system.log.LogResult;
import org.kariya.system.log.LoginLogService;
import org.kariya.system.user.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

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
        if (Boolean.TRUE.equals(redis.hasKey(lockKey(username)))) {
            loginLogs.record(username, null, LogResult.LOCKED, "账户处于锁定状态，拒绝登录尝试");
            throw new BusinessException("账户已锁定，请 15 分钟后再试");
        }
        UserEntity user = users.selectOne(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername, username));
        if (user == null || user.getStatus() != 1 || !passwords.matches(request.password(), user.getPasswordHash())) {
            boolean locked = fail(username);
            loginLogs.record(username, user == null ? null : user.getId(),
                    locked ? LogResult.LOCKED : LogResult.FAILURE,
                    locked ? "连续失败次数过多，账户已锁定 15 分钟" : failureReason(user));
            if (locked) throw new BusinessException("账户已锁定，请 15 分钟后再试");
            throw new BusinessException("用户名或密码错误");
        }
        redis.delete(failKey(username));
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
        return jwt.issue(sid, user.getUsername(), user.getAuthVersion());
    }

    public void touch(String sid) {
        sessions.touch(sid);
    }

    private boolean fail(String username) {
        Long count = redis.opsForValue().increment(failKey(username));
        if (count != null && count == 1) redis.expire(failKey(username), Duration.ofMinutes(15));
        if (count != null && count >= 5) {
            redis.opsForValue().set(lockKey(username), "1", Duration.ofMinutes(15));
            redis.delete(failKey(username));
            return true;
        }
        return false;
    }

    private String failKey(String n) {
        return "auth:login:fail:" + n;
    }

    private String lockKey(String n) {
        return "auth:login:lock:" + n;
    }
}
