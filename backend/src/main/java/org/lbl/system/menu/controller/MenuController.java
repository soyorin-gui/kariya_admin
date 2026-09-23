package org.lbl.system.menu.controller;

import jakarta.validation.Valid;
import org.lbl.common.result.Result;
import org.lbl.system.log.aspect.OperationLog;
import org.lbl.system.menu.entity.MenuEntity;
import org.lbl.system.menu.request.MenuRequest;
import org.lbl.system.menu.service.MenuService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system/menus")
public class MenuController {
    private final MenuService service;

    public MenuController(MenuService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('system:menu:list')")
    Result<List<MenuEntity>> list() {
        return Result.ok(service.list());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:menu:add')")
    @OperationLog(module = "菜单管理", action = "新增菜单")
    Result<MenuEntity> create(@Valid @RequestBody MenuRequest request) {
        return Result.ok(service.create(request), "新增菜单成功");
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:menu:update')")
    @OperationLog(module = "菜单管理", action = "修改菜单")
    Result<MenuEntity> update(@PathVariable Long id, @Valid @RequestBody MenuRequest request) {
        return Result.ok(service.update(id, request), "修改菜单成功");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:menu:delete')")
    @OperationLog(module = "菜单管理", action = "删除菜单")
    Result<Void> delete(@PathVariable Long id) {
        service.remove(id);
        return Result.ok(null, "删除菜单成功");
    }
}
