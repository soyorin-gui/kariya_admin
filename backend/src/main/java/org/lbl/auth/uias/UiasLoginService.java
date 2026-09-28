package org.lbl.auth.uias;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.lbl.auth.external.ExternalAuthProperties;
import org.lbl.auth.external.ExternalLoginPersistence;
import org.lbl.auth.external.ExternalLoginService;
import org.lbl.auth.external.LoginTransaction;
import org.lbl.auth.identity.VerifiedIdentity;
import org.lbl.auth.service.LoginAttemptGuard;
import org.lbl.auth.session.LoginSession;
import org.lbl.auth.session.SessionService;
import org.lbl.common.exception.BusinessException;
import org.lbl.system.log.service.LoginLogService;
import org.lbl.system.log.support.LogResult;
import org.lbl.system.user.entity.UserEntity;
import org.lbl.system.user.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Map;

/** UIAS 的流程编排；SDK 验证与 ESF 查询通过两个端口在内网部署时提供。 */
@Service
public class UiasLoginService {
    private static final Logger log = LoggerFactory.getLogger(UiasLoginService.class);
    private static final Duration TRANSACTION_TTL = Duration.ofMinutes(10);
    private static final int START_QUOTA_LIMIT = 30;
    private final ExternalAuthProperties externalAuth;
    private final ObjectProvider<UiasAssertionConsumer> assertionConsumers;
    private final ObjectProvider<EnterpriseDirectoryPort> directories;
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final LoginAttemptGuard attempts;
    private final SessionService sessions;
    private final UserMapper users;
    private final ExternalLoginPersistence persistence;
    private final LoginLogService loginLogs;
    private final SecureRandom random = new SecureRandom();

    public UiasLoginService(ExternalAuthProperties externalAuth, ObjectProvider<UiasAssertionConsumer> assertionConsumers,
                            ObjectProvider<EnterpriseDirectoryPort> directories, StringRedisTemplate redis,
                            ObjectMapper json, LoginAttemptGuard attempts, SessionService sessions,
                            UserMapper users, ExternalLoginPersistence persistence, LoginLogService loginLogs) {
        this.externalAuth = externalAuth;
        this.assertionConsumers = assertionConsumers;
        this.directories = directories;
        this.redis = redis;
        this.json = json;
        this.attempts = attempts;
        this.sessions = sessions;
        this.users = users;
        this.persistence = persistence;
        this.loginLogs = loginLogs;
    }

    /** UIAS 入口只有在开关、SDK 适配器和 ESF 目录适配器都具备时才对前端启用。 */
    public boolean isUsable() {
        ExternalAuthProperties.Provider provider = provider();
        return provider != null && provider.isEnabled() && hasText(provider.getEntryUrl()) && hasText(provider.getCallbackUrl())
                && assertionConsumers.getIfAvailable() != null && directories.getIfAvailable() != null;
    }

    public String beginLogin(String returnTo, boolean rememberMe) {
        attempts.requireSourceQuota("uias-start", START_QUOTA_LIMIT, Duration.ofMinutes(1));
        return begin(safeReturnTo(returnTo), rememberMe, null, null);
    }

    public String beginBinding(Long userId, String initiatingSid) {
        LoginSession session = initiatingSid == null ? null : sessions.find(initiatingSid);
        if (session == null || session.onboarding() || !userId.equals(session.userId())) {
            throw new BusinessException("发起绑定的登录会话无效，请重新登录后再试");
        }
        return begin("/account/security", false, userId, initiatingSid);
    }

    private String begin(String returnTo, boolean rememberMe, Long targetUserId, String initiatingSid) {
        requireUsable();
        String state = token();
        ExternalAuthProperties.Provider provider = provider();
        if (provider == null) throw new BusinessException("未配置 UIAS 提供方");
        LoginTransaction transaction = new LoginTransaction("uias", "", provider.getCallbackUrl(), returnTo,
                rememberMe, targetUserId, initiatingSid);
        try {
            redis.opsForValue().set(transactionKey(state), json.writeValueAsString(transaction), TRANSACTION_TTL);
        } catch (Exception ex) {
            throw new IllegalStateException("无法创建 UIAS 登录事务", ex);
        }
        String callback = appendQuery(provider.getCallbackUrl(), Map.of("state", state));
        return appendQuery(provider.getEntryUrl(), Map.of(provider.getTargetParameter(), callback));
    }

    public ExternalLoginService.Completion complete(HttpServletRequest request, String state, String currentSid) {
        LoginTransaction transaction = consume(state);
        try {
            UiasAssertionConsumer consumer = requireAssertionConsumer();
            EnterpriseDirectoryPort directory = requireDirectory();
            String employeeNo = consumer.consumeEmployeeNo(request);
            if (!hasText(employeeNo)) throw new BusinessException("UIAS 未返回有效工号");
            EnterpriseDirectoryPort.EmployeeProfile profile = directory.findByEmployeeNo(employeeNo.trim())
                    .orElseThrow(() -> new BusinessException("未查询到员工目录信息"));
            if (!profile.employed()) throw new BusinessException("该员工当前非在职状态，无法登录系统");
            if (!employeeNo.trim().equals(profile.employeeNo())) throw new BusinessException("员工目录返回的工号不一致");
            ExternalAuthProperties.Provider provider = provider();
            if (provider == null) throw new BusinessException("未配置 UIAS 提供方");
            VerifiedIdentity verified = new VerifiedIdentity("uias", provider.getIssuer(), employeeNo.trim(),
                    profile.realName(), profile.email(), employeeNo.trim(), Map.of());

            ExternalLoginService.Completion completion;
            if (transaction.targetUserId() != null) {
                UserEntity target = users.selectById(transaction.targetUserId());
                if (target == null || !Integer.valueOf(1).equals(target.getEmployeeNoVerified())
                        || target.getEmployeeNo() == null || !employeeNo.trim().equals(target.getEmployeeNo())) {
                    throw new BusinessException("当前系统账号未绑定相同工号，不能关联内部统一认证");
                }
                completion = persistence.settle("uias", transaction, verified, currentSid);
            } else {
                UserEntity user = users.selectOne(new LambdaQueryWrapper<UserEntity>()
                        .eq(UserEntity::getEmployeeNo, employeeNo.trim())
                        .eq(UserEntity::getEmployeeNoVerified, 1).eq(UserEntity::getStatus, 1));
                if (user == null) throw new BusinessException("员工账号尚未开通，请联系系统管理员");
                completion = persistence.settleEmployeeLogin(transaction, verified, user);
            }
            loginLogs.record(completion.audit().username(), completion.audit().userId(), LogResult.SUCCESS,
                    completion.audit().message());
            return completion;
        } catch (BusinessException ex) {
            loginLogs.record("uias", null, LogResult.FAILURE, "内部统一认证失败：" + ex.getMessage());
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("UIAS login failed with an internal error", ex);
            loginLogs.record("uias", null, LogResult.FAILURE, "内部统一认证失败（内部错误）");
            throw ex;
        }
    }

    private LoginTransaction consume(String state) {
        if (!hasText(state)) throw new BusinessException("UIAS 登录状态无效或已过期");
        String value = redis.opsForValue().getAndDelete(transactionKey(state));
        if (value == null) throw new BusinessException("UIAS 登录状态无效或已过期");
        try {
            return json.readValue(value, LoginTransaction.class);
        } catch (Exception ex) {
            throw new BusinessException("UIAS 登录状态已损坏，请重新发起登录");
        }
    }

    private void requireUsable() {
        if (!isUsable()) throw new BusinessException("UIAS 尚未完成 SDK 或员工目录适配配置");
    }
    private ExternalAuthProperties.Provider provider() { return externalAuth.getProviders().get("uias"); }
    private UiasAssertionConsumer requireAssertionConsumer() {
        UiasAssertionConsumer consumer = assertionConsumers.getIfAvailable();
        if (consumer == null) throw new BusinessException("UIAS SDK 适配器尚未接入");
        return consumer;
    }
    private EnterpriseDirectoryPort requireDirectory() {
        EnterpriseDirectoryPort directory = directories.getIfAvailable();
        if (directory == null) throw new BusinessException("员工目录适配器尚未接入");
        return directory;
    }
    private String safeReturnTo(String value) { return value != null && value.startsWith("/") && !value.startsWith("//") ? value : "/"; }
    private String transactionKey(String state) { return "auth:uias:transaction:" + state; }
    private String token() { byte[] bytes = new byte[32]; random.nextBytes(bytes); return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private boolean hasText(String value) { return value != null && !value.isBlank(); }
    private String appendQuery(String url, Map<String, String> values) {
        String query = values.entrySet().stream().map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right).orElse("");
        return url + (url.contains("?") ? "&" : "?") + query;
    }
    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
