package org.lbl.intranet.operation.testcase.controller;

import jakarta.validation.Valid;
import org.lbl.common.result.Result;
import org.lbl.intranet.operation.testcase.request.OperationTestCaseCopyRequest;
import org.lbl.intranet.operation.testcase.request.OperationTestCaseRequest;
import org.lbl.intranet.operation.testcase.service.OperationTestCaseService;
import org.lbl.intranet.operation.testcase.vo.OperationTestCaseVO;
import org.lbl.system.log.aspect.OperationLog;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/intranet/operation/test-cases")
public class OperationTestCaseController {
    private static final String READ = "hasAnyAuthority('intranet:operation:invoke:list','intranet:operation:compare:list')";
    private static final String MANAGE = "hasAuthority('intranet:operation:test-case:manage')";
    private final OperationTestCaseService service;

    public OperationTestCaseController(OperationTestCaseService service) { this.service = service; }

    @GetMapping
    @PreAuthorize(READ)
    Result<List<OperationTestCaseVO>> list(@RequestParam Long transactionId, @RequestParam String bindingType,
                                           @RequestParam(required = false) Long transactionVersionId) {
        return Result.ok(service.list(transactionId, bindingType, transactionVersionId));
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ)
    Result<OperationTestCaseVO> detail(@PathVariable Long id) { return Result.ok(service.detail(id)); }

    @PostMapping
    @PreAuthorize(MANAGE)
    @OperationLog(module="交易运行中心", action="新增测试案例", targetType="INTRANET_OPERATION_TEST_CASE", targetName="#request.caseName()")
    Result<OperationTestCaseVO> create(@Valid @RequestBody OperationTestCaseRequest request) {
        return Result.ok(service.create(request), "新增测试案例成功");
    }

    @PutMapping("/{id}")
    @PreAuthorize(MANAGE)
    @OperationLog(module="交易运行中心", action="修改测试案例", targetType="INTRANET_OPERATION_TEST_CASE", targetId="#id", targetName="#request.caseName()")
    Result<OperationTestCaseVO> update(@PathVariable Long id, @Valid @RequestBody OperationTestCaseRequest request) {
        return Result.ok(service.update(id, request), "修改测试案例成功");
    }

    @PostMapping("/{id}/copies")
    @PreAuthorize(MANAGE)
    @OperationLog(module="交易运行中心", action="复制测试案例", targetType="INTRANET_OPERATION_TEST_CASE", targetId="#id")
    Result<OperationTestCaseVO> copy(@PathVariable Long id, @Valid @RequestBody OperationTestCaseCopyRequest request) {
        return Result.ok(service.copy(id, request), "复制测试案例成功");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(MANAGE)
    @OperationLog(module="交易运行中心", action="删除测试案例", targetType="INTRANET_OPERATION_TEST_CASE", targetId="#id")
    Result<Void> delete(@PathVariable Long id) {
        service.remove(id);
        return Result.ok(null, "删除测试案例成功");
    }
}
