package org.kariya.security.context;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kariya.common.exception.BusinessException;
import org.kariya.system.dept.DeptMapper;
import org.kariya.system.dept.DeptEntity;
import org.kariya.system.menu.MenuEntity;
import org.kariya.system.menu.MenuMapper;
import org.kariya.system.role.RoleEntity;
import org.kariya.system.role.RoleMapper;
import org.kariya.system.role.RoleMenuMapper;
import org.kariya.system.user.UserEntity;
import org.kariya.system.user.UserMapper;
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
