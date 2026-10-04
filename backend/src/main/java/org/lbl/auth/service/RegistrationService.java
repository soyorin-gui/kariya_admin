package org.lbl.auth.service;

import static org.lbl.common.util.TextValues.trimToNull;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.lbl.auth.identity.*;
import org.lbl.auth.model.*;
import org.lbl.auth.session.LoginSession;
import org.lbl.auth.session.SessionService;
import org.lbl.common.exception.BusinessException;
import org.lbl.common.exception.TooManyRequestsException;
import org.lbl.security.jwt.JwtService;
import org.lbl.system.log.service.LoginLogService;
import org.lbl.system.log.support.LogResult;
import org.lbl.system.role.entity.RoleEntity;
import org.lbl.system.role.mapper.RoleMapper;
import org.lbl.system.user.entity.UserEntity;
import org.lbl.system.user.mapper.UserMapper;
import org.lbl.system.user.mapper.UserRoleMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class RegistrationService {
    private final UserMapper users;
    private final UserRoleMapper userRoles;
    private final RoleMapper roles;
    private final LocalCredentialMapper credentials;
    private final ExternalIdentityMapper identities;
    private final PasswordEncoder passwords;
    private final SessionService sessions;
    private final JwtService jwt;
    private final LoginAttemptGuard attempts;
    private final LoginLogService loginLogs;
    private final CaptchaService captcha;
    private final PasswordChangePolicy passwordChangePolicy;

    public RegistrationService(UserMapper users, UserRoleMapper userRoles, RoleMapper roles,
                               LocalCredentialMapper credentials, ExternalIdentityMapper identities,
                               PasswordEncoder passwords, SessionService sessions, JwtService jwt,
                               LoginAttemptGuard attempts, LoginLogService loginLogs, CaptchaService captcha,
                               PasswordChangePolicy passwordChangePolicy) {
        this.users = users;
        this.userRoles = userRoles;
        this.roles = roles;
        this.credentials = credentials;
        this.identities = identities;
        this.passwords = passwords;
        this.sessions = sessions;
        this.jwt = jwt;
        this.attempts = attempts;
        this.loginLogs = loginLogs;
        this.captcha = captcha;
        this.passwordChangePolicy = passwordChangePolicy;
    }

    @Transactional
    public SessionGrant register(RegistrationRequest request) {
        // 顺序很重要：验证码校验必须在 createUser（一次 SELECT + 一次 INSERT）和
        // savePassword（一次 BCrypt，约几十毫秒 CPU）之前。
        // 放在后面，"刷接口"的成本就已经付掉了，验证码只剩下挡垃圾账号的作用。
        captcha.verify(request.captchaId(), request.captchaCode());
        PasswordRules.requireConfirmed(request.password(), request.confirmPassword());
        UserEntity user = createUser(request.username(), request.realName(), trimToNull(request.phone()),
                trimToNull(request.email()), "LOCAL", null, false, "basic_role");
        savePassword(user.getId(), request.password(), 0);
        return issue(user, request.rememberMe(), "PASSWORD", "local");
    }

    @Transactional
    public SessionGrant createFromOnboarding(LoginSession onboarding, OnboardingAccountRequest request) {
        requireOnboarding(onboarding);
        PasswordRules.requireConfirmed(request.password(), request.confirmPassword());
        boolean internal = "uias".equalsIgnoreCase(onboarding.providerKey());
        UserEntity user = createUser(request.username(), request.realName(), trimToNull(request.phone()),
                trimToNull(request.email()), onboarding.providerKey().toUpperCase(),
                internal ? onboarding.employeeNo() : null, internal, "basic_role");
        savePassword(user.getId(), request.password(), 0);
        bind(user.getId(), onboarding);
        // 这条路径同样会签发会话，属于"谁在什么时候进的系统"，必须和密码登录一样留痕。
        // （此前只有它失败时才有记录，成功反而没有 —— 审计里最想看到的那部分恰好缺失。）
        loginLogs.record(user.getUsername(), user.getId(), LogResult.SUCCESS,
                "通过外部身份创建账号并登录（" + onboarding.providerKey() + "）");
        // 第二个参数不再写死 false：用户在第三方登录入口是否勾选"记住我"，
        // 存在开户确认态会话里（见 LoginSession#onboarding），这里必须原样采用，
        // 否则勾了"记住我"的人走完开户确认后拿到的是会话级 Cookie，关浏览器就掉登录态。
        return issue(user, onboarding.rememberMe(), "EXTERNAL", onboarding.providerKey());
    }

    @Transactional
    public SessionGrant bindFromOnboarding(LoginSession onboarding, OnboardingBindRequest request) {
        requireOnboarding(onboarding);
        // 这里是"用密码校验一个已有账号"的入口，必须与密码登录共用同一套失败计数与锁定。
        // 否则它是一条完整的旁路：外部认证临时身份只需要任意一个启用的平台账号就能拿到，
        // 拿到以后就能对任意用户名（包括 admin）不限速地猜密码 —— 登录接口那两层限流完全绕开。
        String username = request.username().trim();
        String loginKey = attempts.loginKey(username);
        String source = attempts.currentSource();
        if (attempts.isLocked(loginKey, source)) {
            loginLogs.record(username, null, LogResult.LOCKED, "外部身份绑定账号时触发登录频率限制，拒绝绑定尝试");
            throw new TooManyRequestsException("尝试次数过于频繁，请 15 分钟后再试");
        }
        UserEntity user = users.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getUsername, username));
        LocalCredentialEntity credential = user == null ? null : credentials.selectById(user.getId());
        if (user == null || user.getStatus() != 1 || credential == null || credential.getEnabled() != 1
                || !passwords.matches(request.password(), credential.getPasswordHash())) {
            boolean locked = attempts.recordFailure(loginKey, source);
            loginLogs.record(username, user == null ? null : user.getId(),
                    locked ? LogResult.LOCKED : LogResult.FAILURE,
                    locked ? "连续失败次数过多，账号已锁定 15 分钟" : bindFailureReason(user, credential));
            if (locked) throw new TooManyRequestsException("尝试次数过于频繁，请 15 分钟后再试");
            throw new BusinessException("用户名或密码错误");
        }
        attempts.recordSuccess(loginKey, source);
        bind(user.getId(), onboarding);
        // 成功也要记：这条记录回答的是"谁在什么时候把哪个外部身份挂到了哪个账号上"，
        // 是排查"这个账号为什么能用微信登录"时的唯一依据。
        loginLogs.record(user.getUsername(), user.getId(), LogResult.SUCCESS,
                "外部身份绑定到已有账号并登录（" + onboarding.providerKey() + "）");
        // 第二个参数不再写死 false：用户在第三方登录入口是否勾选"记住我"，
        // 存在开户确认态会话里（见 LoginSession#onboarding），这里必须原样采用，
        // 否则勾了"记住我"的人走完开户确认后拿到的是会话级 Cookie，关浏览器就掉登录态。
        return issue(user, onboarding.rememberMe(), "EXTERNAL", onboarding.providerKey());
    }

    /**
     * 绑定失败的具体原因，<b>只写日志</b>，对外统一返回"用户名或密码错误"。
     * <p>
     * 与 {@code AuthService#failureReason} 同一思路，但多一种这里特有的情况：
     * 账号存在、却被外部身份创建时没有设置过密码（没有凭据行），
     * 把它记成"密码错误"会让排查"这个人为什么绑不上"时得出错误结论。
     */
    private String bindFailureReason(UserEntity user, LocalCredentialEntity credential) {
        if (user == null) return "用户名不存在";
        if (user.getStatus() != 1) return "账号已被停用";
        if (credential == null || credential.getEnabled() != 1) return "该账号未设置登录密码，无法用密码绑定";
        return "密码错误";
    }

    private UserEntity createUser(String username, String realName, String phone, String email,
                                  String source, String employeeNo, boolean employeeVerified, String roleCode) {
        String normalized = username.trim();
        if (users.countIncludingDeletedByUsername(normalized) > 0) throw new BusinessException("该用户名已被使用");
        UserEntity user = new UserEntity();
        user.setUsername(normalized);
        user.setEmployeeNo(employeeNo);
        user.setEmployeeNoVerified(employeeVerified ? 1 : 0);
        user.setRealName(realName.trim());
        user.setPhone(phone);
        user.setEmail(email);
        user.setDeptId(null);
        user.setRegistrationSource(source);
        user.setStatus(1);
        user.setAuthVersion(1L);
        user.setBuiltin(0);
        users.insert(user);
        RoleEntity role = roles.selectOne(new LambdaQueryWrapper<RoleEntity>()
                .eq(RoleEntity::getRoleCode, roleCode).eq(RoleEntity::getStatus, 1));
        if (role == null) throw new IllegalStateException("缺少内置基础角色：" + roleCode);
        userRoles.insert(user.getId(), role.getId());
        return user;
    }

    private void savePassword(Long userId, String rawPassword, int mustChange) {
        LocalCredentialEntity credential = new LocalCredentialEntity();
        credential.setUserId(userId);
        credential.setPasswordHash(passwords.encode(rawPassword));
        credential.setPasswordChangeRequired(mustChange);
        credential.setEnabled(1);
        credential.setPasswordChangedTime(LocalDateTime.now());
        credentials.insert(credential);
    }

    private void bind(Long userId, LoginSession onboarding) {
        ExternalIdentityEntity existing = identities.find(onboarding.providerKey(), normalizeIssuer(onboarding.issuer()), onboarding.subject());
        if (existing != null) throw new BusinessException("该外部身份已经绑定其他账号");
        boolean hasSameProvider = identities.findByUserId(userId).stream()
                .anyMatch(identity -> identity.getProviderKey().equals(onboarding.providerKey()));
        if (hasSameProvider) throw new BusinessException("该账号已经绑定了同类型的登录身份，请先解除原绑定");
        ExternalIdentityEntity identity = new ExternalIdentityEntity();
        identity.setUserId(userId);
        identity.setProviderKey(onboarding.providerKey());
        identity.setIssuer(normalizeIssuer(onboarding.issuer()));
        identity.setSubject(onboarding.subject());
        identity.setDisplayNameSnapshot(onboarding.displayName());
        identity.setEmailSnapshot(onboarding.email());
        identity.setCreatedTime(LocalDateTime.now());
        identity.setLastLoginTime(LocalDateTime.now());
        identities.insert(identity);
    }

    private SessionGrant issue(UserEntity user, boolean rememberMe, String method, String provider) {
        LocalCredentialEntity credential = credentials.selectById(user.getId());
        boolean mustChange = "PASSWORD".equals(method) && passwordChangePolicy.requiresChange(credential);
        String sid = sessions.createMember(user.getId(), user.getUsername(), user.getAuthVersion(), rememberMe,
                method, provider, mustChange);
        LoginSession session = sessions.find(sid);
        return new SessionGrant(sid, jwt.issue(sid, session),
                new LoginResult.UserProfile(user.getId(), user.getUsername(), user.getRealName(), mustChange), false);
    }

    private void requireOnboarding(LoginSession session) {
        if (session == null || !session.onboarding()) throw new BusinessException("开户确认状态已失效，请重新完成外部认证");
    }

    private String normalizeIssuer(String value) { return value == null ? "" : value; }
}
