package org.lbl.system.role.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.lbl.common.exception.BusinessException;
import org.lbl.security.context.AccessPolicy;
import org.lbl.system.menu.entity.MenuEntity;
import org.lbl.system.menu.mapper.MenuMapper;
import org.lbl.system.role.mapper.RoleMapper;
import org.lbl.system.role.mapper.RoleMenuMapper;
import org.lbl.system.role.vo.RoleVO;
import org.lbl.system.role.entity.RoleEntity;
import org.lbl.system.role.request.RoleRequest;
import org.lbl.system.user.mapper.UserRoleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.time.LocalDateTime;

@Service
public class RoleService {
    private final RoleMapper roles;
    private final RoleMenuMapper roleMenus;
    private final MenuMapper menus;
    private final UserRoleMapper userRoles;
    private final AccessPolicy access;

    public RoleService(RoleMapper roles, RoleMenuMapper roleMenus, MenuMapper menus, UserRoleMapper userRoles, AccessPolicy access) {
        this.roles = roles;
        this.roleMenus = roleMenus;
        this.menus = menus;
        this.userRoles = userRoles;
        this.access = access;
    }

    public List<RoleVO> list(String keyword) {
        AccessPolicy.Actor actor = access.actor();
        List<RoleEntity> list = roles.selectList(new LambdaQueryWrapper<RoleEntity>()
                        .and(keyword != null && !keyword.isBlank(), q -> q.like(RoleEntity::getRoleName, keyword).or().like(RoleEntity::getRoleCode, keyword))
                        .orderByAsc(RoleEntity::getId));
        if (list.isEmpty()) return List.of();
        List<Long> ids = list.stream().map(RoleEntity::getId).toList();
        Map<Long, Long> counts = userRoles.countByRoleIds(ids).stream().collect(Collectors.toMap(
                value -> value.getRoleId(), value -> value.getUserCount()));
        Map<Long, Set<String>> permissions = actor.superAdmin() ? Map.of() : rolePermissions(ids);
        return list.stream().map(role -> toView(role, actor, counts.getOrDefault(role.getId(), 0L),
                permissions.getOrDefault(role.getId(), Set.of()))).toList();
    }

    public RoleVO detail(Long id) {
        AccessPolicy.Actor actor = access.actor();
        RoleEntity role = require(id);
        Set<String> permissions = actor.superAdmin() ? Set.of() : accessRolePermissions(id);
        access.requireManageRole(actor, role, permissions);
        return toView(role, actor, userRoles.countByRoleId(id), permissions);
    }

    @Transactional
    public RoleVO create(RoleRequest request) {
        AccessPolicy.Actor actor = access.actor();
        access.requireScope(actor, request.dataScope());
        String code = request.roleCode().trim();
        // 口径与 sys_role.role_code 的唯一索引一致（含已逻辑删除的记录），理由同 DeptService.create：
        // 删除只置 deleted=1，标识不会被释放，必须在这里挡住而不是让 INSERT 撞唯一键。
        if (roles.countIncludingDeletedByRoleCode(code, 0L) > 0) {
            throw new BusinessException("角色标识已被占用（已删除角色占用的标识不会被释放，请换一个）");
        }
        RoleEntity role = new RoleEntity();
        apply(role, request);
        role.setBuiltin(0);
        roles.insert(role);
        return toView(role, actor);
    }

    @Transactional
    public RoleVO update(Long id, RoleRequest request) {
        AccessPolicy.Actor actor = access.actor();
        RoleEntity role = require(id);
        access.requireManageRole(actor, role);
        access.requireScope(actor, request.dataScope());
        if (request.status() != 0 && request.status() != 1) throw new BusinessException("角色状态无效");
        String code = request.roleCode().trim();
        if (roles.countIncludingDeletedByRoleCode(code, id) > 0) {
            throw new BusinessException("角色标识已被占用（已删除角色占用的标识不会被释放，请换一个）");
        }
        if (role.getBuiltin() == 1 && (!role.getRoleCode().equals(code) || request.status() != 1)) {
            throw new BusinessException("内置角色不能修改标识或禁用");
        }
        apply(role, request);
        roles.updateById(role);
        return toView(role, actor);
    }

    @Transactional
    public void remove(Long id) {
        AccessPolicy.Actor actor = access.actor();
        RoleEntity role = require(id);
        access.requireManageRole(actor, role);
        if (role.getBuiltin() == 1) throw new BusinessException("内置角色不能删除");
        if (userRoles.countByRoleId(id) > 0) throw new BusinessException("该角色已分配给用户，不能删除");
        roleMenus.deleteByRoleId(id);
        role.setDeletedTime(LocalDateTime.now());
        roles.updateById(role);
        roles.deleteById(id);
    }

    public List<Long> menuIds(Long id) {
        access.requireManageRole(access.actor(), require(id));
        // 授权树不会展示平台级节点；这里也必须使用同一口径。
        // 否则 super_admin 已持有的菜单定义/重置密码 id 会藏在树外、却仍进 checkedKeys，
        // 用户不改任何内容直接保存也会被 grantMenus 拒绝。
        Set<Long> platformOnly = AccessPolicy.platformOnlyMenuIds(allMenus());
        return roleMenus.selectMenuIds(id).stream().filter(menuId -> !platformOnly.contains(menuId)).toList();
    }

    /**
     * 角色授权树。
     * <p>
     * 超管拿全量树，非超管只拿"自己已经拥有的"（避免越权授予）。两种情况下都会剔除
     * <b>平台级权限</b>（菜单定义、重置密码，见 {@link AccessPolicy#PLATFORM_ONLY_PERMISSION_CODES}）：
     * 这些权限无论授给谁都无法行使（MenuService / UserService 里还有 requireSuperAdmin 硬校验），
     * 留在树里只会让管理员勾出一个"侧边栏有入口、点进去全是 403"的死页面。
     * <p>
     * 注意剔除是"连同失去全部后代的父节点一起"做的：菜单管理页下 4 个按钮全是平台级，
     * 于是"菜单管理"这个页面节点也会消失，用户根本看不到这几行，从源头避免误勾。
     */
    public List<MenuEntity> grantableMenus() {
        AccessPolicy.Actor actor = access.actor();
        if (actor.superAdmin()) {
            return AccessPolicy.omitPlatformOnlyMenus(menus.selectList(new LambdaQueryWrapper<MenuEntity>()
                    .eq(MenuEntity::getStatus, 1)
                    .orderByAsc(MenuEntity::getParentId).orderByAsc(MenuEntity::getSortOrder).orderByAsc(MenuEntity::getId)));
        }
        return AccessPolicy.omitPlatformOnlyMenus(menus.selectByUserId(actor.user().getId()));
    }

    @Transactional
    public void grantMenus(Long id, List<Long> menuIds) {
        AccessPolicy.Actor actor = access.actor();
        RoleEntity role = require(id);
        access.requireManageRole(actor, role);
        List<Long> ids = menuIds == null ? List.of() : menuIds.stream().filter(value -> value != null && value > 0).distinct().toList();
        if (!ids.isEmpty() && menus.selectCount(new LambdaQueryWrapper<MenuEntity>().in(MenuEntity::getId, ids)) != ids.size()) {
            throw new BusinessException("授权菜单中包含不存在的记录");
        }
        access.requireGrantableMenus(actor, ids);
        // 纵深防御：授权树已经过滤掉平台级权限，这里再拦一次，防止绕过界面直接调接口
        // 把"永远用不了"的权限写进 sys_role_menu。对超管同样拒绝 —— 授下去也没有任何意义。
        Set<Long> platformOnly = AccessPolicy.platformOnlyMenuIds(allMenus());
        AccessPolicy.requireNotPlatformOnly(ids, platformOnly);
        if (!actor.superAdmin()) {
            Set<String> grantedCodes = ids.isEmpty() ? Set.of() : menus.selectBatchIds(ids).stream()
                    .filter(menu -> menu.getPermissionCode() != null && menu.getStatus() == 1)
                    .map(MenuEntity::getPermissionCode).collect(Collectors.toSet());
            if (grantedCodes.equals(actor.permissions())) throw new BusinessException("不能创建与自身同级的角色");
        }
        // 平台级权限始终不出现在可勾选树里。超级管理员已有的这部分权限是系统内置能力，
        // 保存普通菜单授权时必须原样保留；其他角色若留有历史脏数据，则在本次保存时自然清掉。
        List<Long> retainedPlatformOnly = "super_admin".equals(role.getRoleCode())
                ? roleMenus.selectMenuIds(id).stream().filter(platformOnly::contains).toList()
                : List.of();
        roleMenus.deleteByRoleId(id);
        retainedPlatformOnly.forEach(menuId -> roleMenus.insert(id, menuId));
        ids.forEach(menuId -> roleMenus.insert(id, menuId));
    }

    /**
     * 全量菜单（含停用），仅用于计算"哪些 id 属于平台级"。
     * <p>
     * 刻意不过滤 status：如果某条平台级权限被停用，它同样不该被授予；若只查 status=1，
     * 停用状态下这个 id 就不在"平台级集合"里，反而能被授出去。
     */
    private List<MenuEntity> allMenus() {
        return menus.selectList(new LambdaQueryWrapper<>());
    }

    private void apply(RoleEntity role, RoleRequest request) {
        if (request.status() != 0 && request.status() != 1) throw new BusinessException("角色状态无效");
        role.setRoleName(request.roleName().trim());
        role.setRoleCode(request.roleCode().trim());
        role.setDataScope(request.dataScope());
        role.setStatus(request.status());
    }

    private RoleEntity require(Long id) {
        RoleEntity role = roles.selectById(id);
        if (role == null) throw new BusinessException("角色不存在");
        return role;
    }

    private RoleVO toView(RoleEntity role, AccessPolicy.Actor actor) {
        return toView(role, actor, userRoles.countByRoleId(role.getId()),
                actor.superAdmin() ? Set.of() : accessRolePermissions(role.getId()));
    }

    private RoleVO toView(RoleEntity role, AccessPolicy.Actor actor, long userCount, Set<String> permissions) {
        return new RoleVO(role.getId(), role.getRoleName(), role.getRoleCode(), role.getDataScope(), role.getStatus(),
                role.getBuiltin(), userCount, role.getCreatedTime(), access.canManageRole(actor, role, permissions));
    }

    private Set<String> accessRolePermissions(Long roleId) {
        return new HashSet<>(roleMenus.selectPermissionCodes(roleId));
    }

    private Map<Long, Set<String>> rolePermissions(List<Long> roleIds) {
        if (roleIds.isEmpty()) return Map.of();
        Map<Long, Set<String>> result = new HashMap<>();
        roleMenus.selectPermissionCodesByRoleIds(roleIds).forEach(row ->
                result.computeIfAbsent(row.getRoleId(), ignored -> new HashSet<>()).add(row.getPermissionCode()));
        return result;
    }
}
