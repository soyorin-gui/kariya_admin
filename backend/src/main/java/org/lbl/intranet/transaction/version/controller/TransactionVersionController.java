package org.lbl.intranet.transaction.version.controller;

import org.lbl.common.result.Result;
import org.lbl.intranet.transaction.version.service.TransactionVersionService;
import org.lbl.intranet.transaction.version.vo.TransactionVersionVO;
import org.lbl.intranet.transaction.version.vo.TransactionVersionDetailVO;
import org.lbl.system.log.aspect.OperationLog;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/intranet/transaction-versions")
public class TransactionVersionController {
    private final TransactionVersionService service;
    public TransactionVersionController(TransactionVersionService service) { this.service = service; }

    @GetMapping("/transactions/{transactionId}")
    @PreAuthorize("hasAuthority('intranet:transaction:list')")
    public Result<List<TransactionVersionVO>> list(@PathVariable Long transactionId) { return Result.ok(service.list(transactionId)); }

    @GetMapping("/{versionId}")
    @PreAuthorize("hasAuthority('intranet:transaction:list')")
    public Result<TransactionVersionDetailVO> detail(@PathVariable Long versionId) { return Result.ok(service.detail(versionId)); }

    @PostMapping("/requirements/{requirementId}/activate")
    @PreAuthorize("hasAuthority('intranet:requirement:assign')")
    @OperationLog(module="内网需求", action="交易版本生效", targetType="INTRANET_REQUIREMENT", targetId="#requirementId")
    public Result<TransactionVersionVO> activate(@PathVariable Long requirementId) { return Result.ok(service.activate(requirementId), "交易版本已生效"); }
}
