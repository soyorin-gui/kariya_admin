package org.lbl.intranet.transaction.controller;

import jakarta.validation.Valid;
import org.lbl.common.result.*;
import org.lbl.intranet.transaction.request.TransactionRequest;
import org.lbl.intranet.transaction.service.TransactionAssetService;
import org.lbl.intranet.transaction.vo.*;
import org.lbl.system.log.aspect.OperationLog;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/intranet/transactions")
public class TransactionAssetController {
    private final TransactionAssetService service;
    public TransactionAssetController(TransactionAssetService service) { this.service = service; }
    @GetMapping @PreAuthorize("hasAuthority('intranet:transaction:list')") Result<PageResult<TransactionListVO>> page(@RequestParam(defaultValue="1") long pageNum, @RequestParam(defaultValue="10") long pageSize, @RequestParam(required=false) String keyword, @RequestParam(required=false) Integer status, @RequestParam(required=false) String sortField, @RequestParam(required=false) String sortOrder) { return Result.ok(service.page(pageNum, pageSize, keyword, status, sortField, sortOrder)); }
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('intranet:transaction:list')") Result<TransactionVO> detail(@PathVariable Long id) { return Result.ok(service.detail(id)); }
    @PostMapping @PreAuthorize("hasAuthority('intranet:transaction:add')") @OperationLog(module="交易资产管理", action="新增交易", targetType="INTRANET_TRANSACTION") Result<TransactionVO> create(@Valid @RequestBody TransactionRequest r) { return Result.ok(service.create(r), "新增交易成功"); }
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('intranet:transaction:update')") @OperationLog(module="交易资产管理", action="修改交易", targetType="INTRANET_TRANSACTION", targetId="#id") Result<TransactionVO> update(@PathVariable Long id, @Valid @RequestBody TransactionRequest r) { return Result.ok(service.update(id, r), "修改交易成功"); }
    @DeleteMapping("/{id}") @PreAuthorize("hasAuthority('intranet:transaction:delete')") @OperationLog(module="交易资产管理", action="删除交易", targetType="INTRANET_TRANSACTION", targetId="#id") Result<Void> delete(@PathVariable Long id) { service.remove(id); return Result.ok(null, "删除交易成功"); }
}
