package org.lbl.system.user.service;

import org.lbl.auth.identity.LocalCredentialEntity;
import org.lbl.auth.identity.LocalCredentialMapper;
import org.lbl.auth.service.PasswordRules;
import org.lbl.auth.session.SessionService;
import org.lbl.common.exception.BusinessException;
import org.lbl.common.exception.TooManyRequestsException;
import org.lbl.security.context.AccessPolicy;
import org.lbl.system.role.mapper.RoleMapper;
import org.lbl.system.user.entity.UserEntity;
import org.lbl.system.user.mapper.UserMapper;
import org.lbl.system.user.request.PasswordChangeRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;

/** 密码凭据、错误次数限制以及改密后的会话失效策略。 */
@Service
public class UserPasswordService {
    private static final int MAX_FAILURES = 5;
    private static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);
    private final UserMapper users;
    private final RoleMapper roles;
    private final LocalCredentialMapper credentials;
    private final PasswordEncoder passwords;
    private final SessionService sessions;
    private final AccessPolicy access;
    private final StringRedisTemplate redis;
    private final SecureRandom random = new SecureRandom();

    public UserPasswordService(UserMapper users, RoleMapper roles, LocalCredentialMapper credentials,
                               PasswordEncoder passwords, SessionService sessions, AccessPolicy access,
                               StringRedisTemplate redis) {
        this.users = users;
        this.roles = roles;
        this.credentials = credentials;
        this.passwords = passwords;
        this.sessions = sessions;
        this.access = access;
        this.redis = redis;
    }

    @Transactional
    public String reset(Long id) {
        AccessPolicy.Actor actor = access.actor("system:user:reset-password");
        UserEntity user = require(id);
        access.requireManageUser(actor, user);
        if (!actor.superAdmin() || AccessPolicy.containsSuperAdmin(roles.selectAssignedByUserId(id)))
            throw new BusinessException("仅超级管理员可重置普通用户密码");
        String temporaryPassword = PasswordRules.randomCompliantPassword(random, 16);
        saveCredential(user.getId(), temporaryPassword, 1);
        user.setAuthVersion(user.getAuthVersion() + 1);
        users.updateById(user);
        sessions.removeAll(id);
        return temporaryPassword;
    }

    @Transactional
    public void changeOwn(PasswordChangeRequest request) {
        UserEntity user = access.actor().user();
        String failKey = "auth:password-change:fail:" + user.getId();
        if (failures(failKey) >= MAX_FAILURES) throw new TooManyRequestsException("原密码错误次数过多，请 15 分钟后再试");
        LocalCredentialEntity credential = credentials.selectById(user.getId());
        if (credential == null || credential.getEnabled() != 1) throw new BusinessException("当前账号未设置登录密码");
        boolean oldPasswordMatches = passwords.matches(request.oldPassword(), credential.getPasswordHash());
        if (!oldPasswordMatches || passwords.matches(request.newPassword(), credential.getPasswordHash())) {
            recordFailure(failKey);
            throw new BusinessException("原密码不正确，或新密码与当前密码相同");
        }
        credential.setPasswordHash(passwords.encode(request.newPassword()));
        credential.setPasswordChangeRequired(0);
        credential.setPasswordChangedTime(LocalDateTime.now());
        credentials.updateById(credential);
        user.setAuthVersion(user.getAuthVersion() + 1);
        users.updateById(user);
        sessions.removeAll(user.getId());
        redis.delete(failKey);
    }

    private Long failures(String key) {
        String value = redis.opsForValue().get(key);
        if (value == null) return 0L;
        try { return Long.parseLong(value); } catch (NumberFormatException ignored) { return 0L; }
    }

    private void recordFailure(String key) {
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) redis.expire(key, FAILURE_WINDOW);
    }

    private void saveCredential(Long userId, String rawPassword, int passwordChangeRequired) {
        LocalCredentialEntity credential = credentials.selectById(userId);
        if (credential == null) {
            credential = new LocalCredentialEntity();
            credential.setUserId(userId);
            credential.setEnabled(1);
            credential.setPasswordHash(passwords.encode(rawPassword));
            credential.setPasswordChangeRequired(passwordChangeRequired);
            credential.setPasswordChangedTime(LocalDateTime.now());
            credentials.insert(credential);
        } else {
            credential.setEnabled(1);
            credential.setPasswordHash(passwords.encode(rawPassword));
            credential.setPasswordChangeRequired(passwordChangeRequired);
            credential.setPasswordChangedTime(LocalDateTime.now());
            credentials.updateById(credential);
        }
    }

    private UserEntity require(Long id) {
        UserEntity user = users.selectById(id);
        if (user == null) throw new BusinessException("用户不存在");
        return user;
    }
}
