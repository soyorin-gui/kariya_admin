package org.lbl.intranet.operation.invoke.controller;

import jakarta.validation.Valid;
import org.lbl.common.result.Result;
import org.lbl.intranet.operation.invoke.model.MockTransactionInvocationResult;
import org.lbl.intranet.operation.invoke.request.MockTransactionInvocationRequest;
import org.lbl.intranet.operation.invoke.service.MockTransactionInvocationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/intranet/operation/invocations")
public class OperationInvocationController {
    private final MockTransactionInvocationService service;

    public OperationInvocationController(MockTransactionInvocationService service) {
        this.service = service;
    }

    @PostMapping("/mock")
    @PreAuthorize("hasAuthority('intranet:operation:invoke:list')")
    Result<MockTransactionInvocationResult> mock(@Valid @RequestBody MockTransactionInvocationRequest request) {
        return Result.ok(service.invoke(request));
    }
}
