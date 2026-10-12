package org.lbl.intranet.requirement.controller;

import jakarta.validation.Valid;
import org.lbl.common.result.*;
import org.lbl.intranet.requirement.request.RequirementRequest;
import org.lbl.intranet.requirement.request.RequirementDecisionRequest;
import org.lbl.intranet.requirement.request.AssignDeveloperRequest;
import org.lbl.intranet.requirement.request.AssignTransactionRequest;
import org.lbl.intranet.requirement.request.ConfigureImplementationRequest;
import org.lbl.intranet.requirement.service.IntranetRequirementService;
import org.lbl.intranet.requirement.vo.RequirementListVO;
import org.lbl.intranet.requirement.vo.RequirementApprovalDetailVO;
import org.lbl.intranet.requirement.vo.RequirementImplementationOptionsVO;
import org.lbl.intranet.requirement.entity.NewTransactionRequirementDetailEntity;
import org.lbl.intranet.requirement.entity.TransactionCallRequirementDetailEntity;
import org.lbl.intranet.requirement.entity.TransactionChangeRequirementDetailEntity;
import org.lbl.system.log.aspect.OperationLog;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/intranet/requirements")
public class IntranetRequirementController {
    private final IntranetRequirementService service;
    public IntranetRequirementController(IntranetRequirementService service) { this.service = service; }
    @GetMapping @PreAuthorize("hasAuthority('intranet:requirement:list')") Result<PageResult<RequirementListVO>> page(@RequestParam(defaultValue="1") long pageNum, @RequestParam(defaultValue="10") long pageSize, @RequestParam(required=false) String keyword, @RequestParam(required=false) Integer status, @RequestParam(required=false) Integer type, @RequestParam(required=false) String sortField, @RequestParam(required=false) String sortOrder) { return Result.ok(service.page(pageNum, pageSize, keyword, status, type, sortField, sortOrder)); }
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('intranet:requirement:list')") Result<RequirementListVO> get(@PathVariable Long id) { return Result.ok(service.get(id)); }
    @GetMapping("/{id}/approval-detail") @PreAuthorize("isAuthenticated() and !hasAuthority('onboarding:access')") Result<RequirementApprovalDetailVO> approvalDetail(@PathVariable Long id) { return Result.ok(service.getApprovalDetail(id)); }
    @GetMapping("/{id}/new-transaction-detail") @PreAuthorize("hasAuthority('intranet:requirement:list')") Result<NewTransactionRequirementDetailEntity> newTransactionDetail(@PathVariable Long id) { return Result.ok(service.getNewTransactionDetail(id)); }
    @PutMapping("/{id}/new-transaction-detail") @PreAuthorize("hasAuthority('intranet:requirement:update')") Result<NewTransactionRequirementDetailEntity> saveNewTransactionDetail(@PathVariable Long id, @RequestBody NewTransactionRequirementDetailEntity detail) { return Result.ok(service.saveNewTransactionDetail(id, detail)); }
    @GetMapping("/{id}/transaction-call-detail") @PreAuthorize("hasAuthority('intranet:requirement:list')") Result<TransactionCallRequirementDetailEntity> transactionCallDetail(@PathVariable Long id) { return Result.ok(service.getTransactionCallDetail(id)); }
    @PutMapping("/{id}/transaction-call-detail") @PreAuthorize("hasAuthority('intranet:requirement:update')") Result<TransactionCallRequirementDetailEntity> saveTransactionCallDetail(@PathVariable Long id, @RequestBody TransactionCallRequirementDetailEntity detail) { return Result.ok(service.saveTransactionCallDetail(id, detail)); }
    @GetMapping("/{id}/transaction-change-detail") @PreAuthorize("hasAuthority('intranet:requirement:list')") Result<TransactionChangeRequirementDetailEntity> transactionChangeDetail(@PathVariable Long id) { return Result.ok(service.getTransactionChangeDetail(id)); }
    @PutMapping("/{id}/transaction-change-detail") @PreAuthorize("hasAuthority('intranet:requirement:update')") Result<TransactionChangeRequirementDetailEntity> saveTransactionChangeDetail(@PathVariable Long id, @RequestBody TransactionChangeRequirementDetailEntity detail) { return Result.ok(service.saveTransactionChangeDetail(id, detail)); }
    @PostMapping @PreAuthorize("hasAuthority('intranet:requirement:add')") @OperationLog(module="内网需求", action="新建需求", targetType="INTRANET_REQUIREMENT") Result<RequirementListVO> create(@Valid @RequestBody RequirementRequest request) { return Result.ok(service.create(request)); }
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('intranet:requirement:update')") @OperationLog(module="内网需求", action="编辑需求", targetType="INTRANET_REQUIREMENT", targetId="#id") Result<RequirementListVO> update(@PathVariable Long id, @Valid @RequestBody RequirementRequest request) { return Result.ok(service.update(id, request)); }
    @PostMapping("/{id}/submit") @PreAuthorize("hasAuthority('intranet:requirement:submit')") Result<RequirementListVO> submit(@PathVariable Long id) { return Result.ok(service.submit(id)); }
    @PostMapping("/{id}/cancel") @PreAuthorize("hasAuthority('intranet:requirement:submit')") Result<RequirementListVO> cancel(@PathVariable Long id) { return Result.ok(service.cancel(id)); }
    @PostMapping("/{id}/approve") @PreAuthorize("isAuthenticated() and !hasAuthority('onboarding:access')") Result<RequirementListVO> approve(@PathVariable Long id) { return Result.ok(service.approve(id)); }
    @PostMapping("/{id}/reject") @PreAuthorize("isAuthenticated() and !hasAuthority('onboarding:access')") Result<RequirementListVO> reject(@PathVariable Long id, @Valid @RequestBody RequirementDecisionRequest request) { return Result.ok(service.reject(id, request.reason())); }
    @PutMapping("/{id}/developer") @PreAuthorize("hasAuthority('intranet:requirement:assign')") Result<RequirementListVO> assign(@PathVariable Long id, @Valid @RequestBody AssignDeveloperRequest request) { return Result.ok(service.assignDeveloper(id, request)); }
    @GetMapping("/{id}/implementation-options") @PreAuthorize("hasAuthority('intranet:requirement:assign')") Result<RequirementImplementationOptionsVO> implementationOptions(@PathVariable Long id) { return Result.ok(service.implementationOptions(id)); }
    @PutMapping("/{id}/implementation") @PreAuthorize("hasAuthority('intranet:requirement:assign')") Result<RequirementListVO> configureImplementation(@PathVariable Long id, @Valid @RequestBody ConfigureImplementationRequest request) { return Result.ok(service.configureImplementation(id, request)); }
    @PutMapping("/{id}/transaction") @PreAuthorize("hasAuthority('intranet:requirement:assign')") Result<RequirementListVO> assignTransaction(@PathVariable Long id, @Valid @RequestBody AssignTransactionRequest request) { return Result.ok(service.assignTransaction(id, request)); }
}
