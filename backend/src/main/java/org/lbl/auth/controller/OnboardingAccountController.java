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
import org.lbl.auth.session.RefreshCookieFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth/onboarding/account")
public class OnboardingAccountController {
    private final RegistrationService registrations;
    private final SessionService sessions;
    private final RefreshCookieFactory refreshCookies;

    public OnboardingAccountController(RegistrationService registrations, SessionService sessions,
                                       RefreshCookieFactory refreshCookies) {
        this.registrations = registrations;
        this.sessions = sessions;
        this.refreshCookies = refreshCookies;
    }

    @PostMapping("/create")
    @PreAuthorize("hasAuthority('onboarding:account:create')")
    public Result<Map<String, String>> create(@CookieValue(value = RefreshCookieFactory.NAME, required = false) String sid,
                                              @Valid @RequestBody OnboardingAccountRequest request,
                                              HttpServletResponse response) {
        SessionGrant grant = registrations.createFromOnboarding(requireOnboarding(sid), request);
        sessions.remove(sid);
        setCookie(response, grant.sid());
        return Result.ok(Map.of("accessToken", grant.accessToken()), "账号创建成功");
    }

    @PostMapping("/bind")
    @PreAuthorize("hasAuthority('onboarding:account:bind')")
    public Result<Map<String, String>> bind(@CookieValue(value = RefreshCookieFactory.NAME, required = false) String sid,
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
     * {@code RefreshCookieFactory#createFor} 完全一致，否则浏览器会当成两个不同的 Cookie。
     * Cookie 生命周期统一从安全配置读取，不再在各认证入口分别硬编码。
     */
    private void setCookie(HttpServletResponse response, String sid) {
        response.addHeader("Set-Cookie", refreshCookies.createFor(sid, sessions.find(sid)).toString());
    }
}
