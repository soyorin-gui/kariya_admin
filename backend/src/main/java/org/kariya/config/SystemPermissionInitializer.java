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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Keeps built-in management permissions available after upgrading an existing database.
 * <p>
 * 每次启动幂等执行：缺失的页面菜单/按钮权限按需补建，并授予超级管理员。
 * 之所以把"新增模块的菜单"放在这里而不是只写进 init__data.sql：init__data.sql 只在建库时跑一次，
 * 老库升级时不会重新执行，结果就是"代码里有这个页面，菜单树里却没有入口"，
 * 只能靠人工去菜单管理里点出来。放在这里则代码一升级、菜单自动补齐。
 */
@Component
@Order(100)
public class SystemPermissionInitializer implements ApplicationRunner {
    private static final String SYSTEM_DIRECTORY_ROUTE = "/system";

    /** 需要在老库中补建的页面级菜单（MENU 类型）。先建页面，再挂按钮。 */
    private static final List<PageSeed> PAGES = List.of(
            new PageSeed("登录日志", "system-login-log", "/system/login-log", "system/loginlog/index", "FileSearchOutlined", 5),
            new PageSeed("操作日志", "system-operation-log", "/system/operation-log", "system/operatelog/index", "HistoryOutlined", 6)
    );

    /** 按钮级权限。parentRoute 指向所属页面菜单的 route_path。 */
    private static final List<PermissionSeed> PERMISSIONS = List.of(
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
            new PermissionSeed("删除菜单", "system:menu:delete", "/system/menu", 4),
            new PermissionSeed("查询登录日志", "system:loginlog:list", "/system/login-log", 1),
            new PermissionSeed("删除登录日志", "system:loginlog:delete", "/system/login-log", 2),
            new PermissionSeed("查询操作日志", "system:operatelog:list", "/system/operation-log", 1),
            new PermissionSeed("删除操作日志", "system:operatelog:delete", "/system/operation-log", 2)
    );

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
        // 一次性取出已授权菜单，避免逐个种子去查库。
        Set<Long> grantedToSuperAdmin = new HashSet<>(roleMenus.selectMenuIds(superAdmin.getId()));
        for (PageSeed page : PAGES) ensurePage(page, superAdmin.getId(), grantedToSuperAdmin);
        for (PermissionSeed permission : PERMISSIONS) ensureButton(permission, superAdmin.getId(), grantedToSuperAdmin);
    }

    private void ensurePage(PageSeed seed, Long superAdminId, Set<Long> granted) {
        MenuEntity menu = menus.selectOne(new LambdaQueryWrapper<MenuEntity>().eq(MenuEntity::getRoutePath, seed.routePath()));
        if (menu == null) {
            MenuEntity parent = menus.selectOne(new LambdaQueryWrapper<MenuEntity>().eq(MenuEntity::getRoutePath, SYSTEM_DIRECTORY_ROUTE));
            if (parent == null) return;
            menu = new MenuEntity();
            menu.setParentId(parent.getId());
            menu.setMenuName(seed.name());
            menu.setMenuType("MENU");
            menu.setRouteName(seed.routeName());
            menu.setRoutePath(seed.routePath());
            menu.setComponent(seed.component());
            menu.setIcon(seed.icon());
            menu.setSortOrder(seed.sortOrder());
            menu.setVisible(1);
            menu.setStatus(1);
            menu.setKeepAlive(0);
            menu.setBuiltin(1);
            menus.insert(menu);
        }
        grant(superAdminId, menu.getId(), granted);
    }

    private void ensureButton(PermissionSeed seed, Long superAdminId, Set<Long> granted) {
        MenuEntity button = menus.selectOne(new LambdaQueryWrapper<MenuEntity>().eq(MenuEntity::getPermissionCode, seed.code()));
        if (button == null) {
            MenuEntity parent = menus.selectOne(new LambdaQueryWrapper<MenuEntity>().eq(MenuEntity::getRoutePath, seed.parentRoute()));
            if (parent == null) return;
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
        grant(superAdminId, button.getId(), granted);
    }

    private void grant(Long roleId, Long menuId, Set<Long> granted) {
        if (granted.add(menuId)) roleMenus.insert(roleId, menuId);
    }

    private record PageSeed(String name, String routeName, String routePath, String component, String icon, int sortOrder) {
    }

    private record PermissionSeed(String name, String code, String parentRoute, int sortOrder) {
    }
}
