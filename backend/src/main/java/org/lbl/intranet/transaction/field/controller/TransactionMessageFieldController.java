package org.lbl.intranet.transaction.field.controller;

import jakarta.validation.Valid;
import org.lbl.common.result.Result;
import org.lbl.intranet.transaction.field.request.TransactionMessageFieldRequest;
import org.lbl.intranet.transaction.field.service.TransactionMessageFieldService;
import org.lbl.intranet.transaction.field.vo.TransactionMessageFieldVO;
import org.lbl.system.log.aspect.OperationLog;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/intranet/transactions/{transactionId}/message-fields")
public class TransactionMessageFieldController {
    private final TransactionMessageFieldService service;
    public TransactionMessageFieldController(TransactionMessageFieldService service) { this.service = service; }
    @GetMapping @PreAuthorize("hasAuthority('intranet:transaction:list')") Result<List<TransactionMessageFieldVO>> list(@PathVariable Long transactionId, @RequestParam String side) { return Result.ok(service.list(transactionId, side)); }
    @PostMapping @PreAuthorize("hasAuthority('intranet:transaction:update')") @OperationLog(module="交易资产管理", action="新增报文字段", targetType="INTRANET_TRANSACTION") Result<TransactionMessageFieldVO> create(@PathVariable Long transactionId, @RequestParam String side, @Valid @RequestBody TransactionMessageFieldRequest r) { return Result.ok(service.create(transactionId, side, r)); }
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('intranet:transaction:update')") @OperationLog(module="交易资产管理", action="修改报文字段", targetType="INTRANET_TRANSACTION") Result<TransactionMessageFieldVO> update(@PathVariable Long transactionId, @RequestParam String side, @PathVariable Long id, @Valid @RequestBody TransactionMessageFieldRequest r) { return Result.ok(service.update(transactionId, side, id, r)); }
    @DeleteMapping("/{id}") @PreAuthorize("hasAuthority('intranet:transaction:update')") @OperationLog(module="交易资产管理", action="删除报文字段", targetType="INTRANET_TRANSACTION") Result<Void> delete(@PathVariable Long transactionId, @RequestParam String side, @PathVariable Long id) { service.remove(transactionId, side, id); return Result.ok(null, "删除字段成功"); }
}
