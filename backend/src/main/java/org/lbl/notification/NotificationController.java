package org.lbl.notification;

import org.lbl.common.result.Result;
import org.lbl.security.context.CurrentUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/account/notifications")
@PreAuthorize("isAuthenticated() and !hasAuthority('onboarding:access')")
public class NotificationController {
    private final NotificationService service;
    public NotificationController(NotificationService service) { this.service = service; }

    @GetMapping public Result<List<NotificationEntity>> list(@AuthenticationPrincipal CurrentUser user,
                                                              @RequestParam(defaultValue = "50") int limit) {
        return Result.ok(service.latest(user.id(), limit));
    }
    @GetMapping("/unread-count") public Result<Map<String, Long>> unread(@AuthenticationPrincipal CurrentUser user) {
        return Result.ok(Map.of("count", service.unread(user.id())));
    }
    @PutMapping("/{id}/read") public Result<Void> read(@PathVariable Long id, @AuthenticationPrincipal CurrentUser user) {
        service.read(id, user.id()); return Result.ok(null);
    }
    @PutMapping("/read-all") public Result<Void> readAll(@AuthenticationPrincipal CurrentUser user) {
        service.readAll(user.id()); return Result.ok(null);
    }
}
