package org.lbl.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.lbl.system.menu.entity.MenuEntity;
import org.lbl.system.menu.mapper.MenuMapper;
import org.lbl.system.role.entity.RoleEntity;
import org.lbl.system.role.mapper.RoleMapper;
import org.lbl.system.role.mapper.RoleMenuMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 确保已有数据库升级后仍具备内置管理权限。
 * <p>
 * 每次启动幂等执行：缺失的页面菜单/按钮权限按需补建，并授予超级管理员。
 * 之所以把"新增模块的菜单"放在这里而不是只写进 init_data.sql：init_data.sql 只在建库时跑一次，
 * 老库升级时不会重新执行，结果就是"代码里有这个页面，菜单树里却没有入口"，
 * 只能靠人工去菜单管理里点出来。放在这里则代码一升级、菜单自动补齐。
 *
 * <h2>这里跑在 ApplicationRunner 里，所以"查不到/查到多条"都不能抛异常</h2>
 * 本类的任何异常都会让<b>整个应用启动失败</b>，而它做的事只是"补几条内置菜单"。
 * 因此对两种常见脏数据都做了降级处理，而不是让它们变成"必须改库才能启动"：
 * <ul>
 *   <li><b>同一 {@code route_path} / {@code permission_code} 存在多条记录</b>（历史手工改库、
 *       或唯一索引加约束之前留下的重复数据）：{@code selectOne} 遇到多行会抛
 *       {@code TooManyResultsException}，这里改为固定取 id 最小的一条 + WARN，
 *       把"起不来"降级成"能用且有告警"。</li>
 *   <li><b>多实例同时启动</b>：两边都查不到 → 都插入 → 一方撞唯一索引。
 *       捕获 {@code DuplicateKeyException} 后重新读回对方插入的那条继续授权。</li>
 * </ul>
 */
@Component
@Order(100)
public class SystemPermissionInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SystemPermissionInitializer.class);
    private static final String SYSTEM_DIRECTORY_ROUTE = "/system";
    private static final String SUPER_ADMIN_ROLE_CODE = "super_admin";

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
        RoleEntity superAdmin = findSuperAdminRole();
        if (superAdmin == null) {
            log.warn("No role with role_code='{}' found; skipping built-in menu seeding. "
                    + "The management pages will have no permission entries until it is restored.", SUPER_ADMIN_ROLE_CODE);
            return;
        }
        // 一次性取出已授权菜单，避免逐个种子去查库。
        Set<Long> grantedToSuperAdmin = new HashSet<>(roleMenus.selectMenuIds(superAdmin.getId()));
        for (PageSeed page : PAGES) ensurePage(page, superAdmin.getId(), grantedToSuperAdmin);
        for (PermissionSeed permission : PERMISSIONS) ensureButton(permission, superAdmin.getId(), grantedToSuperAdmin);
    }

    private RoleEntity findSuperAdminRole() {
        return first(warnOnDuplicates(roles.selectList(new LambdaQueryWrapper<RoleEntity>()
                .eq(RoleEntity::getRoleCode, SUPER_ADMIN_ROLE_CODE)
                .orderByAsc(RoleEntity::getId)), "role_code='" + SUPER_ADMIN_ROLE_CODE + "'"));
    }

    private void ensurePage(PageSeed seed, Long superAdminId, Set<Long> granted) {
        MenuEntity menu = findByRoutePath(seed.routePath());
        if (menu == null) {
            MenuEntity parent = findByRoutePath(SYSTEM_DIRECTORY_ROUTE);
            if (parent == null) {
                log.warn("Skipped seeding page menu '{}' ({}): the '{}' directory menu does not exist",
                        seed.name(), seed.routePath(), SYSTEM_DIRECTORY_ROUTE);
                return;
            }
            MenuEntity created = new MenuEntity();
            created.setParentId(parent.getId());
            created.setMenuName(seed.name());
            created.setMenuType("MENU");
            created.setRouteName(seed.routeName());
            created.setRoutePath(seed.routePath());
            created.setComponent(seed.component());
            created.setIcon(seed.icon());
            created.setSortOrder(seed.sortOrder());
            created.setVisible(1);
            created.setStatus(1);
            created.setKeepAlive(0);
            created.setBuiltin(1);
            menu = insertReusingRacingRow(created, () -> findByRoutePath(seed.routePath()),
                    () -> menus.selectAnyByRoutePath(seed.routePath()), "page menu " + seed.routePath());
            if (menu == null) return;
        }
        grant(superAdminId, menu.getId(), granted);
    }

    private void ensureButton(PermissionSeed seed, Long superAdminId, Set<Long> granted) {
        MenuEntity button = findByPermissionCode(seed.code());
        if (button == null) {
            MenuEntity parent = findByRoutePath(seed.parentRoute());
            if (parent == null) {
                log.warn("Skipped seeding button permission '{}' ({}): the '{}' page menu does not exist",
                        seed.name(), seed.code(), seed.parentRoute());
                return;
            }
            MenuEntity created = new MenuEntity();
            created.setParentId(parent.getId());
            created.setMenuName(seed.name());
            created.setMenuType("BUTTON");
            created.setPermissionCode(seed.code());
            created.setSortOrder(seed.sortOrder());
            created.setVisible(1);
            created.setStatus(1);
            created.setKeepAlive(0);
            created.setBuiltin(1);
            button = insertReusingRacingRow(created, () -> findByPermissionCode(seed.code()),
                    () -> menus.selectAnyByPermissionCode(seed.code()), "button " + seed.code());
            if (button == null) return;
        }
        grant(superAdminId, button.getId(), granted);
    }

    private void grant(Long roleId, Long menuId, Set<Long> granted) {
        if (!granted.add(menuId)) return;
        try {
            roleMenus.insert(roleId, menuId);
        } catch (DuplicateKeyException ex) {
            // 多实例同时启动时另一边已经授过同一条 (role_id, menu_id)。这不算错误。
            log.info("Grant of menu {} to role {} already exists (concurrent startup)", menuId, roleId);
        }
    }

    /**
     * 插入一条内置菜单；若并发启动的另一实例已经插入同一条（撞唯一索引），
     * 则改为复用对方那条记录而不是让启动失败。
     *
     * @param reRead                 按"未删除"口径重新读取（正常并发场景）
     * @param reReadIncludingDeleted 按"含已删除"口径重新读取（判断是否被软删除的历史记录占位）
     * @return 实际生效的记录；无法继续补建时返回 null（调用方跳过该条）
     */
    private MenuEntity insertReusingRacingRow(MenuEntity menu, Supplier<MenuEntity> reRead,
                                              Supplier<MenuEntity> reReadIncludingDeleted, String description) {
        try {
            menus.insert(menu);
            return menu;
        } catch (DuplicateKeyException ex) {
            MenuEntity existing = reRead.get();
            if (existing != null) {
                log.info("{} already created by a concurrent startup; reusing id={}", description, existing.getId());
                return existing;
            }
            if (reReadIncludingDeleted.get() != null) {
                // 唯一索引看不到 deleted：一条已被逻辑删除的记录仍然占着这个路由地址 / 权限标识。
                // 继续补建只会一直撞唯一键，但也不该因此让应用起不来 —— 记 ERROR 并跳过这一条，
                // 由人工决定是恢复那条历史记录、清掉它，还是换一个值。
                log.error("Cannot seed {}: the value is still occupied by a soft-deleted menu row. "
                        + "Restore or physically remove that row (or change the value) and restart.", description);
                return null;
            }
            // 撞了唯一键、按两种口径都查不到：属于预期之外的数据异常，不能吞掉。
            throw ex;
        }
    }

    private MenuEntity findByRoutePath(String routePath) {
        return first(warnOnDuplicates(menus.selectList(new LambdaQueryWrapper<MenuEntity>()
                .eq(MenuEntity::getRoutePath, routePath)
                .orderByAsc(MenuEntity::getId)), "route_path='" + routePath + "'"));
    }

    private MenuEntity findByPermissionCode(String permissionCode) {
        return first(warnOnDuplicates(menus.selectList(new LambdaQueryWrapper<MenuEntity>()
                .eq(MenuEntity::getPermissionCode, permissionCode)
                .orderByAsc(MenuEntity::getId)), "permission_code='" + permissionCode + "'"));
    }

    /**
     * 只取第一条，绝不用 {@code selectOne}：后者遇到多行会抛 {@code TooManyResultsException}，
     * 而这个类抛异常等于应用启动失败。重复数据只告警，不阻断启动。
     */
    private static <T> T first(List<T> found) {
        return found.isEmpty() ? null : found.get(0);
    }

    private static <T> List<T> warnOnDuplicates(List<T> found, String description) {
        if (found.size() > 1) {
            log.warn("Found {} rows matching {} in the built-in menu seeding path; using the first one. "
                    + "Duplicated permission/route entries should be cleaned up manually.", found.size(), description);
        }
        return found;
    }

    private record PageSeed(String name, String routeName, String routePath, String component, String icon, int sortOrder) {
    }

    private record PermissionSeed(String name, String code, String parentRoute, int sortOrder) {
    }
}
