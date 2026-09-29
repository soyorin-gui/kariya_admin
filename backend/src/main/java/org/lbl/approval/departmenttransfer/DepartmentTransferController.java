package org.lbl.approval.departmenttransfer;

import jakarta.validation.Valid;
import org.lbl.common.result.Result;
import org.lbl.approval.departmenttransfer.DepartmentTransferModels.*;
import org.lbl.system.log.aspect.OperationLog;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.List;

@RestController
@RequestMapping("/api/account/department-change")
@PreAuthorize("isAuthenticated() and !hasAuthority('onboarding:access')")
public class DepartmentTransferController {
    private final DepartmentTransferApprovalService service;
    public DepartmentTransferController(DepartmentTransferApprovalService service) { this.service = service; }

    @GetMapping("/options") public Result<List<DeptOption>> options() { return Result.ok(service.options()); }
    @GetMapping("/profile") public Result<Profile> profile() { return Result.ok(service.profile()); }
    @GetMapping("/{id}") public Result<Detail> detail(@PathVariable Long id) { return Result.ok(service.get(id)); }
    @PostMapping @OperationLog(module = "个人资料", action = "提交部门变更申请")
    public Result<Detail> submit(@Valid @RequestBody Submit request) { return Result.ok(service.submit(request), "部门变更申请已提交"); }
    @PostMapping("/{id}/approve") @OperationLog(module = "部门审批", action = "通过部门变更申请")
    public Result<Detail> approve(@PathVariable Long id) { return Result.ok(service.approve(id), "当前审批步骤已通过"); }
    @PostMapping("/{id}/reject") @OperationLog(module = "部门审批", action = "拒绝部门变更申请")
    public Result<Detail> reject(@PathVariable Long id, @Valid @RequestBody Reject request) { return Result.ok(service.reject(id, request.reason()), "申请已拒绝"); }
    @PostMapping("/{id}/cancel") @OperationLog(module = "个人资料", action = "撤销部门变更申请")
    public Result<Detail> cancel(@PathVariable Long id) { return Result.ok(service.cancel(id), "申请已撤销"); }
}
