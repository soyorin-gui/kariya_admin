package org.lbl.auth.controller;

import org.lbl.auth.session.SessionService;
import org.lbl.auth.session.SessionView;
import org.lbl.common.exception.BusinessException;
import org.lbl.common.exception.UnauthorizedException;
import org.lbl.common.result.Result;
import org.lbl.security.context.CurrentUser;
import org.lbl.system.log.aspect.OperationLog;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/account/sessions")
@PreAuthorize("isAuthenticated() and !hasAuthority('onboarding:access')")
public class AccountSessionController {
    private final SessionService sessions;

    public AccountSessionController(SessionService sessions) {
        this.sessions = sessions;
    }

    @GetMapping
    public Result<List<SessionView>> list(@AuthenticationPrincipal CurrentUser user,
                                          Authentication authentication) {
        return Result.ok(sessions.listMemberSessions(user.id(), currentSid(authentication)));
    }

    @DeleteMapping("/{reference}")
    @OperationLog(module = "登录与安全", action = "退出指定设备")
    public Result<Void> remove(@PathVariable String reference, @AuthenticationPrincipal CurrentUser user,
                               Authentication authentication) {
        String currentReference = sessions.listMemberSessions(user.id(), currentSid(authentication)).stream()
                .filter(SessionView::current).map(SessionView::id).findFirst().orElse(null);
        if (reference.equals(currentReference)) throw new BusinessException("当前会话请使用退出登录");
        if (!sessions.removeByReference(user.id(), reference)) throw new BusinessException("登录会话不存在或已失效");
        return Result.ok(null, "该设备已退出登录");
    }

    @DeleteMapping("/others")
    @OperationLog(module = "登录与安全", action = "退出其他设备")
    public Result<Void> removeOthers(@AuthenticationPrincipal CurrentUser user,
                                     Authentication authentication) {
        sessions.removeAllExcept(user.id(), currentSid(authentication));
        return Result.ok(null, "其他设备已退出登录");
    }

    /**
     * 业务接口不会携带 Path=/api/auth 的刷新 Cookie，当前 SID 必须来自已验证的 JWT。
     * JwtAuthenticationFilter 在验证签名、会话和用户后已将 SID 放入 details，
     * 这样既不需要放宽 Cookie Path，也能防止设备管理误删当前会话。
     */
    private String currentSid(Authentication authentication) {
        Object details = authentication == null ? null : authentication.getDetails();
        if (details instanceof String sid && !sid.isBlank()) return sid;
        throw new UnauthorizedException("登录状态已失效");
    }
}
