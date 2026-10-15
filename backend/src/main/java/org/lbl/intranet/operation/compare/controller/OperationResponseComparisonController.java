package org.lbl.intranet.operation.compare.controller;

import jakarta.validation.Valid;
import org.lbl.common.result.Result;
import org.lbl.intranet.operation.compare.model.ComparisonEnvironment;
import org.lbl.intranet.operation.compare.model.MockResponseComparison;
import org.lbl.intranet.operation.compare.request.MockResponseComparisonRequest;
import org.lbl.intranet.operation.compare.service.MockResponseComparisonService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/intranet/operation/comparisons")
public class OperationResponseComparisonController {
    private final MockResponseComparisonService service;

    public OperationResponseComparisonController(MockResponseComparisonService service) {
        this.service = service;
    }

    @GetMapping("/environments")
    @PreAuthorize("hasAuthority('intranet:operation:compare:list')")
    Result<List<ComparisonEnvironment>> environments() {
        return Result.ok(service.environments());
    }

    @PostMapping("/mock")
    @PreAuthorize("hasAuthority('intranet:operation:compare:list')")
    Result<MockResponseComparison> mock(@Valid @RequestBody MockResponseComparisonRequest request) {
        return Result.ok(service.compare(request));
    }
}
