package org.lbl.system.dept.controller;

import jakarta.validation.Valid;
import org.lbl.common.result.Result;
import org.lbl.system.dept.vo.DeptFormOptions;
import org.lbl.system.dept.request.DeptRequest;
import org.lbl.system.dept.service.DeptService;
import org.lbl.system.dept.vo.DeptVO;
import org.lbl.system.log.aspect.OperationLog;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system/depts")
public class DeptController {
    private final DeptService service;

    public DeptController(DeptService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('system:dept:list')")
    Result<List<DeptVO>> list() {
        return Result.ok(service.list());
    }

    @GetMapping("/form-options")
    @PreAuthorize("hasAnyAuthority('system:dept:add', 'system:dept:update')")
    Result<DeptFormOptions> formOptions() {
        return Result.ok(service.formOptions());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:dept:add')")
    @OperationLog(module = "部门管理", action = "新增部门")
    Result<DeptVO> create(@Valid @RequestBody DeptRequest request) {
        return Result.ok(service.create(request), "新增部门成功");
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:dept:update')")
    @OperationLog(module = "部门管理", action = "修改部门")
    Result<DeptVO> update(@PathVariable Long id, @Valid @RequestBody DeptRequest request) {
        return Result.ok(service.update(id, request), "修改部门成功");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:dept:delete')")
    @OperationLog(module = "部门管理", action = "删除部门")
    Result<Void> delete(@PathVariable Long id) {
        service.remove(id);
        return Result.ok(null, "删除部门成功");
    }
}
