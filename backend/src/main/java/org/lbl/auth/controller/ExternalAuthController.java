package org.lbl.auth.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.lbl.auth.external.ExternalLoginService;
import org.lbl.auth.model.SessionGrant;
import org.lbl.auth.session.LoginSession;
import org.lbl.auth.session.SessionService;
import org.lbl.common.exception.BusinessException;
import org.lbl.common.exception.TooManyRequestsException;
import org.lbl.common.result.Result;
import org.lbl.config.SecurityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/auth/external")
public class ExternalAuthController {
    private static final Logger log = LoggerFactory.getLogger(ExternalAuthController.class);
    /** 与 AuthController / OnboardingAccountController 签发的是同一个 Cookie，拼错一个字就会变成另一个 Cookie。 */
    private static final String REFRESH_COOKIE = "lbl_refresh";
    private final ExternalLoginService service;
    private final boolean secureCookie;
    private final SessionService sessions;

    public ExternalAuthController(ExternalLoginService service, SecurityProperties security, SessionService sessions) {
        this.service = service;
        this.secureCookie = security.secureCookie();
        this.sessions = sessions;
    }

    @GetMapping("/providers")
    public Result<List<ExternalLoginService.ProviderView>> providers() {
        return Result.ok(service.providers());
    }

    @GetMapping("/{provider}/start")
    public void start(@PathVariable String provider,
                      @RequestParam(required = false) String returnTo,
                      @RequestParam(defaultValue = "false") boolean rememberMe,
                      HttpServletResponse response) throws IOException {
        response.sendRedirect(service.beginLogin(provider, returnTo, rememberMe));
    }

    /**
     * 第三方平台跳回来的落点。
     * <p>
     * 这里会读刷新 Cookie，原因只有一个：<b>绑定</b>流程必须认出"发起绑定的那个浏览器当前持有的会话"，
     * 才能在该会话上继续，而不是另开一个新会话。<b>登录</b>流程完全不需要它（会新建会话）。
     * <p>
     * 能读到是有前提的，两个条件当前都成立：Cookie 的 path 是 {@code /api/auth}，
     * 正好覆盖 {@code /api/auth/external/**}；SameSite=Lax 允许"从外部站点跳回来的顶层 GET 导航"携带它。
     * 因此这个参数是"尽力而为"的 —— 读不到时 {@code complete} 会退回新建会话，
     * 也就是修这个 bug 之前的行为，不会让绑定流程整体失败。
     */
    @GetMapping("/{provider}/callback")
    public void callback(@PathVariable String provider,
                         @RequestParam(required = false) String code,
                         @RequestParam(required = false) String state,
                         @RequestParam(required = false) String error,
                         @RequestParam(name = "error_description", required = false) String errorDescription,
                         @CookieValue(value = REFRESH_COOKIE, required = false) String currentSid,
                         HttpServletResponse response) throws IOException {
        if (error != null || code == null || state == null) {
            String message = errorDescription == null || errorDescription.isBlank() ? "外部登录已取消或认证失败" : errorDescription;
            response.sendRedirect(service.frontendCallback("/login", message));
            return;
        }
        try {
            ExternalLoginService.Completion completion = service.complete(provider, code, state, currentSid);
            SessionGrant grant = completion.grant();
            LoginSession session = sessions.find(grant.sid());
            Duration age = grant.onboarding() ? Duration.ofHours(2)
                    : session != null && session.rememberMe() ? Duration.ofDays(14) : Duration.ofSeconds(-1);
            // 绑定流程复用原会话时，这里的 sid 与浏览器手里那个相同：
            // 重新下发同一个 Cookie 是幂等的，且 age 由原会话的 rememberMe 推出，"记住我"不会再被降级。
            response.addHeader("Set-Cookie", cookie(grant.sid(), age).toString());
            response.sendRedirect(service.frontendCallback(completion.returnTo(), null));
        } catch (BusinessException | TooManyRequestsException ex) {
            // 这两类异常的消息是本项目自己写的、面向用户的（"该外部身份已经绑定其他账号"、
            // "待绑定账号不存在或已停用"），回显出去是有效信息，不是泄露。
            response.sendRedirect(service.frontendCallback("/login", ex.getMessage() == null ? "外部登录失败" : ex.getMessage()));
        } catch (Exception ex) {
            // 其余异常（唯一键冲突、SQL 异常、NPE …）的 getMessage() 里可能带表名、列名、
            // SQL 片段甚至参数值，而这段文本会被拼进 302 的 Location：
            // 它会出现在浏览器地址栏、Referer 头、以及网关的 access log 里 —— 等于把内部结构
            // 写进了三处我们控制不了的日志。因此对外只给一句通用文案，细节全部留在服务端日志。
            log.warn("External auth callback failed: provider={}", provider, ex);
            response.sendRedirect(service.frontendCallback("/login", "外部登录失败，请稍后重试或改用账号密码登录"));
        }
    }

    private ResponseCookie cookie(String sid, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, sid).httpOnly(true).secure(secureCookie).sameSite("Lax")
                .path("/api/auth").maxAge(maxAge).build();
    }
}
