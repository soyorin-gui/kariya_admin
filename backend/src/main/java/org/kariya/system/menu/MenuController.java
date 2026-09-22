package org.kariya.system.menu;

import jakarta.validation.Valid;
import org.kariya.common.result.Result;
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
    Result<MenuEntity> create(@Valid @RequestBody MenuRequest request) {
        return Result.ok(service.create(request), "新增菜单成功");
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:menu:update')")
    Result<MenuEntity> update(@PathVariable Long id, @Valid @RequestBody MenuRequest request) {
        return Result.ok(service.update(id, request), "修改菜单成功");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:menu:delete')")
    Result<Void> delete(@PathVariable Long id) {
        service.remove(id);
        return Result.ok(null, "删除菜单成功");
    }
}
