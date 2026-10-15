package org.lbl.intranet.operation.flow.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.lbl.common.result.PageResult;
import org.lbl.common.result.Result;
import org.lbl.intranet.operation.flow.model.FlowEnvironment;
import org.lbl.intranet.operation.flow.model.TransactionFlowRecord;
import org.lbl.intranet.operation.flow.request.TransactionFlowQueryRequest;
import org.lbl.intranet.operation.flow.service.TransactionFlowQueryService;
import org.lbl.intranet.operation.flow.vo.FlowFilterOptionsVO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/intranet/operation/flows")
public class TransactionFlowController {
    private final TransactionFlowQueryService service;

    public TransactionFlowController(TransactionFlowQueryService service) {
        this.service = service;
    }

    @GetMapping("/environments")
    @PreAuthorize("hasAuthority('intranet:operation:flow:list')")
    Result<List<FlowEnvironment>> environments() {
        return Result.ok(service.environments());
    }

    @GetMapping("/options")
    @PreAuthorize("hasAuthority('intranet:operation:flow:list')")
    Result<FlowFilterOptionsVO> options(@RequestParam @NotBlank(message = "运行环境不能为空") String environmentId) {
        return Result.ok(service.options(environmentId));
    }

    @PostMapping("/query")
    @PreAuthorize("hasAuthority('intranet:operation:flow:list')")
    Result<PageResult<TransactionFlowRecord>> query(@Valid @RequestBody TransactionFlowQueryRequest request) {
        return Result.ok(service.query(request));
    }
}
