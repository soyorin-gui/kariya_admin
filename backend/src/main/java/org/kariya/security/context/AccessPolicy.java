package org.kariya.security.context;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.kariya.common.exception.BusinessException;
import org.kariya.common.exception.UnauthorizedException;
import org.kariya.system.dept.DeptEntity;
import org.kariya.system.dept.DeptMapper;
import org.kariya.system.menu.MenuEntity;
import org.kariya.system.menu.MenuMapper;
import org.kariya.system.role.RoleEntity;
import org.kariya.system.role.RoleMapper;
import org.kariya.system.role.RoleMenuMapper;
import org.kariya.system.user.UserEntity;
import org.kariya.system.user.UserMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AccessPolicy {
    private final UserMapper users;
    private final RoleMapper roles;
    private final RoleMenuMapper roleMenus;
    private final MenuMapper menus;
    private final DeptMapper depts;

    public AccessPolicy(UserMapper users, RoleMapper roles, RoleMenuMapper roleMenus, MenuMapper menus, DeptMapper depts) {
        this.users = users;
        this.roles = roles;
        this.roleMenus = roleMenus;
        this.menus = menus;
        this.depts = depts;
    }

    public Actor actor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) throw new UnauthorizedException("登录状态已失效");
        UserEntity user = users.selectOne(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername, authentication.getName()));
        if (user == null || user.getStatus() != 1) throw new UnauthorizedException("登录状态已失效");
        List<RoleEntity> assigned = roles.selectAssignedByUserId(user.getId());
        boolean superAdmin = assigned.stream().anyMatch(role -> "super_admin".equals(role.getRoleCode()) && role.getStatus() == 1);
        Set<String> permissions = permissions(user.getId());
        Set<Long> visibleDepartments = new HashSet<>();
        boolean all = superAdmin;
        boolean self = false;
        for (RoleEntity role : assigned) {
            if (role.getStatus() != 1) continue;
            switch (role.getDataScope()) {
                case "ALL" -> all = true;
                case "DEPT_AND_CHILDREN" -> {
                    visibleDepartments.add(user.getDeptId());
                    DeptEntity ownDept = depts.selectById(user.getDeptId());
                    if (ownDept != null) {
                        String path = ownDept.getAncestors() + "," + ownDept.getId();
                        depts.selectList(new LambdaQueryWrapper<DeptEntity>().and(query ->
                                        query.eq(DeptEntity::getAncestors, path).or().likeRight(DeptEntity::getAncestors, path + ",")))
                                .stream().map(DeptEntity::getId).forEach(visibleDepartments::add);
                    }
                }
                case "DEPT" -> visibleDepartments.add(user.getDeptId());
                case "SELF" -> self = true;
                default -> throw new BusinessException("角色的数据范围配置无效");
            }
        }
        return new Actor(user, superAdmin, permissions, all, self, visibleDepartments,
                assigned.stream().filter(role -> role.getStatus() == 1).mapToInt(role -> scopeRank(role.getDataScope())).max().orElse(0));
    }

    public void applyUserScope(LambdaQueryWrapper<UserEntity> query, Actor actor) {
        if (actor.all()) return;
        query.and(condition -> {
            if (!actor.departments().isEmpty()) condition.in(UserEntity::getDeptId, actor.departments());
            else condition.eq(UserEntity::getId, actor.user().getId());
            if (actor.self() && !actor.departments().isEmpty()) condition.or().eq(UserEntity::getId, actor.user().getId());
        });
    }

    public boolean canManageUser(Actor actor, UserEntity target) {
        if (actor.superAdmin()) return true;
        if (target.getId().equals(actor.user().getId()) || target.getBuiltin() == 1 || !canSeeUser(actor, target)) return false;
        List<RoleEntity> targetRoles = roles.selectAssignedByUserId(target.getId());
        if (targetRoles.stream().anyMatch(role -> "super_admin".equals(role.getRoleCode()))) return false;
        Set<String> targetPermissions = permissions(target.getId());
        return actor.permissions().containsAll(targetPermissions) && !actor.permissions().equals(targetPermissions)
                && targetRoles.stream().filter(role -> role.getStatus() == 1)
                .allMatch(role -> scopeRank(role.getDataScope()) <= actor.maxScopeRank());
    }

    public void requireManageUser(Actor actor, UserEntity target) {
        if (!canManageUser(actor, target)) throw new BusinessException("不能管理同级或更高权限的用户");
    }

    public boolean canAssignRole(Actor actor, RoleEntity role) {
        if (role.getStatus() != 1) return false;
        if (actor.superAdmin()) return true;
        if (role.getBuiltin() == 1 || "super_admin".equals(role.getRoleCode())) return false;
        Set<String> granted = rolePermissions(role.getId());
        return actor.permissions().containsAll(granted) && !actor.permissions().equals(granted)
                && scopeRank(role.getDataScope()) <= actor.maxScopeRank();
    }

    public void requireAssignableRoles(Actor actor, List<RoleEntity> assigned) {
        if (actor.superAdmin()) return;
        Set<String> combined = new HashSet<>();
        assigned.forEach(role -> combined.addAll(rolePermissions(role.getId())));
        if (combined.equals(actor.permissions())) throw new BusinessException("不能分配与自身同级的权限组合");
    }

    public void requireManageRole(Actor actor, RoleEntity role) {
        if (!canManageRole(actor, role)) throw new BusinessException("不能管理同级或更高权限的角色");
    }

    public boolean canManageRole(Actor actor, RoleEntity role) {
        if (actor.superAdmin()) return true;
        Set<String> granted = rolePermissions(role.getId());
        return role.getBuiltin() != 1 && actor.permissions().containsAll(granted)
                && !actor.permissions().equals(granted) && scopeRank(role.getDataScope()) <= actor.maxScopeRank();
    }

    public void requireScope(Actor actor, String dataScope) {
        int rank = scopeRank(dataScope);
        if (!actor.superAdmin() && rank > actor.maxScopeRank()) throw new BusinessException("不能设置超过自身的数据范围");
    }

    public void requireGrantableMenus(Actor actor, List<Long> menuIds) {
        if (actor.superAdmin()) return;
        Set<Long> owned = menus.selectByUserId(actor.user().getId()).stream().map(MenuEntity::getId).collect(Collectors.toSet());
        if (!owned.containsAll(menuIds)) throw new BusinessException("不能授予自身没有的菜单权限");
    }

    private boolean canSeeUser(Actor actor, UserEntity target) {
        return actor.all() || actor.departments().contains(target.getDeptId())
                || actor.self() && target.getId().equals(actor.user().getId());
    }

    private Set<String> permissions(Long userId) {
        Set<String> values = new HashSet<>();
        menus.selectByUserId(userId).stream().map(MenuEntity::getPermissionCode)
                .filter(code -> code != null && !code.isBlank()).forEach(values::add);
        return values;
    }

    private Set<String> rolePermissions(Long roleId) {
        Set<String> values = new HashSet<>();
        for (Long menuId : roleMenus.selectMenuIds(roleId)) {
            MenuEntity menu = menus.selectById(menuId);
            if (menu != null && menu.getStatus() == 1 && menu.getPermissionCode() != null) values.add(menu.getPermissionCode());
        }
        return values;
    }

    private int scopeRank(String scope) {
        return switch (scope) {
            case "SELF" -> 1;
            case "DEPT" -> 2;
            case "DEPT_AND_CHILDREN" -> 3;
            case "ALL" -> 4;
            default -> throw new BusinessException("数据范围必须是 ALL、DEPT_AND_CHILDREN、DEPT 或 SELF");
        };
    }

    public record Actor(UserEntity user, boolean superAdmin, Set<String> permissions, boolean all, boolean self,
                        Set<Long> departments, int maxScopeRank) {
    }
}
