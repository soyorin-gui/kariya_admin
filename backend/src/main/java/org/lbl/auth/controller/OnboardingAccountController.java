package org.lbl.auth.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.lbl.auth.model.OnboardingAccountRequest;
import org.lbl.auth.model.OnboardingBindRequest;
import org.lbl.auth.model.SessionGrant;
import org.lbl.auth.service.RegistrationService;
import org.lbl.auth.session.LoginSession;
import org.lbl.auth.session.SessionService;
import org.lbl.common.exception.UnauthorizedException;
import org.lbl.common.result.Result;
import org.lbl.config.SecurityProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/auth/onboarding/account")
public class OnboardingAccountController {
    /**
     * 记住我会话的刷新 Cookie 有效期。必须与 {@code AuthController#login/register} 和
     * {@code ExternalAuthController#callback} 里的 {@code Duration.ofDays(14)} 一致 ——
     * 三处表达的是同一件事："记住我 = 14 天"。
     */
    private static final Duration REMEMBERED_COOKIE_AGE = Duration.ofDays(14);
    private final RegistrationService registrations;
    private final SessionService sessions;
    private final boolean secureCookie;

    public OnboardingAccountController(RegistrationService registrations, SessionService sessions,
                                       SecurityProperties security) {
        this.registrations = registrations;
        this.sessions = sessions;
        this.secureCookie = security.secureCookie();
    }

    @PostMapping("/create")
    @PreAuthorize("hasAuthority('onboarding:account:create')")
    public Result<Map<String, String>> create(@CookieValue(value = "lbl_refresh", required = false) String sid,
                                              @Valid @RequestBody OnboardingAccountRequest request,
                                              HttpServletResponse response) {
        SessionGrant grant = registrations.createFromOnboarding(requireOnboarding(sid), request);
        sessions.remove(sid);
        setCookie(response, grant.sid());
        return Result.ok(Map.of("accessToken", grant.accessToken()), "账号创建成功");
    }

    @PostMapping("/bind")
    @PreAuthorize("hasAuthority('onboarding:account:bind')")
    public Result<Map<String, String>> bind(@CookieValue(value = "lbl_refresh", required = false) String sid,
                                            @Valid @RequestBody OnboardingBindRequest request,
                                            HttpServletResponse response) {
        SessionGrant grant = registrations.bindFromOnboarding(requireOnboarding(sid), request);
        sessions.remove(sid);
        setCookie(response, grant.sid());
        return Result.ok(Map.of("accessToken", grant.accessToken()), "账号绑定成功");
    }

    private LoginSession requireOnboarding(String sid) {
        LoginSession session = sid == null ? null : sessions.find(sid);
        if (session == null || !session.onboarding()) throw new UnauthorizedException("开户确认状态已失效");
        return session;
    }

    /**
     * 与新会话同时下发刷新 Cookie。
     * <p>
     * maxAge 必须由"这个新会话是不是记住我会话"决定，不能一律写成会话级 Cookie：
     * 开户确认（创建/绑定）是第三方登录的一部分，用户在登录入口勾没勾"记住我"
     * 已经记在开户态会话里并被 RegistrationService 带进了新会话，Cookie 只是把它如实表达出来。
     * 此前这里固定 {@code maxAge(-1)}，于是即便服务端会话本身是记住我会话，
     * 浏览器手里的凭据也是会话级的 —— 关掉浏览器就再也换不出令牌。
     * <p>
     * 其余属性（name / HttpOnly / Secure / SameSite / Path）必须与
     * {@code AuthController#refreshCookie} 完全一致，否则浏览器会当成两个不同的 Cookie，
     * 出现"登录成功但凭据没更新"这类问题；14 天也与之对齐（记住我 = 7 天空闲、14 天绝对有效）。
     */
    private void setCookie(HttpServletResponse response, String sid) {
        LoginSession session = sessions.find(sid);
        Duration maxAge = session != null && session.rememberMe() ? REMEMBERED_COOKIE_AGE : Duration.ofSeconds(-1);
        response.addHeader("Set-Cookie", ResponseCookie.from("lbl_refresh", sid).httpOnly(true).secure(secureCookie)
                .sameSite("Lax").path("/api/auth").maxAge(maxAge).build().toString());
    }
}
