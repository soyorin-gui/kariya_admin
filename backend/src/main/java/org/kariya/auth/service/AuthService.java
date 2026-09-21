package org.kariya.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.kariya.auth.model.*;
import org.kariya.auth.session.*;
import org.kariya.common.exception.BusinessException;
import org.kariya.security.jwt.JwtService;
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

    public AuthService(UserMapper users, PasswordEncoder passwords, SessionService sessions, JwtService jwt, StringRedisTemplate redis) {
        this.users = users;
        this.passwords = passwords;
        this.sessions = sessions;
        this.jwt = jwt;
        this.redis = redis;
    }

    @Transactional
    public LoginResult login(LoginRequest request) {
        String username = request.username().trim();
        if (Boolean.TRUE.equals(redis.hasKey(lockKey(username))))
            throw new BusinessException("账户已锁定，请 15 分钟后再试");
        UserEntity user = users.selectOne(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername, username));
        if (user == null || user.getStatus() != 1 || !passwords.matches(request.password(), user.getPasswordHash())) {
            if (fail(username)) throw new BusinessException("账户已锁定，请 15 分钟后再试");
            throw new BusinessException("用户名或密码错误");
        }
        redis.delete(failKey(username));
        String sid = sessions.create(user.getId(), user.getUsername(), user.getAuthVersion(), request.rememberMe());
        return new LoginResult(jwt.issue(sid, user.getUsername(), user.getAuthVersion()), new LoginResult.UserProfile(user.getId(), user.getUsername(), user.getRealName()));
    }

    public String refresh(String sid) {
        LoginSession s = sessions.find(sid);
        if (s == null) throw new BusinessException("登录状态已失效");
        UserEntity user = users.selectById(s.userId());
        if (user == null || user.getStatus() != 1 || !user.getAuthVersion().equals(s.authVersion())) {
            sessions.remove(sid);
            throw new BusinessException("登录状态已失效");
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
