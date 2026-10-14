package org.lbl.auth.session;

import org.lbl.system.log.support.RequestInfo;

import java.io.Serializable;
import java.time.LocalDateTime;

public record LoginSession(String principalType, Long userId, String username, long authVersion, boolean rememberMe,
                           String authMethod, String providerKey, String onboardingId, String issuer, String subject,
                           String displayName, String email, String employeeNo, boolean passwordChangeRequired,
                           LocalDateTime createdTime, LocalDateTime lastActiveTime, String loginIp,
                           String userAgent) implements Serializable {
    public static LoginSession member(Long userId, String username, long authVersion, boolean rememberMe,
                                      String authMethod, String providerKey, boolean passwordChangeRequired) {
        LocalDateTime now = LocalDateTime.now();
        return new LoginSession("MEMBER", userId, username, authVersion, rememberMe, authMethod, providerKey,
                null, null, null, null, null, null, passwordChangeRequired, now, now,
                RequestInfo.clientIp(), RequestInfo.userAgent());
    }

    /**
     * 开户确认态会话。
     * <p>
     * {@code rememberMe} 是「发起第三方登录时用户是否勾选了记住我」的<b>快照</b>，
     * 刻意加在会话里而不是让回调另外找地方存：它的唯一用途是被
     * {@link org.lbl.auth.service.RegistrationService} 在创建/绑定账号时读走，
     * 用来决定<b>正式会员会话</b>是否长期有效。
     * <p>
     * 注意它<b>不改变本会话自身的有效期</b>（仍是 30 分钟空闲 / 2 小时绝对，见 SessionService）：
     * "三方认证后不注册、不绑定即失效"是刻意的产品决定，不能因为勾了记住我就把开户态拖长。
     * 此前这个值被写死成 false，于是勾了"记住我"的用户走完开户确认后拿到的是会话级 Cookie，
     * 关掉浏览器就要重新登录 —— 用户的选择在最后一跳被静默丢弃。
     */
    public static LoginSession onboarding(String onboardingId, String providerKey, String issuer, String subject,
                                     String displayName, String email, String employeeNo, boolean rememberMe) {
        LocalDateTime now = LocalDateTime.now();
        return new LoginSession("ONBOARDING", null, null, 0, rememberMe, "EXTERNAL", providerKey,
                onboardingId, issuer, subject, displayName, email, employeeNo, false, now, now,
                RequestInfo.clientIp(), RequestInfo.userAgent());
    }

    public boolean onboarding() {
        return "ONBOARDING".equals(principalType);
    }

    public LoginSession touch() {
        return new LoginSession(principalType, userId, username, authVersion, rememberMe, authMethod, providerKey,
                onboardingId, issuer, subject, displayName, email, employeeNo, passwordChangeRequired,
                createdTime, LocalDateTime.now(), loginIp, userAgent);
    }

    public LoginSession withPasswordChangeRequired(boolean required) {
        return new LoginSession(principalType, userId, username, authVersion, rememberMe, authMethod, providerKey,
                onboardingId, issuer, subject, displayName, email, employeeNo, required,
                createdTime, lastActiveTime, loginIp, userAgent);
    }
}
