package org.lbl.security.context;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lbl.common.exception.BusinessException;
import org.lbl.system.dept.mapper.DeptMapper;
import org.lbl.system.dept.entity.DeptEntity;
import org.lbl.system.menu.entity.MenuEntity;
import org.lbl.system.menu.mapper.MenuMapper;
import org.lbl.system.role.entity.RoleEntity;
import org.lbl.system.role.mapper.RoleMapper;
import org.lbl.system.role.mapper.RoleMenuMapper;
import org.lbl.system.user.entity.UserEntity;
import org.lbl.system.user.mapper.UserMapper;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccessPolicyTest {
    private final UserMapper users = mock(UserMapper.class);
    private final RoleMapper roles = mock(RoleMapper.class);
    private final RoleMenuMapper roleMenus = mock(RoleMenuMapper.class);
    private final MenuMapper menus = mock(MenuMapper.class);
    private final DeptMapper depts = mock(DeptMapper.class);
    private AccessPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new AccessPolicy(users, roles, roleMenus, menus, depts);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /** 生效的 super_admin 角色必须挡住管理操作（判定口径见 isSuperAdminRequiresActiveRole）。 */
    @Test
    void cannotManagePeerOrSuperAdminEvenWithinDepartment() {
        AccessPolicy.Actor actor = actor(Set.of("system:user:update", "system:user:list"));
        UserEntity peer = user(2L, 10L);
        when(roles.selectAssignedByUserId(2L)).thenReturn(List.of());
        when(menus.selectByUserId(2L)).thenReturn(List.of(menu("system:user:update"), menu("system:user:list")));
        assertFalse(policy.canManageUser(actor, peer));

        RoleEntity superRole = role(1L, "super_admin", "ALL");
        when(roles.selectAssignedByUserId(2L)).thenReturn(List.of(superRole));
        assertFalse(policy.canManageUser(actor, peer));
    }

    /**
     * 停用的 super_admin 角色不授予任何权限（权限集合按 status=1 过滤），
     * 因此它也不该让持有人被当成超管。/auth/me 与"不可重置"判定都依赖这一条。
     */
    @Test
    void isSuperAdminRequiresActiveRole() {
        assertTrue(AccessPolicy.isSuperAdmin(role(1L, "super_admin", "ALL")));

        RoleEntity disabled = role(1L, "super_admin", "ALL");
        disabled.setStatus(0);
        assertFalse(AccessPolicy.isSuperAdmin(disabled));

        assertFalse(AccessPolicy.isSuperAdmin(role(2L, "system_admin", "ALL")));
        assertFalse(AccessPolicy.isSuperAdmin(null));
        assertTrue(AccessPolicy.containsSuperAdmin(List.of(role(1L, "super_admin", "ALL"))));
        assertFalse(AccessPolicy.containsSuperAdmin(List.of(disabled)));
        assertFalse(AccessPolicy.containsSuperAdmin(null));
    }

    @Test
    void canManageLowerPermissionUserOnlyInsideDataScope() {
        AccessPolicy.Actor actor = actor(Set.of("system:user:update", "system:user:list"));
        UserEntity target = user(2L, 10L);
        when(roles.selectAssignedByUserId(2L)).thenReturn(List.of());
        when(menus.selectByUserId(2L)).thenReturn(List.of(menu("system:user:list")));
        assertTrue(policy.canManageUser(actor, target));
        target.setDeptId(20L);
        assertFalse(policy.canManageUser(actor, target));
    }

    @Test
    void combinedRolesCannotRecreateActorsPermissionSet() {
        AccessPolicy.Actor actor = actor(Set.of("read", "write"));
        RoleEntity reader = role(3L, "reader", "SELF");
        RoleEntity writer = role(4L, "writer", "SELF");
        when(roleMenus.selectMenuIds(3L)).thenReturn(List.of(31L));
        when(roleMenus.selectMenuIds(4L)).thenReturn(List.of(41L));
        when(menus.selectById(31L)).thenReturn(menu("read"));
        when(menus.selectById(41L)).thenReturn(menu("write"));
        assertThrows(BusinessException.class, () -> policy.requireAssignableRoles(actor, List.of(reader, writer)));
    }

    @Test
    void userScopeAddsDepartmentAndSelfFilters() {
        AccessPolicy.Actor actor = actor(Set.of("read"));
        LambdaQueryWrapper<UserEntity> query = new LambdaQueryWrapper<>();
        policy.applyUserScope(query, actor);
        assertFalse(query.isEmptyOfWhere());
    }

    @Test
    void departmentAndChildrenScopeIncludesDescendantsButNotSiblings() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("manager", null, List.of()));
        UserEntity manager = user(1L, 10L);
        manager.setStatus(1);
        when(users.selectOne(any(LambdaQueryWrapper.class))).thenReturn(manager);
        when(roles.selectAssignedByUserId(1L)).thenReturn(List.of(role(5L, "manager", "DEPT_AND_CHILDREN")));
        when(menus.selectByUserId(1L)).thenReturn(List.of());
        DeptEntity child = dept(11L, "0,10");
        DeptEntity grandchild = dept(12L, "0,10,11");
        when(depts.selectById(10L)).thenReturn(dept(10L, "0"));
        when(depts.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(child, grandchild));

        AccessPolicy.Actor actor = policy.actor();

        assertEquals(Set.of(10L, 11L, 12L), actor.departments());
        assertFalse(actor.all());
    }

    // ------------------------------------------------------------------
    // 平台级权限（菜单定义 / 重置密码）：不能出现在授权树里，也不能被授出去
    // ------------------------------------------------------------------

    /**
     * 核心行为：平台级权限按钮被剔除，且"子节点全被剔除"的空壳父页面一并剔除。
     * <p>
     * 后者是必须的：像"菜单管理"这种页面本身没有权限码（权限码在其下属按钮上），
     * 若只删按钮，"菜单管理"会变成一个没有任何复选框、但仍可勾选的节点 —— 勾上之后
     * 又是"侧边栏出现入口、点进去全是 403"。所以空壳页面必须一起消失。
     */
    @Test
    void platformOnlyButtonsAndTheirOrphanedPageAreOmitted() {
        List<MenuEntity> all = List.of(
                node(1L, 0L, "目录", null),
                node(2L, 1L, "用户管理", null),
                node(3L, 2L, "查询用户", "system:user:list"),
                node(4L, 1L, "菜单管理", null),
                node(5L, 4L, "查询菜单", "system:menu:list"),
                node(6L, 4L, "新增菜单", "system:menu:add"),
                node(7L, 4L, "更新菜单", "system:menu:update"),
                node(8L, 4L, "删除菜单", "system:menu:delete"),
                node(9L, 2L, "重置密码", "system:user:reset-password"),
                node(10L, 2L, "导出用户", "system:user:export")
        );

        Set<Long> omitted = AccessPolicy.platformOnlyMenuIds(all);
        // 4 是空壳页面，5-8 是平台级按钮，9 是平台级按钮
        assertEquals(Set.of(4L, 5L, 6L, 7L, 8L, 9L), omitted);

        List<Long> remaining = AccessPolicy.omitPlatformOnlyMenus(all).stream().map(MenuEntity::getId).sorted().toList();
        assertEquals(List.of(1L, 2L, 3L, 10L), remaining);
    }

    /** 用户管理页只该丢掉"重置密码"，页面本身和其他按钮都要留着。 */
    @Test
    void pageWithMixedChildrenKeepsItselfAndOrdinaryButtons() {
        List<MenuEntity> all = List.of(
                node(1L, 0L, "目录", null),
                node(2L, 1L, "用户管理", null),
                node(3L, 2L, "查询用户", "system:user:list"),
                node(4L, 2L, "重置密码", "system:user:reset-password"),
                node(5L, 2L, "导出用户", "system:user:export")
        );
        assertEquals(Set.of(4L), AccessPolicy.platformOnlyMenuIds(all));
        assertEquals(List.of(1L, 2L, 3L, 5L), AccessPolicy.omitPlatformOnlyMenus(all).stream().map(MenuEntity::getId).sorted().toList());
    }

    /**
     * 回归保护：一个"纯页面层级、下面没有任何按钮"的分支必须整体保留。
     * 早期版本按"有没有可行使的后代"递归判定空壳，会把这种分支层层级联删空，
     * 授权树凭空少一大块；现在的规则只依据结构（原有子节点是否全部被剔除），不会误伤。
     */
    @Test
    void pageOnlyBranchWithoutAnyButtonsIsFullyKept() {
        List<MenuEntity> all = List.of(
                node(1L, 0L, "系统管理", null),
                node(2L, 1L, "登录日志", null),
                node(3L, 1L, "操作日志", null),
                node(4L, 0L, "首页", null)
        );
        assertTrue(AccessPolicy.platformOnlyMenuIds(all).isEmpty());
        assertEquals(List.of(1L, 2L, 3L, 4L),
                AccessPolicy.omitPlatformOnlyMenus(all).stream().map(MenuEntity::getId).sorted().toList());
    }

    /** 级联只沿"原有子节点全部被剔除"的结构链走，且必须自底向上，不能只处理一层。 */
    @Test
    void orphanCascadeGoesBottomUp() {
        List<MenuEntity> all = List.of(
                node(1L, 0L, "系统管理", null),
                node(2L, 1L, "菜单管理", null),
                node(3L, 2L, "按钮分组", null),
                node(4L, 3L, "查询菜单", "system:menu:list"),
                node(5L, 1L, "用户管理", null),
                node(6L, 5L, "查询用户", "system:user:list")
        );
        // 4 → 3 空壳 → 2 空壳；1 因为有 5 这个存活子节点而保留
        assertEquals(Set.of(2L, 3L, 4L), AccessPolicy.platformOnlyMenuIds(all));
        assertEquals(List.of(1L, 5L, 6L), AccessPolicy.omitPlatformOnlyMenus(all).stream().map(MenuEntity::getId).sorted().toList());
    }

    /** 平台的码被停用（status=0）时同样不该被授出去，所以计算 id 集合时不能按 status 过滤。 */
    @Test
    void platformOnlyDetectionIgnoresStatus() {
        MenuEntity disabled = node(5L, 0L, "查询菜单", "system:menu:list");
        disabled.setStatus(0);
        assertEquals(Set.of(5L), AccessPolicy.platformOnlyMenuIds(List.of(disabled)));
    }

    @Test
    void emptyMenuListIsHandled() {
        assertTrue(AccessPolicy.platformOnlyMenuIds(List.of()).isEmpty());
        assertTrue(AccessPolicy.omitPlatformOnlyMenus(List.of()).isEmpty());
    }

    @Test
    void grantingPlatformOnlyPermissionIsRejected() {
        Set<Long> platformOnly = Set.of(5L, 9L);
        assertThrows(BusinessException.class, () -> AccessPolicy.requireNotPlatformOnly(List.of(1L, 5L), platformOnly));
        // 普通权限、空列表、null 都不应被拦
        assertDoesNotThrow(() -> AccessPolicy.requireNotPlatformOnly(List.of(1L, 2L), platformOnly));
        assertDoesNotThrow(() -> AccessPolicy.requireNotPlatformOnly(List.of(), platformOnly));
        assertDoesNotThrow(() -> AccessPolicy.requireNotPlatformOnly(null, platformOnly));
    }

    private MenuEntity node(Long id, Long parentId, String name, String permission) {
        MenuEntity menu = new MenuEntity();
        menu.setId(id);
        menu.setParentId(parentId);
        menu.setMenuName(name);
        menu.setPermissionCode(permission);
        menu.setStatus(1);
        return menu;
    }

    private AccessPolicy.Actor actor(Set<String> permissions) {
        return new AccessPolicy.Actor(user(1L, 10L), false, permissions, false, true, Set.of(10L), 3);
    }

    private UserEntity user(Long id, Long deptId) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setDeptId(deptId);
        user.setBuiltin(0);
        return user;
    }

    private RoleEntity role(Long id, String code, String scope) {
        RoleEntity role = new RoleEntity();
        role.setId(id);
        role.setRoleCode(code);
        role.setDataScope(scope);
        role.setStatus(1);
        role.setBuiltin(0);
        return role;
    }

    private MenuEntity menu(String permission) {
        MenuEntity menu = new MenuEntity();
        menu.setPermissionCode(permission);
        menu.setStatus(1);
        return menu;
    }

    private DeptEntity dept(Long id, String ancestors) {
        DeptEntity dept = new DeptEntity();
        dept.setId(id);
        dept.setAncestors(ancestors);
        return dept;
    }
}
