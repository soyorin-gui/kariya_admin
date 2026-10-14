package org.lbl.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.lbl.auth.model.*;
import org.lbl.auth.identity.LocalCredentialEntity;
import org.lbl.auth.identity.LocalCredentialMapper;
import org.lbl.auth.session.*;
import org.lbl.common.exception.BusinessException;
import org.lbl.common.exception.TooManyRequestsException;
import org.lbl.common.exception.UnauthorizedException;
import org.lbl.security.jwt.JwtService;
import org.lbl.system.log.support.LogResult;
import org.lbl.system.log.service.LoginLogService;
import org.lbl.system.user.entity.UserEntity;
import org.lbl.system.user.mapper.UserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserMapper users;
    private final PasswordEncoder passwords;
    private final SessionService sessions;
    private final JwtService jwt;
    private final LoginAttemptGuard attempts;
    private final LoginLogService loginLogs;
    private final LocalCredentialMapper credentials;
    private final PasswordChangePolicy passwordChangePolicy;

    public AuthService(UserMapper users, PasswordEncoder passwords, SessionService sessions, JwtService jwt,
                       LoginAttemptGuard attempts, LoginLogService loginLogs, LocalCredentialMapper credentials,
                       PasswordChangePolicy passwordChangePolicy) {
        this.users = users;
        this.passwords = passwords;
        this.sessions = sessions;
        this.jwt = jwt;
        this.attempts = attempts;
        this.loginLogs = loginLogs;
        this.credentials = credentials;
        this.passwordChangePolicy = passwordChangePolicy;
    }

    @Transactional
    public LoginResult login(LoginRequest request) {
        String username = request.username().trim();
        String loginKey = attempts.loginKey(username);
        String source = attempts.currentSource();
        if (attempts.isLocked(loginKey, source)) {
            loginLogs.record(username, null, LogResult.LOCKED, "登录尝试触发频率限制，拒绝登录尝试");
            throw new TooManyRequestsException("登录尝试过于频繁，请 15 分钟后再试");
        }
        UserEntity user = users.selectOne(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername, username));
        LocalCredentialEntity credential = user == null ? null : credentials.selectById(user.getId());
        if (user == null || user.getStatus() != 1 || credential == null || credential.getEnabled() != 1
                || !passwords.matches(request.password(), credential.getPasswordHash())) {
            boolean locked = attempts.recordFailure(loginKey, source);
            loginLogs.record(username, user == null ? null : user.getId(),
                    locked ? LogResult.LOCKED : LogResult.FAILURE,
                    locked ? "连续失败次数过多，账户已锁定 15 分钟" : failureReason(user));
            if (locked) throw new TooManyRequestsException("登录尝试过于频繁，请 15 分钟后再试");
            throw new BusinessException("用户名或密码错误");
        }
        // 成功即清空该「账号 + 来源」与来源级的失败计数，原因见 LoginAttemptGuard#recordSuccess。
        attempts.recordSuccess(loginKey, source);
        boolean passwordChangeRequired = passwordChangePolicy.requiresChange(credential);
        loginLogs.record(username, user.getId(), LogResult.SUCCESS,
                passwordChangeRequired ? "登录成功，需先更新密码" : "登录成功");
        String sid = sessions.create(user.getId(), user.getUsername(), user.getAuthVersion(), request.rememberMe(),
                passwordChangeRequired);
        return new LoginResult(jwt.issue(sid, user.getUsername(), user.getAuthVersion()),
                new LoginResult.UserProfile(user.getId(), user.getUsername(), user.getRealName(), passwordChangeRequired));
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
        if (session != null && !session.onboarding()) loginLogs.record(session.username(), session.userId(), LogResult.SUCCESS, "主动退出登录");
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

    public RefreshGrant refresh(String sid) {
        LoginSession s = sessions.find(sid);
        if (s == null) throw new UnauthorizedException("登录状态已失效");
        if (s.onboarding()) {
            if (!sessions.touch(sid)) throw new UnauthorizedException("开户确认状态已失效");
            return new RefreshGrant(jwt.issue(sid, sessions.find(sid)), false);
        }
        UserEntity user = users.selectById(s.userId());
        if (user == null || user.getStatus() != 1 || !user.getAuthVersion().equals(s.authVersion())) {
            sessions.remove(sid);
            throw new UnauthorizedException("登录状态已失效");
        }
        boolean passwordChangeRequired = false;
        if ("PASSWORD".equals(s.authMethod())) {
            passwordChangeRequired = passwordChangePolicy.requiresChange(credentials.selectById(user.getId()));
            sessions.updatePasswordChangeRequired(sid, passwordChangeRequired);
        }
        if (!sessions.touch(sid)) throw new UnauthorizedException("登录状态已失效");
        return new RefreshGrant(jwt.issue(sid, user.getUsername(), user.getAuthVersion()), passwordChangeRequired);
    }

    public void touch(String sid) {
        LoginSession session = sessions.find(sid);
        if (session == null) throw new UnauthorizedException("登录状态已失效");
        if (session.onboarding()) {
            if (!sessions.touch(sid)) throw new UnauthorizedException("开户确认状态已失效");
            return;
        }
        UserEntity user = users.selectById(session.userId());
        if (user == null || user.getStatus() != 1 || !user.getAuthVersion().equals(session.authVersion()) || !sessions.touch(sid)) {
            sessions.remove(sid);
            throw new UnauthorizedException("登录状态已失效");
        }
    }

    public record RefreshGrant(String accessToken, boolean passwordChangeRequired) {
    }
}
