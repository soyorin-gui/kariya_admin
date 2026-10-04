package org.lbl.security.context;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.lbl.common.exception.BusinessException;
import org.lbl.common.exception.UnauthorizedException;
import org.lbl.system.dept.entity.DeptEntity;
import org.lbl.system.dept.mapper.DeptMapper;
import org.lbl.system.dept.support.DeptPaths;
import org.lbl.system.menu.entity.MenuEntity;
import org.lbl.system.menu.mapper.MenuMapper;
import org.lbl.system.role.entity.RoleEntity;
import org.lbl.system.role.mapper.RoleMapper;
import org.lbl.system.role.mapper.RoleDeptMapper;
import org.lbl.system.role.mapper.RoleMenuMapper;
import org.lbl.system.user.entity.UserEntity;
import org.lbl.system.user.mapper.UserMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AccessPolicy {
    private final UserMapper users;
    private final RoleMapper roles;
    private final RoleMenuMapper roleMenus;
    private final MenuMapper menus;
    private final DeptMapper depts;
    private final RoleDeptMapper roleDepts;

    public AccessPolicy(UserMapper users, RoleMapper roles, RoleMenuMapper roleMenus, MenuMapper menus,
                        DeptMapper depts, RoleDeptMapper roleDepts) {
        this.users = users;
        this.roles = roles;
        this.roleMenus = roleMenus;
        this.menus = menus;
        this.depts = depts;
        this.roleDepts = roleDepts;
    }

    /** 身份和完整授权上下文；业务读写应使用 actor(permission) 选择对应操作的范围。 */
    public Actor actor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) throw new UnauthorizedException("登录状态已失效");
        UserEntity user = users.selectOne(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername, authentication.getName()));
        if (user == null || user.getStatus() != 1) throw new UnauthorizedException("登录状态已失效");
        List<RoleEntity> assigned = roles.selectAssignedByUserId(user.getId());
        boolean superAdmin = containsSuperAdmin(assigned);
        // 停用部门不影响已有人员与数据范围，仅已删除部门不参与计算。
        Map<Long, DeptEntity> departments = depts.selectList(new LambdaQueryWrapper<DeptEntity>()).stream()
                .collect(Collectors.toMap(DeptEntity::getId, value -> value));
        ScopeContext context = new ScopeContext(departments, new HashMap<>(), new HashMap<>());
        Actor initial = new Actor(user, superAdmin, permissions(user.getId()), Scope.empty(), Map.of(), context);
        prepareRoles(initial, assigned);
        Map<String, Scope> operationScopes = new HashMap<>();
        Scope combined = Scope.empty();
        for (RoleEntity role : assigned) {
            if (role.getStatus() != 1) continue;
            Scope scope = roleScope(initial, role, user);
            combined = combined.union(scope);
            for (String permission : context.rolePermissions().getOrDefault(role.getId(), Set.of())) {
                operationScopes.merge(permission, scope, Scope::union);
            }
        }
        return new Actor(user, superAdmin, initial.permissions(), superAdmin ? Scope.unrestricted() : combined,
                Map.copyOf(operationScopes), context);
    }

    public Actor actor(String... permissions) {
        return forPermissions(actor(), permissions);
    }

    /** 在同一个请求的授权快照中选择操作范围，避免列表按钮逐行回查当前用户。 */
    public Actor forPermissions(Actor actor, String... permissions) {
        Scope scope = Scope.empty();
        for (String permission : permissions) scope = scope.union(operationScope(actor, permission));
        return new Actor(actor.user(), actor.superAdmin(), actor.permissions(), scope, actor.operationScopes(), actor.context());
    }

    private Scope operationScope(Actor actor, String permission) {
        return actor.superAdmin() ? Scope.unrestricted() : actor.operationScopes().getOrDefault(permission, Scope.empty());
    }

    /** 批量预取角色信息；列表和授权检查共享请求内缓存，不逐用户查询自定义部门。 */
    public void prepareRoles(Actor actor, List<RoleEntity> values) {
        List<Long> missing = values.stream().map(RoleEntity::getId).distinct()
                .filter(id -> !actor.context().rolePermissions().containsKey(id)).toList();
        if (!missing.isEmpty()) {
            missing.forEach(id -> actor.context().rolePermissions().put(id, new HashSet<>()));
            roleMenus.selectPermissionCodesByRoleIds(missing).forEach(value ->
                    actor.context().rolePermissions().get(value.getRoleId()).add(value.getPermissionCode()));
        }
        List<Long> custom = values.stream().filter(role -> "CUSTOM".equals(role.getDataScope()))
                .map(RoleEntity::getId).distinct().filter(id -> !actor.context().customDepartments().containsKey(id)).toList();
        if (!custom.isEmpty()) {
            custom.forEach(id -> actor.context().customDepartments().put(id, new HashSet<>()));
            roleDepts.selectByRoleIds(custom).forEach(value ->
                    actor.context().customDepartments().get(value.getRoleId()).add(value.getDeptId()));
        }
    }

    private Scope roleScope(Actor actor, RoleEntity role, UserEntity holder) {
        Map<Long, DeptEntity> available = actor.context().departments();
        Set<Long> ids = new HashSet<>();
        Set<Long> branches = new HashSet<>();
        switch (role.getDataScope()) {
            case "ALL" -> { return Scope.unrestricted(); }
            case "SELF" -> { return new Scope(false, true, Set.of(), Set.of()); }
            case "CUSTOM" -> ids.addAll(actor.context().customDepartments().getOrDefault(role.getId(), Set.of()));
            case "DEPT", "DEPT_AND_CHILDREN" -> {
                DeptEntity own = available.get(holder.getDeptId());
                if (own != null) {
                    ids.add(own.getId());
                    if ("DEPT_AND_CHILDREN".equals(role.getDataScope())) {
                        String path = DeptPaths.selfPath(own);
                        available.values().stream().filter(dept -> DeptPaths.isInsideSubtree(dept.getAncestors(), path))
                                .map(DeptEntity::getId).forEach(ids::add);
                        branches.addAll(ids);
                    }
                }
            }
            default -> throw new BusinessException("角色的数据范围配置无效");
        }
        ids.retainAll(available.keySet());
        return new Scope(false, false, Set.copyOf(ids), Set.copyOf(branches));
    }

    public void applyUserScope(LambdaQueryWrapper<UserEntity> query, Actor actor) {
        if (actor.all()) return;
        query.and(condition -> {
            if (!actor.departments().isEmpty()) {
                condition.in(UserEntity::getDeptId, actor.departments());
                if (actor.self()) condition.or().eq(UserEntity::getId, actor.user().getId());
            } else if (actor.self()) condition.eq(UserEntity::getId, actor.user().getId());
            else condition.apply("1 = 0");
        });
    }

    public boolean canManageUser(Actor actor, UserEntity target) {
        List<RoleEntity> targetRoles = roles.selectAssignedByUserId(target.getId());
        prepareRoles(actor, targetRoles);
        return canManageUser(actor, target, containsSuperAdmin(targetRoles), permissions(target.getId()), targetRoles);
    }

    public boolean canManageUser(Actor actor, UserEntity target, boolean targetSuperAdmin,
                                 Set<String> targetPermissions, List<RoleEntity> targetRoles) {
        if (actor.superAdmin()) return true;
        if (target.getId().equals(actor.user().getId()) || target.getBuiltin() == 1 || !canSeeUser(actor, target) || targetSuperAdmin) return false;
        if (!actor.permissions().containsAll(targetPermissions) || actor.permissions().equals(targetPermissions)) return false;
        return targetRoles.stream().filter(role -> role.getStatus() == 1)
                .allMatch(role -> grantFits(actor, role, target, actor.context().rolePermissions().getOrDefault(role.getId(), Set.of())));
    }

    public void requireManageUser(Actor actor, UserEntity target) {
        if (!canManageUser(actor, target)) throw new BusinessException("不能管理范围之外、同级或更高权限的用户");
    }

    public boolean canAssignRole(Actor actor, RoleEntity role) {
        return canAssignRole(actor, role, rolePermissions(role.getId()));
    }

    public boolean canAssignRole(Actor actor, RoleEntity role, Set<String> granted) {
        if (role.getStatus() != 1) return false;
        if (actor.superAdmin()) return true;
        if (role.getBuiltin() == 1 || "super_admin".equals(role.getRoleCode())) return false;
        prepareRoles(actor, List.of(role));
        return actor.permissions().containsAll(granted) && !actor.permissions().equals(granted)
                && (!Set.of("CUSTOM", "ALL").contains(role.getDataScope()) || grantFits(actor, role, actor.user(), granted));
    }

    /** 根据实际接收人的目标部门计算，避免 DEPT 角色跨部门授予导致提权。 */
    public void requireAssignableRoles(Actor actor, List<RoleEntity> assigned, UserEntity target) {
        if (actor.superAdmin()) return;
        prepareRoles(actor, assigned);
        Set<String> combined = assigned.stream().flatMap(role -> actor.context().rolePermissions()
                .getOrDefault(role.getId(), Set.of()).stream()).collect(Collectors.toSet());
        if (combined.equals(actor.permissions())) throw new BusinessException("不能分配与自身同级的权限组合");
        if (assigned.stream().anyMatch(role -> !grantFits(actor, role, target,
                actor.context().rolePermissions().getOrDefault(role.getId(), Set.of())))) {
            throw new BusinessException("分配后的角色数据范围超出自身可授权范围");
        }
    }

    private boolean grantFits(Actor actor, RoleEntity role, UserEntity target, Set<String> granted) {
        Scope scope = roleScope(actor, role, target);
        return granted.stream().allMatch(permission -> operationScope(actor, permission).contains(scope, actor.user(), target));
    }

    /** 调部门会改变动态角色范围；这类迁移交由平台管理员审批。 */
    public boolean requiresPlatformTransferReview(Actor actor, UserEntity target) {
        List<RoleEntity> dynamic = roles.selectAssignedByUserId(target.getId()).stream()
                .filter(role -> role.getStatus() == 1)
                .filter(role -> Set.of("DEPT", "DEPT_AND_CHILDREN").contains(role.getDataScope())).toList();
        prepareRoles(actor, dynamic);
        return dynamic.stream().anyMatch(role -> !actor.context().rolePermissions().getOrDefault(role.getId(), Set.of()).isEmpty());
    }

    public void requireRoleGrantScope(Actor actor, RoleEntity role, Set<String> granted) {
        prepareRoles(actor, List.of(role));
        if (!actor.superAdmin() && !grantFits(actor, role, actor.user(), granted))
            throw new BusinessException("角色操作对应的数据范围超出自身授权范围");
    }

    public void requireManageRole(Actor actor, RoleEntity role) {
        if (!canManageRole(actor, role)) throw new BusinessException("不能管理同级或更高权限的角色");
    }

    public void requireManageRole(Actor actor, RoleEntity role, Set<String> granted) {
        if (!canManageRole(actor, role, granted)) throw new BusinessException("不能管理同级或更高权限的角色");
    }

    public boolean canManageRole(Actor actor, RoleEntity role) {
        return canManageRole(actor, role, rolePermissions(role.getId()));
    }

    public boolean canManageRole(Actor actor, RoleEntity role, Set<String> granted) {
        if (actor.superAdmin()) return true;
        prepareRoles(actor, List.of(role));
        // 已分配角色的定义由平台管理员维护，避免修改共享角色间接扩大其他持有人的权限。
        return role.getBuiltin() != 1 && actor.permissions().containsAll(granted) && !actor.permissions().equals(granted)
                && grantFits(actor, role, actor.user(), granted);
    }

    public void requireScope(Actor actor, String dataScope) {
        if (!Set.of("ALL", "DEPT_AND_CHILDREN", "DEPT", "SELF", "CUSTOM").contains(dataScope))
            throw new BusinessException("数据范围配置无效");
        if (!actor.superAdmin() && "ALL".equals(dataScope) && !actor.all())
            throw new BusinessException("不能设置超过自身的数据范围");
    }

    public void requireGrantableMenus(Actor actor, List<Long> menuIds) {
        if (actor.superAdmin()) return;
        Set<Long> owned = menus.selectByUserId(actor.user().getId()).stream().map(MenuEntity::getId).collect(Collectors.toSet());
        if (!owned.containsAll(menuIds)) throw new BusinessException("不能授予自身没有的菜单权限");
    }

    /**
     * 只有超级管理员才可能真正使用的权限码 —— 全项目唯一的声明处。
     * <p>
     * 为什么这两个模块要列在这里：
     * <ul>
     *   <li><b>菜单定义</b>（{@code system:menu:*}）：菜单记录里的 {@code component} /
     *       {@code routePath} / {@code permissionCode} 必须与前端页面文件、后端 {@code @PreAuthorize}
     *       严格同步。它不是"配置"，而是"代码的一部分"，因此授权出去没有意义。</li>
     *   <li><b>重置密码</b>（{@code system:user:reset-password}）：能重置别人的密码就等于能接管
     *       那个账号，属于最高敏感操作，只保留给内置超管。</li>
     * </ul>
     * 这两组权限在 {@code MenuService} / {@code UserService} 里都还有一层
     * {@code requireSuperAdmin} 硬校验（那是真正的边界）。因此把它们授给非超管，
     * 只会得到"侧边栏有入口、点进去全是 403"的死页面 —— 界面在骗人。
     * <p>
     * 所以这里把"不可行使"的权限显式声明出来，由 {@link #platformOnlyMenuIds()} 与
     * {@link #omitPlatformOnlyMenus(List)} 统一过滤，让授权树里压根不出现这些复选框。
     * <b>把知识集中在这一处</b>，是刻意的：之前 system:user:export 的补建就因为没有
     * 单一真相来源，在三份种子/补建逻辑之间漂移过。
     */
    public static final Set<String> PLATFORM_ONLY_PERMISSION_CODES = Set.of(
            "system:menu:list",
            "system:menu:add",
            "system:menu:update",
            "system:menu:delete",
            "system:user:reset-password"
    );

    /**
     * 从菜单树中剔除平台级权限节点，以及被剔空后剩下的空壳父节点。
     * <p>
     * 空壳判定只依据<b>结构</b>（原有子节点是否已全部被剔除），不依据"有没有可行使的后代"，
     * 因此级联是可控的：只有"整棵子树都不可行使"的分支才会被删掉。
     */
    public static List<MenuEntity> omitPlatformOnlyMenus(List<MenuEntity> all) {
        Set<Long> omitted = platformOnlyMenuIds(all);
        return all.stream().filter(menu -> !omitted.contains(menu.getId())).toList();
    }

    /**
     * 需要从授权树中剔除的菜单 id。两条规则：
     * <ol>
     *   <li>挂着平台级权限码的节点（按钮）—— 永不保留；</li>
     *   <li>把它们剔完之后，<b>子节点全部消失的空壳父节点</b> —— 一并剔除。</li>
     * </ol>
     * <p>
     * 第 2 条是必须的：像"菜单管理"这种页面，它本身没有权限码（权限码在它下属的按钮上），
     * 所以只按第 1 条过滤的话，它会变成一个"没有任何复选框、但页面节点仍然可勾选"的节点 ——
     * 勾上之后又是"侧边栏出现入口、点进去全是 403"，问题原封不动。
     * <p>
     * <b>判定空壳只用结构关系，绝不能引入"有没有可行使的后代"这种递归条件。</b>
     * 之前正是那样写成"该节点是否还有可行使的后代"，结果一个"纯页面层级、下面没有任何按钮"
     * 的分支（例如只配了页面、还没配按钮的日志模块）会被判为空壳，再自底向上层层级联，
     * 最终把整棵子树删空 —— 授权树会凭空空掉一大块。现在的规则不会级联：
     * 只删"原本有子节点、且这些子节点全部被剔除"的那一层。
     */
    public static Set<Long> platformOnlyMenuIds(List<MenuEntity> all) {
        Set<Long> omitted = new HashSet<>();
        for (MenuEntity menu : all) {
            if (isPlatformOnly(menu)) omitted.add(menu.getId());
        }
        Map<Long, List<MenuEntity>> byParent = all.stream().collect(Collectors.groupingBy(MenuEntity::getParentId));
        Map<Long, MenuEntity> byId = all.stream().collect(Collectors.toMap(MenuEntity::getId, menu -> menu, (first, second) -> first));
        Map<Long, Integer> depthCache = new HashMap<>();
        // 按深度从深到浅处理：父节点必须在其子节点判定完之后才能得出结论，
        // 否则"孙辈被剔除 ⇒ 子辈空壳 ⇒ 父辈空壳"这条链只会走一层。
        List<MenuEntity> deepestFirst = all.stream()
                .sorted(Comparator.comparingInt((MenuEntity menu) -> depthOf(menu, byId, depthCache)).reversed())
                .toList();
        for (MenuEntity menu : deepestFirst) {
            if (omitted.contains(menu.getId())) continue;
            List<MenuEntity> children = byParent.get(menu.getId());
            // 没有子节点 ⇒ 叶子，一律保留（首页、纯页面菜单等）。
            if (children == null || children.isEmpty()) continue;
            if (children.stream().allMatch(child -> omitted.contains(child.getId()))) omitted.add(menu.getId());
        }
        return omitted;
    }

    /**
     * 菜单深度（根为 0），沿 parentId 上溯，结果缓存。
     * 遇到环或悬空父节点时截断，避免脏数据把这里变成死循环。
     */
    private static int depthOf(MenuEntity menu, Map<Long, MenuEntity> byId, Map<Long, Integer> cache) {
        Integer cached = cache.get(menu.getId());
        if (cached != null) return cached;
        int depth = 0;
        Set<Long> visited = new HashSet<>();
        visited.add(menu.getId());
        Long parentId = menu.getParentId();
        while (parentId != null && parentId > 0) {
            MenuEntity parent = byId.get(parentId);
            if (parent == null || !visited.add(parentId)) break;
            depth++;
            parentId = parent.getParentId();
        }
        cache.put(menu.getId(), depth);
        return depth;
    }

    /** 该节点是否挂着平台级权限码（只有按钮会带权限码）。 */
    private static boolean isPlatformOnly(MenuEntity menu) {
        String code = menu.getPermissionCode();
        return code != null && PLATFORM_ONLY_PERMISSION_CODES.contains(code);
    }

    /**
     * 拒绝把平台级权限授给任何角色（含超管自己创建的角色）。
     * <p>
     * 既然这些权限无论授给谁都无法行使，授下去只会产生"看得见的死入口"和一堆无效的
     * {@code sys_role_menu} 记录，因此直接在写入侧拦住，而不是等用户点进去撞 403。
     * 这是 {@link #omitPlatformOnlyMenus} 的纵深防御：即使有人绕过界面直接调接口，也授不出去。
     */
    public static void requireNotPlatformOnly(List<Long> menuIds, Set<Long> platformOnlyIds) {
        if (menuIds == null || platformOnlyIds == null || platformOnlyIds.isEmpty()) return;
        boolean blocked = menuIds.stream().anyMatch(platformOnlyIds::contains);
        if (blocked) {
            throw new BusinessException("菜单定义与重置密码属于平台级权限，仅超级管理员可行使，不能授予任何角色");
        }
    }

    /** 菜单定义会改变全局路由与 API 权限码，因此只能由平台级管理员操作。 */
    public void requireSuperAdmin(Actor actor) {
        if (!actor.superAdmin()) throw new AccessDeniedException("仅超级管理员可维护全局菜单配置");
    }

    public boolean canManageDept(Actor actor, DeptEntity dept) {
        return actor.all() || actor.departments().contains(dept.getId());
    }

    public boolean canCreateChildDept(Actor actor, DeptEntity parent) {
        return actor.all() || actor.scope().branches().contains(parent.getId());
    }

    /**
     * 能否新增<b>顶级部门</b>（即 {@code parentId <= 0}）。
     * <p>
     * 只有 {@code ALL} 数据范围的账号可以：其它范围的账号建立的顶级部门一定落在自己可见范围之外，
     * 建完就看不见、也管不了。前端需要用这个判定决定"上级部门"能不能清空
     * （清空 = 建顶级部门），因此它必须与 {@link #requireCreateDept} 的判断完全同源，
     * 否则会出现"表单允许提交、后端一律 403"。
     */
    public boolean canCreateRootDept(Actor actor) {
        return actor.all();
    }

    public void requireCreateDept(Actor actor, Long parentId) {
        if (parentId == null || parentId <= 0) {
            if (!canCreateRootDept(actor)) throw new AccessDeniedException("只有全部数据范围的账号可以新增顶级部门");
            return;
        }
        if (actor.all()) return;
        if (!actor.scope().branches().contains(parentId)) {
            throw new AccessDeniedException("只能在本部门及下级部门范围内新增子部门");
        }
    }

    public void requireManageDept(Actor actor, DeptEntity dept) {
        if (!canManageDept(actor, dept)) throw new AccessDeniedException("不能管理数据范围之外的部门");
    }

    public void requireMoveDept(Actor actor, Long originalParentId, Long newParentId) {
        if (actor.all() || originalParentId.equals(newParentId)) return;
        if (newParentId == null || newParentId <= 0 || !actor.scope().branches().contains(newParentId)) {
            throw new AccessDeniedException("不能将部门移动到数据范围之外");
        }
    }

    private boolean canSeeUser(Actor actor, UserEntity target) {
        return actor.all() || target.getDeptId() != null && actor.departments().contains(target.getDeptId())
                || actor.self() && target.getId().equals(actor.user().getId());
    }

    private Set<String> permissions(Long userId) {
        Set<String> values = new HashSet<>();
        menus.selectByUserId(userId).stream().map(MenuEntity::getPermissionCode)
                .filter(code -> code != null && !code.isBlank()).forEach(values::add);
        return values;
    }

    /**
     * 判断一个角色是否让持有人成为超级管理员。全项目只应有这一份判定口径。
     * <p>
     * 关键点是 <b>status 必须为 1</b>：停用的角色不授予任何权限（menu_tree/权限集合都按
     * {@code r.status = 1} 过滤），因此停用的 super_admin 角色绝不能让一个人仍被当成超管。
     * 以前只有 {@link #actor()} 带了 status 判断，{@link #canManageUser}、UserService 与
     * /auth/me 都没有，于是把 super_admin 角色停用后会出现"后端认为他不是超管、前端却显示
     * 他是超管"的两套口径 —— 这类不一致本身不直接提权，但会让界面与真实权限长期对不上，
     * 是排查权限问题时最耗时间的一类 bug。
     */
    public static boolean isSuperAdmin(RoleEntity role) {
        return role != null && "super_admin".equals(role.getRoleCode()) && Integer.valueOf(1).equals(role.getStatus());
    }

    /** 一组已分配角色中是否存在生效的超级管理员角色。 */
    public static boolean containsSuperAdmin(List<RoleEntity> assignedRoles) {
        return assignedRoles != null && assignedRoles.stream().anyMatch(AccessPolicy::isSuperAdmin);
    }

    private Set<String> rolePermissions(Long roleId) {
        return new HashSet<>(roleMenus.selectPermissionCodes(roleId));
    }

    /** branches 仅来源于动态子树授权，CUSTOM 精确集合不会获得组织结构扩展能力。 */
    public record Scope(boolean all, boolean self, Set<Long> departments, Set<Long> branches) {
        public static Scope empty() { return new Scope(false, false, Set.of(), Set.of()); }
        public static Scope unrestricted() { return new Scope(true, false, Set.of(), Set.of()); }
        public Scope union(Scope other) {
            if (all || other.all) return unrestricted();
            Set<Long> ids = new HashSet<>(departments); ids.addAll(other.departments);
            Set<Long> roots = new HashSet<>(branches); roots.addAll(other.branches);
            return new Scope(false, self || other.self, Set.copyOf(ids), Set.copyOf(roots));
        }
        public boolean contains(Scope other, UserEntity actor, UserEntity target) {
            if (all) return true;
            if (other.all || !departments.containsAll(other.departments)) return false;
            return !other.self || target.getDeptId() != null && departments.contains(target.getDeptId())
                    || self && java.util.Objects.equals(actor.getId(), target.getId());
        }
    }

    public record ScopeContext(Map<Long, DeptEntity> departments, Map<Long, Set<Long>> customDepartments,
                               Map<Long, Set<String>> rolePermissions) { }

    public record Actor(UserEntity user, boolean superAdmin, Set<String> permissions, Scope scope,
                        Map<String, Scope> operationScopes, ScopeContext context) {
        public boolean all() { return scope.all(); }
        public boolean self() { return scope.self(); }
        public Set<Long> departments() { return scope.departments(); }
    }
}
