package org.lbl.intranet.dataasset.relation.controller;

import jakarta.validation.Valid;
import org.lbl.common.result.Result;
import org.lbl.intranet.dataasset.relation.request.TransactionDataResourceRequest;
import org.lbl.intranet.dataasset.relation.service.TransactionDataResourceService;
import org.lbl.intranet.dataasset.relation.vo.TransactionDataResourceVO;
import org.lbl.system.log.aspect.OperationLog;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/intranet/transactions/{transactionId}/data-resources")
public class TransactionDataResourceController {
    private final TransactionDataResourceService service;

    public TransactionDataResourceController(TransactionDataResourceService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAuthority('intranet:transaction:list')")
    Result<TransactionDataResourceVO> detail(@PathVariable Long transactionId) {
        return Result.ok(service.detail(transactionId));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('intranet:transaction:update')")
    @OperationLog(module = "交易资产管理", action = "配置数据资源", targetType = "INTRANET_TRANSACTION", targetId = "#transactionId")
    Result<TransactionDataResourceVO> replace(@PathVariable Long transactionId,
                                              @Valid @RequestBody TransactionDataResourceRequest request) {
        return Result.ok(service.replace(transactionId, request), "配置数据资源成功");
    }
}
