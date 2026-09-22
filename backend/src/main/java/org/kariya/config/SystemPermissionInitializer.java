package org.kariya.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.kariya.system.menu.MenuEntity;
import org.kariya.system.menu.MenuMapper;
import org.kariya.system.role.RoleEntity;
import org.kariya.system.role.RoleMapper;
import org.kariya.system.role.RoleMenuMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Keeps built-in management permissions available after upgrading an existing database. */
@Component
@Order(100)
public class SystemPermissionInitializer implements ApplicationRunner {
    private final MenuMapper menus;
    private final RoleMapper roles;
    private final RoleMenuMapper roleMenus;

    public SystemPermissionInitializer(MenuMapper menus, RoleMapper roles, RoleMenuMapper roleMenus) {
        this.menus = menus;
        this.roles = roles;
        this.roleMenus = roleMenus;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        RoleEntity superAdmin = roles.selectOne(new LambdaQueryWrapper<RoleEntity>().eq(RoleEntity::getRoleCode, "super_admin"));
        if (superAdmin == null) return;
        List<PermissionSeed> seeds = List.of(
                new PermissionSeed("查询角色", "system:role:list", "/system/role", 1),
                new PermissionSeed("新增角色", "system:role:add", "/system/role", 2),
                new PermissionSeed("更新角色", "system:role:update", "/system/role", 3),
                new PermissionSeed("删除角色", "system:role:delete", "/system/role", 4),
                new PermissionSeed("授权角色", "system:role:grant", "/system/role", 5),
                new PermissionSeed("查询部门", "system:dept:list", "/system/dept", 1),
                new PermissionSeed("新增部门", "system:dept:add", "/system/dept", 2),
                new PermissionSeed("更新部门", "system:dept:update", "/system/dept", 3),
                new PermissionSeed("删除部门", "system:dept:delete", "/system/dept", 4),
                new PermissionSeed("查询菜单", "system:menu:list", "/system/menu", 1),
                new PermissionSeed("新增菜单", "system:menu:add", "/system/menu", 2),
                new PermissionSeed("更新菜单", "system:menu:update", "/system/menu", 3),
                new PermissionSeed("删除菜单", "system:menu:delete", "/system/menu", 4)
        );
        for (PermissionSeed seed : seeds) {
            MenuEntity button = menus.selectOne(new LambdaQueryWrapper<MenuEntity>().eq(MenuEntity::getPermissionCode, seed.code()));
            if (button == null) {
                MenuEntity parent = menus.selectOne(new LambdaQueryWrapper<MenuEntity>().eq(MenuEntity::getRoutePath, seed.parentRoute()));
                if (parent == null) continue;
                button = new MenuEntity();
                button.setParentId(parent.getId());
                button.setMenuName(seed.name());
                button.setMenuType("BUTTON");
                button.setPermissionCode(seed.code());
                button.setSortOrder(seed.sortOrder());
                button.setVisible(1);
                button.setStatus(1);
                button.setKeepAlive(0);
                button.setBuiltin(1);
                menus.insert(button);
            }
            if (!roleMenus.selectMenuIds(superAdmin.getId()).contains(button.getId())) {
                roleMenus.insert(superAdmin.getId(), button.getId());
            }
        }
    }

    private record PermissionSeed(String name, String code, String parentRoute, int sortOrder) {
    }
}
