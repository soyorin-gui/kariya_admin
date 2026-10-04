package org.lbl.approval.task;

import org.lbl.common.result.PageResult;
import org.lbl.common.result.Result;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/account/approvals")
@PreAuthorize("isAuthenticated() and !hasAuthority('onboarding:access')")
public class ApprovalTaskController {
    private final ApprovalTaskService service;

    public ApprovalTaskController(ApprovalTaskService service) { this.service = service; }

    @GetMapping
    public Result<PageResult<ApprovalTaskView>> page(@RequestParam(defaultValue = "pending") String scope,
                                                     @RequestParam(defaultValue = "1") long pageNum,
                                                     @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(service.page(scope, pageNum, pageSize));
    }

    @GetMapping("/pending-count")
    public Result<Map<String, Long>> pendingCount() {
        return Result.ok(Map.of("count", service.pendingCount()));
    }
}
