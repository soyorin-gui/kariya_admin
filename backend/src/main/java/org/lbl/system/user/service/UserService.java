package org.lbl.system.user.service;

import static org.lbl.common.util.TextValues.trimToNull;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.lbl.auth.session.SessionService;
import org.lbl.auth.service.PasswordRules;
import org.lbl.auth.identity.LocalCredentialEntity;
import org.lbl.auth.identity.LocalCredentialMapper;
import org.lbl.auth.identity.ExternalIdentityMapper;
import org.lbl.common.exception.BusinessException;
import org.lbl.common.result.PageResult;
import org.lbl.security.context.AccessPolicy;
import org.lbl.system.dept.entity.DeptEntity;
import org.lbl.system.dept.mapper.DeptMapper;
import org.lbl.system.menu.mapper.MenuMapper;
import org.lbl.system.role.entity.RoleEntity;
import org.lbl.system.role.mapper.RoleMapper;
import org.lbl.system.role.mapper.RoleMenuMapper;
import org.lbl.system.user.entity.UserEntity;
import org.lbl.system.user.mapper.UserMapper;
import org.lbl.system.user.mapper.UserRoleMapper;
import org.lbl.system.user.request.UserRequest;
import org.lbl.system.user.request.UserCreateRequest;
import org.lbl.system.user.request.ContactUpdateRequest;
import org.lbl.system.user.vo.ContactProfile;
import org.lbl.system.user.vo.UserFormOptions;
import org.lbl.system.user.vo.UserVO;
import org.lbl.system.user.vo.UserListVO;
import org.lbl.system.user.vo.UserRoleAssignment;
import org.lbl.system.user.vo.UserPermissionAssignment;
import org.lbl.system.user.vo.UsernameAvailability;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserService {
    private final UserMapper mapper;
    private final UserRoleMapper userRoles;
    private final DeptMapper depts;
    private final RoleMapper roles;
    private final RoleMenuMapper roleMenus;
    private final MenuMapper allMenus;
    private final PasswordEncoder passwords;
    private final SessionService sessions;
    private final AccessPolicy access;
    private final LocalCredentialMapper credentials;
    private final ExternalIdentityMapper externalIdentities;

    public UserService(UserMapper mapper, UserRoleMapper userRoles, DeptMapper depts, RoleMapper roles, RoleMenuMapper roleMenus,
                       MenuMapper allMenus, PasswordEncoder passwords, SessionService sessions, AccessPolicy access,
                       LocalCredentialMapper credentials, ExternalIdentityMapper externalIdentities) {
        this.mapper = mapper;
        this.userRoles = userRoles;
        this.depts = depts;
        this.roles = roles;
        this.roleMenus = roleMenus;
        this.allMenus = allMenus;
        this.passwords = passwords;
        this.sessions = sessions;
        this.access = access;
        this.credentials = credentials;
        this.externalIdentities = externalIdentities;
    }

    public PageResult<UserListVO> page(long page, long size, String keyword) {
        if (page < 1 || size < 1 || size > 100) throw new BusinessException("分页参数无效，每页最多 100 条");
        AccessPolicy.Actor actor = access.actor("system:user:list");
        LambdaQueryWrapper<UserEntity> query = query(keyword);
        access.applyUserScope(query, actor);
        Page<UserEntity> result = mapper.selectPage(Page.of(page, size), query.orderByDesc(UserEntity::getCreatedTime).orderByDesc(UserEntity::getId));
        return new PageResult<>(toListViews(result.getRecords(), actor), result.getTotal(), page, size);
    }

    /** 编辑弹窗按需加载最新详情；列表不再携带 roleIds、deptId 等编辑态字段。 */
    public UserVO detail(Long id) {
        AccessPolicy.Actor actor = access.actor("system:user:update");
        UserEntity user = require(id);
        List<RoleEntity> assigned = roles.selectAssignedByUserId(id);
        List<RoleEntity> active = assigned.stream().filter(role -> role.getStatus() == 1).toList();
        Set<String> targetPermissions = actor.superAdmin() ? Set.of() : allMenus.selectByUserId(id).stream()
                .map(org.lbl.system.menu.entity.MenuEntity::getPermissionCode).filter(Objects::nonNull)
                .filter(code -> !code.isBlank()).collect(Collectors.toSet());
        boolean superAdminTarget = AccessPolicy.containsSuperAdmin(active);
        access.prepareRoles(actor, active);
        boolean manageable = access.canManageUser(actor, user, superAdminTarget, targetPermissions,
                active);
        if (!manageable) throw new BusinessException("不能管理同级或更高权限的用户");
        DeptEntity department = user.getDeptId() == null ? null : depts.selectById(user.getDeptId());
        return new UserVO(user.getId(), user.getUsername(), user.getRealName(), user.getPhone(), user.getEmail(), user.getDeptId(),
                department == null ? "-" : department.getDeptName(), active.stream().map(RoleEntity::getId).toList(),
                active.stream().map(RoleEntity::getRoleName).collect(Collectors.joining("、")), user.getStatus(),
                user.getCreatedTime(), true, user.getBuiltin() != 1, actor.superAdmin() && !superAdminTarget);
    }

    /**
     * 新增/编辑用户表单的候选数据（部门、角色）。
     * <p>
     * 原实现直接写在 Controller 里、并用注入的 DeptMapper / RoleMapper 当场查询，
     * 属于"Controller 越过 Service 直连持久层"。这里的筛选本身是实打实的授权规则
     * （部门按数据范围收窄、角色按可分配性收窄），必须和 UserService.create/update
     * 的校验口径放在一起，否则两边迟早会漂移：表单显示了一个候选、提交时却被拒，
     * 或者反过来显示不全、让人以为权限不够。
     */
    public UserFormOptions formOptions(String operation) {
        if (!Set.of("add", "update").contains(operation)) throw new BusinessException("表单操作无效");
        AccessPolicy.Actor actor = access.actor("system:user:" + operation);
        List<UserFormOptions.DepartmentOption> departments = depts.selectList(new LambdaQueryWrapper<DeptEntity>()
                        .eq(DeptEntity::getStatus, 1)
                        .orderByAsc(DeptEntity::getSortOrder)).stream()
                .filter(value -> actor.superAdmin() || actor.all() || actor.departments().contains(value.getId()))
                .map(value -> new UserFormOptions.DepartmentOption(value.getId(), value.getDeptName(), value.getParentId()))
                .toList();
        List<RoleEntity> candidates = roles.selectList(new LambdaQueryWrapper<RoleEntity>().eq(RoleEntity::getStatus, 1));
        access.prepareRoles(actor, candidates);
        Map<Long, Set<String>> permissionsByRole = actor.superAdmin() ? Map.of()
                : rolePermissions(candidates.stream().map(RoleEntity::getId).toList());
        List<UserFormOptions.Option> assignableRoles = candidates.stream()
                .filter(value -> access.canAssignRole(actor, value, permissionsByRole.getOrDefault(value.getId(), Set.of())))
                .map(value -> new UserFormOptions.Option(value.getId(), value.getRoleName()))
                .toList();
        return new UserFormOptions(departments, assignableRoles);
    }

    public ContactProfile currentContactProfile() {
        UserEntity user = access.actor().user();
        return new ContactProfile(user.getUsername(), user.getRealName(), user.getPhone(), user.getEmail());
    }

    @Transactional
    public ContactProfile updateOwnContact(ContactUpdateRequest request) {
        UserEntity user = access.actor().user();
        user.setPhone(trimToNull(request.phone()));
        user.setEmail(trimToNull(request.email()));
        mapper.updateById(user);
        return new ContactProfile(user.getUsername(), user.getRealName(), user.getPhone(), user.getEmail());
    }

    @Transactional
    public UserVO create(UserCreateRequest createRequest) {
        PasswordRules.requireConfirmed(createRequest.password(), createRequest.confirmPassword());
        UserRequest request = createRequest.userRequest();
        AccessPolicy.Actor actor = access.actor("system:user:add");
        validateAssignment(actor, request, null);
        requireUsernameAvailable(request.username().trim());
        UserEntity user = new UserEntity();
        user.setUsername(request.username().trim());
        user.setRealName(request.realName());
        user.setPhone(request.phone());
        user.setEmail(request.email());
        user.setDeptId(request.deptId());
        user.setEmployeeNoVerified(0);
        user.setRegistrationSource("ADMIN");
        user.setStatus(request.status());
        user.setAuthVersion(1L);
        user.setBuiltin(0);
        mapper.insert(user);
        saveCredential(user.getId(), createRequest.password(), 0);
        replaceRoles(user.getId(), request.roleIds());
        return toView(user, actor);
    }

    @Transactional
    public UserVO update(Long id, UserRequest request) {
        AccessPolicy.Actor actor = access.actor("system:user:update");
        UserEntity user = require(id);
        access.requireManageUser(actor, user);
        validateAssignment(actor, request, user);
        if (user.getBuiltin() == 1 && request.status() != 1) throw new BusinessException("内置管理员不能禁用");
        // 注意这里刻意 <b>不</b> 复用 AccessPolicy.isSuperAdmin：那个方法还要求角色 status=1，
        // 而这一条问的是"内置管理员的角色分配里是否仍然保留着 super_admin 这个角色"。
        // 若带上状态判断，就会变成"super_admin 角色一旦被停用，连管理员改用户资料都做不了"，
        // 与"必须保留该角色"的本意正好相反。
        if (user.getBuiltin() == 1 && request.roleIds().stream().map(roles::selectById)
                .noneMatch(role -> role != null && "super_admin".equals(role.getRoleCode()))) {
            throw new BusinessException("内置管理员必须保留超级管理员角色");
        }
        user.setRealName(request.realName());
        user.setPhone(request.phone());
        user.setEmail(request.email());
        user.setDeptId(request.deptId());
        user.setStatus(request.status());
        // basic_role 是自助开户账号的最低权限角色，由开户流程维护。
        // 管理员在此只是在其上叠加业务角色，不能因为编辑表单整组覆盖而
        // 意外抹掉基础身份；这也让无权分配内置角色的普通管理员能够正常编辑用户。
        List<Long> effectiveRoleIds = new ArrayList<>(request.roleIds());
        roles.selectAssignedByUserId(id).stream()
                .filter(role -> "basic_role".equals(role.getRoleCode()))
                .map(RoleEntity::getId)
                .filter(roleId -> !effectiveRoleIds.contains(roleId))
                .forEach(effectiveRoleIds::add);
        replaceRoles(id, effectiveRoleIds);
        user.setAuthVersion(user.getAuthVersion() + 1);
        mapper.updateById(user);
        sessions.removeAll(id);
        return toView(user, actor);
    }

    @Transactional
    public void remove(Long id) {
        AccessPolicy.Actor actor = access.actor("system:user:delete");
        UserEntity user = require(id);
        access.requireManageUser(actor, user);
        if (user.getBuiltin() == 1) throw new BusinessException("内置用户不能删除");
        user.setDeletedTime(LocalDateTime.now());
        mapper.updateById(user);
        mapper.deleteById(id);
        userRoles.deleteByUserId(id);
        credentials.deleteById(id);
        externalIdentities.deleteByUserId(id);
        sessions.removeAll(id);
    }

    private LambdaQueryWrapper<UserEntity> query(String keyword) {
        return new LambdaQueryWrapper<UserEntity>().and(keyword != null && !keyword.isBlank(), condition -> condition.like(UserEntity::getUsername, keyword).or().like(UserEntity::getRealName, keyword));
    }

    /**
     * 用户名可用性校验，供新增表单实时提示。
     * <p>
     * 分两步、且第二步查的是"包含逻辑删除"的口径，原因是两者对应两种不同的失败：
     * 前者会被业务规则挡住，后者会在数据库唯一索引上被挡住。如果只查前者，
     * 用户会看到"可用"，点提交后却收到一个 500，这是最难排查的一类体验问题。
     */
    public UsernameAvailability checkUsername(String username) {
        String value = username == null ? "" : username.trim();
        if (value.isEmpty()) throw new BusinessException("请输入用户名");
        if (mapper.selectCount(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername, value)) > 0) {
            return new UsernameAvailability(false, "该用户名已被使用");
        }
        if (mapper.countIncludingDeletedByUsername(value) > 0) {
            return new UsernameAvailability(false, "该用户名曾被使用过，历史记录仍占用唯一约束，请更换");
        }
        return new UsernameAvailability(true, null);
    }

    /** 与数据库唯一索引口径一致的重复校验；不通过时直接给出可展示的业务提示。 */
    private void requireUsernameAvailable(String username) {
        UsernameAvailability availability = checkUsername(username);
        if (!availability.available()) throw new BusinessException(availability.message());
    }

    /**
     * 校验用户的部门与角色分配。
     * <p>
     * 停用部门不再接收新用户，但已经在其中的用户仍可修改资料，也可以迁往启用部门。
     * 否则部门一旦停用，连禁用其中的用户账号这种必要操作也无法执行。
     *
     * @param current 编辑时的原用户；新增时传 {@code null}
     */
    private void validateAssignment(AccessPolicy.Actor actor, UserRequest request, UserEntity current) {
        // 这里刻意分成三句话，而不是原来那句"部门不存在或已停用"。
        // 管理员能看到的信息只有这一句，混在一起就会把人引向错误方向：
        // 自助注册创建的账号本来就没有部门，但提示会说它"不存在或已停用"。
        // （deptId 为 null 的请求由 UserRequest 上的 @NotNull 挡在前面，到不了这里。）
        DeptEntity department = depts.selectById(request.deptId());
        if (department == null) {
            throw new BusinessException("所选部门不存在（可能已被删除），请重新选择");
        }
        boolean staysInCurrentDept = current != null && Objects.equals(current.getDeptId(), request.deptId());
        if (department.getStatus() != 1 && !staysInCurrentDept) {
            throw new BusinessException("所选部门已停用，不能将用户迁入该部门；原本就在该部门的用户不受影响");
        }
        if (!actor.superAdmin() && !actor.departments().contains(request.deptId()) && !actor.all()) {
            throw new BusinessException("不能将用户分配到数据范围之外的部门");
        }
        if (request.status() != 0 && request.status() != 1) throw new BusinessException("用户状态无效");
        // 平台级权限的 id 集合：只在与角色相关时才需要，因此这里按需查询一次。
        Set<Long> platformOnlyIds = null;
        List<RoleEntity> assigned = new ArrayList<>();
        for (Long roleId : request.roleIds()) {
            RoleEntity role = roleId == null ? null : roles.selectById(roleId);
            if (role == null || !access.canAssignRole(actor, role)) throw new BusinessException("包含无权分配或已停用的角色");
            if (platformOnlyIds == null) platformOnlyIds = AccessPolicy.platformOnlyMenuIds(allMenus.selectList(new LambdaQueryWrapper<>()));
            // 纵深防御：正常路径下 grantMenus 已经把平台级权限挡在 sys_role_menu 之外，
            // 所以这里只对"历史遗留数据 / 有人直接改库"生效。仍然拦一道，避免把这类角色
            // 分配出去，从而造出"侧边栏有入口、点进去全是 403"的账号。
            AccessPolicy.requireNotPlatformOnly(roleMenus.selectMenuIds(role.getId()), platformOnlyIds);
            assigned.add(role);
        }
        UserEntity target = new UserEntity();
        target.setId(current == null ? null : current.getId());
        target.setDeptId(request.deptId());
        if (current != null) roles.selectAssignedByUserId(current.getId()).stream()
                .filter(role -> "basic_role".equals(role.getRoleCode()) && role.getStatus() == 1)
                .filter(role -> assigned.stream().noneMatch(value -> value.getId().equals(role.getId())))
                .forEach(assigned::add);
        access.requireAssignableRoles(actor, assigned, target);
    }

    private void replaceRoles(Long userId, List<Long> roleIds) { userRoles.deleteByUserId(userId); roleIds.stream().distinct().forEach(roleId -> userRoles.insert(userId, roleId)); }
    private void saveCredential(Long userId, String rawPassword, int passwordChangeRequired) {
        LocalCredentialEntity credential = credentials.selectById(userId);
        if (credential == null) {
            credential = new LocalCredentialEntity();
            credential.setUserId(userId);
            credential.setEnabled(1);
            credential.setPasswordHash(passwords.encode(rawPassword));
            credential.setPasswordChangeRequired(passwordChangeRequired);
            credential.setPasswordChangedTime(LocalDateTime.now());
            credentials.insert(credential);
        } else {
            credential.setEnabled(1);
            credential.setPasswordHash(passwords.encode(rawPassword));
            credential.setPasswordChangeRequired(passwordChangeRequired);
            credential.setPasswordChangedTime(LocalDateTime.now());
            credentials.updateById(credential);
        }
    }
    private UserEntity require(Long id) { UserEntity user = mapper.selectById(id); if (user == null) throw new BusinessException("用户不存在"); return user; }
    private UserVO toView(UserEntity user, AccessPolicy.Actor actor) {
        List<RoleEntity> assignedRoles = roles.selectByUserId(user.getId());
        DeptEntity department = user.getDeptId() == null ? null : depts.selectById(user.getDeptId());
        boolean manageable = access.canManageUser(actor, user);
        // 分配到的角色里有"停用的 super_admin"不算超管目标（停用角色不授予权限），
        // 与 AccessPolicy.isSuperAdmin 同一口径。注意 assignedRoles 用的是 selectByUserId
        // （只含启用角色），所以这里必须另查一次"含停用"的分配记录，否则停用的 super_admin
        // 会被漏判成"可重置密码的普通用户"。
        boolean superAdminTarget = AccessPolicy.containsSuperAdmin(roles.selectAssignedByUserId(user.getId()));
        return new UserVO(user.getId(), user.getUsername(), user.getRealName(), user.getPhone(), user.getEmail(), user.getDeptId(),
                department == null ? "-" : department.getDeptName(), assignedRoles.stream().map(RoleEntity::getId).toList(),
                assignedRoles.stream().map(RoleEntity::getRoleName).collect(Collectors.joining("、")), user.getStatus(),
                user.getCreatedTime(), manageable, manageable && user.getBuiltin() != 1,
                manageable && actor.superAdmin() && !superAdminTarget);
    }

    private List<UserListVO> toListViews(List<UserEntity> users, AccessPolicy.Actor actor) {
        if (users.isEmpty()) return List.of();
        List<Long> userIds = users.stream().map(UserEntity::getId).toList();
        Map<Long, List<UserRoleAssignment>> rolesByUser = roles.selectAssignedByUserIds(userIds).stream()
                .collect(Collectors.groupingBy(UserRoleAssignment::getUserId));
        Set<Long> deptIds = users.stream().map(UserEntity::getDeptId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> departmentNames = deptIds.isEmpty() ? Map.of() : depts.selectBatchIds(deptIds).stream()
                .collect(Collectors.toMap(DeptEntity::getId, DeptEntity::getDeptName));
        Map<Long, RoleEntity> roleEntities = new HashMap<>();
        rolesByUser.values().stream().flatMap(List::stream).forEach(value -> {
            RoleEntity role = new RoleEntity();
            role.setId(value.getRoleId()); role.setRoleCode(value.getRoleCode()); role.setDataScope(value.getDataScope());
            role.setStatus(value.getStatus()); role.setBuiltin(value.getBuiltin());
            roleEntities.put(role.getId(), role);
        });
        access.prepareRoles(actor, List.copyOf(roleEntities.values()));
        AccessPolicy.Actor updateActor = access.forPermissions(actor, "system:user:update");
        AccessPolicy.Actor deleteActor = access.forPermissions(actor, "system:user:delete");
        Map<Long, Set<String>> permissionsByUser = new HashMap<>();
        if (!actor.superAdmin()) {
            for (UserPermissionAssignment permission : allMenus.selectPermissionCodesByUserIds(userIds)) {
                permissionsByUser.computeIfAbsent(permission.getUserId(), ignored -> new java.util.HashSet<>()).add(permission.getPermissionCode());
            }
        }
        return users.stream().map(user -> {
            List<UserRoleAssignment> assigned = rolesByUser.getOrDefault(user.getId(), List.of());
            List<UserRoleAssignment> active = assigned.stream().filter(role -> role.getStatus() == 1).toList();
            boolean superAdminTarget = active.stream().anyMatch(role -> "super_admin".equals(role.getRoleCode()));
            List<RoleEntity> targetRoles = active.stream().map(value -> roleEntities.get(value.getRoleId())).toList();
            Set<String> targetPermissions = permissionsByUser.getOrDefault(user.getId(), Set.of());
            boolean manageable = access.canManageUser(updateActor, user, superAdminTarget, targetPermissions, targetRoles);
            boolean deletable = access.canManageUser(deleteActor, user, superAdminTarget, targetPermissions, targetRoles);
            return new UserListVO(user.getId(), user.getUsername(), user.getRealName(), user.getPhone(),
                    user.getDeptId() == null ? "-" : departmentNames.getOrDefault(user.getDeptId(), "-"),
                    active.stream().map(UserRoleAssignment::getRoleName).collect(Collectors.joining("、")), user.getStatus(),
                    user.getCreatedTime(), manageable, deletable && user.getBuiltin() != 1,
                    actor.superAdmin() && !superAdminTarget);
        }).toList();
    }

    private Map<Long, Set<String>> rolePermissions(List<Long> roleIds) {
        if (roleIds.isEmpty()) return Map.of();
        Map<Long, Set<String>> result = new HashMap<>();
        roleMenus.selectPermissionCodesByRoleIds(roleIds).forEach(row ->
                result.computeIfAbsent(row.getRoleId(), ignored -> new java.util.HashSet<>()).add(row.getPermissionCode()));
        return result;
    }

}
