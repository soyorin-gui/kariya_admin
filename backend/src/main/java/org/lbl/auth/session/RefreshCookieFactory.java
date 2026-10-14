package org.lbl.auth.session;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 所有认证入口统一使用的刷新 Cookie；有效期由各登录场景决定。
 * Path 限定在认证接口下，业务请求不携带此 Cookie。
 * SameSite=Lax；本地开发时前后端应使用相同站点（不要混用 localhost 和 127.0.0.1）。
 */
@Component
public class RefreshCookieFactory {
    public static final String NAME = "lbl_refresh";
    private final boolean secure;
    private final SessionLifetimePolicy lifetimes;

    public RefreshCookieFactory(org.lbl.config.SecurityProperties security, SessionLifetimePolicy lifetimes) {
        this.secure = security.secureCookie();
        this.lifetimes = lifetimes;
    }

    public ResponseCookie createFor(String sid, LoginSession session) {
        if (session == null) throw new IllegalArgumentException("登录会话不存在，无法签发刷新 Cookie");
        return create(sid, lifetimes.cookieMaxAge(session));
    }

    public ResponseCookie clear() {
        return create("", Duration.ZERO);
    }

    private ResponseCookie create(String sid, Duration maxAge) {
        return ResponseCookie.from(NAME, sid)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(maxAge)
                .build();
    }
}
