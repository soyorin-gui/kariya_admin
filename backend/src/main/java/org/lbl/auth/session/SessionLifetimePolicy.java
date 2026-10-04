package org.lbl.auth.session;

import org.lbl.config.SecurityProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** 登录会话在 Redis 与浏览器中的唯一生命周期口径。 */
@Component
public class SessionLifetimePolicy {
    private static final Duration ONBOARDING_IDLE = Duration.ofMinutes(30);
    private static final Duration ONBOARDING_ABSOLUTE = Duration.ofHours(2);

    private final SecurityProperties security;

    public SessionLifetimePolicy(SecurityProperties security) {
        this.security = security;
    }

    public Duration idle(boolean rememberMe) {
        return Duration.ofHours(rememberMe ? security.rememberedIdleDays() * 24 : security.idleHours());
    }

    public Duration idle(LoginSession session) {
        return session.onboarding() ? ONBOARDING_IDLE : idle(session.rememberMe());
    }

    public Duration absolute(LoginSession session) {
        return session.onboarding() ? ONBOARDING_ABSOLUTE : Duration.ofDays(security.rememberedAbsoluteDays());
    }

    /** 非“记住我”会员会话使用浏览器会话 Cookie；其余与服务端绝对期限严格一致。 */
    public Duration cookieMaxAge(LoginSession session) {
        return !session.onboarding() && !session.rememberMe() ? Duration.ofSeconds(-1) : absolute(session);
    }
}
