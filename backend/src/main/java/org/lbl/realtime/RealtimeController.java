package org.lbl.realtime;

import org.lbl.common.result.Result;
import org.lbl.security.context.CurrentUser;
import org.lbl.auth.session.LoginSession;
import org.lbl.auth.session.SessionService;
import org.lbl.common.exception.UnauthorizedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Map;

@RestController
@RequestMapping("/api/account/realtime")
@PreAuthorize("isAuthenticated() and !hasAuthority('onboarding:access')")
public class RealtimeController {
    private final RealtimeTicketService tickets;
    private final SessionService sessions;
    public RealtimeController(RealtimeTicketService tickets, SessionService sessions) { this.tickets = tickets; this.sessions = sessions; }

    @PostMapping("/ticket")
    public Result<Map<String, String>> ticket(@AuthenticationPrincipal CurrentUser user, Authentication authentication) {
        String sid = authentication.getDetails() instanceof String value ? value : null;
        LoginSession session = sid == null ? null : sessions.find(sid);
        if (session == null || session.onboarding() || !user.id().equals(session.userId())) throw new UnauthorizedException("登录状态已失效");
        return Result.ok(Map.of("ticket", tickets.issue(user.id(), sid)));
    }
}
