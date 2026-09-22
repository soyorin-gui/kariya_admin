package org.kariya.system.role;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.kariya.common.result.Result;
import org.kariya.system.log.OperationLog;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system/roles")
public class RoleController {
    private final RoleService service;

    public RoleController(RoleService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('system:role:list')")
    Result<List<RoleVO>> list(@RequestParam(required = false) String keyword) {
        return Result.ok(service.list(keyword));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:role:add')")
    @OperationLog(module = "角色管理", action = "新增角色")
    Result<RoleVO> create(@Valid @RequestBody RoleRequest request) {
        return Result.ok(service.create(request), "新增角色成功");
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role:update')")
    @OperationLog(module = "角色管理", action = "修改角色")
    Result<RoleVO> update(@PathVariable Long id, @Valid @RequestBody RoleRequest request) {
        return Result.ok(service.update(id, request), "修改角色成功");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role:delete')")
    @OperationLog(module = "角色管理", action = "删除角色")
    Result<Void> delete(@PathVariable Long id) {
        service.remove(id);
        return Result.ok(null, "删除角色成功");
    }

    @GetMapping("/{id}/menu-ids")
    @PreAuthorize("hasAuthority('system:role:grant')")
    Result<List<Long>> menuIds(@PathVariable Long id) {
        return Result.ok(service.menuIds(id));
    }

    @PutMapping("/{id}/menu-ids")
    @PreAuthorize("hasAuthority('system:role:grant')")
    @OperationLog(module = "角色管理", action = "角色授权")
    Result<Void> grant(@PathVariable Long id, @RequestBody List<@NotNull Long> menuIds) {
        service.grantMenus(id, menuIds);
        return Result.ok(null, "菜单权限保存成功");
    }
}
