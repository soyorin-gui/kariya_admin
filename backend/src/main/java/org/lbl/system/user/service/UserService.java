package org.lbl.system.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletResponse;
import org.lbl.auth.session.SessionService;
import org.lbl.auth.session.SessionView;
import org.lbl.auth.service.PasswordRules;
import org.lbl.auth.identity.LocalCredentialEntity;
import org.lbl.auth.identity.LocalCredentialMapper;
import org.lbl.auth.identity.ExternalIdentityMapper;
import org.lbl.common.exception.BusinessException;
import org.lbl.common.exception.TooManyRequestsException;
import org.lbl.common.result.PageResult;
import org.lbl.common.util.ExcelExportColumn;
import org.lbl.common.util.ExcelExportUtil;
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
import org.lbl.system.user.request.PasswordChangeRequest;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserService {
    /** 15 分钟窗口内允许的错误"原密码"尝试次数，超过即拒绝并要求等待。 */
    private static final int PASSWORD_CHANGE_MAX_FAILURES = 5;
    private static final Duration PASSWORD_CHANGE_WINDOW = Duration.ofMinutes(15);
    /** 单批最多留在内存中的导出记录数；Excel 单元格由 SXSSF 继续流式写出。 */
    private static final long EXPORT_BATCH_SIZE = 500;

    private final UserMapper mapper;
    private final UserRoleMapper userRoles;
    private final DeptMapper depts;
    private final RoleMapper roles;
    private final RoleMenuMapper roleMenus;
    private final MenuMapper allMenus;
    private final PasswordEncoder passwords;
    private final SessionService sessions;
    private final AccessPolicy access;
    private final StringRedisTemplate redis;
    private final SecureRandom random = new SecureRandom();
    private final LocalCredentialMapper credentials;
    private final ExternalIdentityMapper externalIdentities;

    public UserService(UserMapper mapper, UserRoleMapper userRoles, DeptMapper depts, RoleMapper roles, RoleMenuMapper roleMenus,
                       MenuMapper allMenus, PasswordEncoder passwords, SessionService sessions, AccessPolicy access,
                       StringRedisTemplate redis, LocalCredentialMapper credentials, ExternalIdentityMapper externalIdentities) {
        this.mapper = mapper;
        this.userRoles = userRoles;
        this.depts = depts;
        this.roles = roles;
        this.roleMenus = roleMenus;
        this.allMenus = allMenus;
        this.passwords = passwords;
        this.sessions = sessions;
        this.access = access;
        this.redis = redis;
        this.credentials = credentials;
        this.externalIdentities = externalIdentities;
    }

    public PageResult<UserListVO> page(long page, long size, String keyword) {
        if (page < 1 || size < 1 || size > 100) throw new BusinessException("分页参数无效，每页最多 100 条");
        AccessPolicy.Actor actor = access.actor();
        LambdaQueryWrapper<UserEntity> query = query(keyword);
        access.applyUserScope(query, actor);
        Page<UserEntity> result = mapper.selectPage(Page.of(page, size), query.orderByDesc(UserEntity::getCreatedTime).orderByDesc(UserEntity::getId));
        return new PageResult<>(toListViews(result.getRecords(), actor), result.getTotal(), page, size);
    }

    /** 编辑弹窗按需加载最新详情；列表不再携带 roleIds、deptId 等编辑态字段。 */
    public UserVO detail(Long id) {
        AccessPolicy.Actor actor = access.actor();
        UserEntity user = require(id);
        List<RoleEntity> assigned = roles.selectAssignedByUserId(id);
        List<RoleEntity> active = assigned.stream().filter(role -> role.getStatus() == 1).toList();
        Set<String> targetPermissions = actor.superAdmin() ? Set.of() : allMenus.selectByUserId(id).stream()
                .map(org.lbl.system.menu.entity.MenuEntity::getPermissionCode).filter(Objects::nonNull)
                .filter(code -> !code.isBlank()).collect(Collectors.toSet());
        boolean superAdminTarget = AccessPolicy.containsSuperAdmin(active);
        boolean manageable = access.canManageUser(actor, user, superAdminTarget, targetPermissions,
                active.stream().map(RoleEntity::getDataScope).toList());
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
    public UserFormOptions formOptions() {
        AccessPolicy.Actor actor = access.actor();
        List<UserFormOptions.DepartmentOption> departments = depts.selectList(new LambdaQueryWrapper<DeptEntity>()
                        .eq(DeptEntity::getStatus, 1)
                        .orderByAsc(DeptEntity::getSortOrder)).stream()
                .filter(value -> actor.superAdmin() || actor.all() || actor.departments().contains(value.getId()))
                .map(value -> new UserFormOptions.DepartmentOption(value.getId(), value.getDeptName(), value.getParentId()))
                .toList();
        List<RoleEntity> candidates = roles.selectList(new LambdaQueryWrapper<RoleEntity>().eq(RoleEntity::getStatus, 1));
        Map<Long, Set<String>> permissionsByRole = actor.superAdmin() ? Map.of()
                : rolePermissions(candidates.stream().map(RoleEntity::getId).toList());
        List<UserFormOptions.Option> assignableRoles = candidates.stream()
                .filter(value -> access.canAssignRole(actor, value, permissionsByRole.getOrDefault(value.getId(), Set.of())))
                .map(value -> new UserFormOptions.Option(value.getId(), value.getRoleName()))
                .toList();
        return new UserFormOptions(departments, assignableRoles);
    }

    public List<SessionView> managedSessions(Long userId, String currentSid) {
        UserEntity target = require(userId);
        access.requireManageUser(access.actor(), target);
        return sessions.listMemberSessions(userId, currentSid);
    }

    public void removeManagedSession(Long userId, String reference) {
        UserEntity target = require(userId);
        access.requireManageUser(access.actor(), target);
        if (!sessions.removeByReference(userId, reference)) throw new BusinessException("登录会话不存在或已失效");
    }

    public void removeAllManagedSessions(Long userId) {
        UserEntity target = require(userId);
        access.requireManageUser(access.actor(), target);
        sessions.removeAll(userId);
    }

    public ContactProfile currentContactProfile() {
        UserEntity user = access.actor().user();
        return new ContactProfile(user.getUsername(), user.getRealName(), user.getPhone(), user.getEmail());
    }

    @Transactional
    public ContactProfile updateOwnContact(ContactUpdateRequest request) {
        UserEntity user = access.actor().user();
        user.setPhone(blankToNull(request.phone()));
        user.setEmail(blankToNull(request.email()));
        mapper.updateById(user);
        return new ContactProfile(user.getUsername(), user.getRealName(), user.getPhone(), user.getEmail());
    }

    @Transactional
    public UserVO create(UserCreateRequest createRequest) {
        PasswordRules.requireConfirmed(createRequest.password(), createRequest.confirmPassword());
        UserRequest request = createRequest.userRequest();
        AccessPolicy.Actor actor = access.actor();
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
        AccessPolicy.Actor actor = access.actor();
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
        AccessPolicy.Actor actor = access.actor();
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

    @Transactional
    public String resetPassword(Long id) {
        AccessPolicy.Actor actor = access.actor();
        UserEntity user = require(id);
        access.requireManageUser(actor, user);
        if (!actor.superAdmin() || AccessPolicy.containsSuperAdmin(roles.selectAssignedByUserId(id))) {
            throw new BusinessException("仅超级管理员可重置普通用户密码");
        }
        String temporaryPassword = temporaryPassword();
        saveCredential(user.getId(), temporaryPassword, 1);
        user.setAuthVersion(user.getAuthVersion() + 1);
        mapper.updateById(user);
        sessions.removeAll(id);
        return temporaryPassword;
    }

    /**
     * 修改本人密码。
     * <p>
     * 增加了对"原密码错误"的次数限制。原因：这个接口只需要会话、不需要任何权限码
     * （首登用户 authorities 为空也必须能改密码），于是它天然是一个"带身份的原密码爆破点"——
     * 拿到一个已登录会话（例如借用他人未锁屏的电脑）后，可以无限次猜原密码，
     * 而猜中就能把账号彻底接管、把真正的主人挤下线。5 次 / 15 分钟足够正常人改错重试，
     * 又能把爆破成本抬到不可行。
     * <p>
     * 用 Redis 计数而不是数据库字段：这是短时效的防护，不该污染用户表，也不该在
     * 密码正确时留下痕迹。键里用用户 id 而不是用户名，避免用户名出现在 key 里。
     */
    @Transactional
    public void changeOwnPassword(PasswordChangeRequest request) {
        UserEntity user = access.actor().user();
        String failKey = passwordChangeFailKey(user.getId());
        if (passwordChangeFailures(failKey) >= PASSWORD_CHANGE_MAX_FAILURES) {
            throw new TooManyRequestsException("原密码错误次数过多，请 15 分钟后再试");
        }
        // 只算一次哈希校验：BCrypt 是有成本的，之前同样的比较做了两次（一次判原密码、
        // 一次判新旧是否相同），这里复用第一次的结果。
        LocalCredentialEntity credential = credentials.selectById(user.getId());
        if (credential == null || credential.getEnabled() != 1) throw new BusinessException("当前账号未设置登录密码");
        boolean oldPasswordMatches = passwords.matches(request.oldPassword(), credential.getPasswordHash());
        if (!oldPasswordMatches || passwords.matches(request.newPassword(), credential.getPasswordHash())) {
            recordPasswordChangeFailure(failKey);
            // 两种情况统一成一句话：区分"原密码错误"与"新密码与原密码相同"会向
            // 尚未通过验证的调用者泄露"原密码是对的"，没有必要。
            throw new BusinessException("原密码不正确，或新密码与当前密码相同");
        }
        credential.setPasswordHash(passwords.encode(request.newPassword()));
        credential.setPasswordChangeRequired(0);
        credential.setPasswordChangedTime(LocalDateTime.now());
        credentials.updateById(credential);
        user.setAuthVersion(user.getAuthVersion() + 1);
        mapper.updateById(user);
        sessions.removeAll(user.getId());
        redis.delete(failKey);
    }

    private Long passwordChangeFailures(String key) {
        String value = redis.opsForValue().get(key);
        if (value == null) return 0L;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            // 计数被外部写坏时按 0 处理，而不是让改密码功能整体不可用。
            return 0L;
        }
    }

    private void recordPasswordChangeFailure(String key) {
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) redis.expire(key, PASSWORD_CHANGE_WINDOW);
    }

    private String passwordChangeFailKey(Long userId) {
        return "auth:password-change:fail:" + userId;
    }

    public void export(String keyword, HttpServletResponse response) throws IOException {
        AccessPolicy.Actor actor = access.actor();
        String filename = "用户列表_" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now()) + ".xlsx";
        ExcelExportUtil.write(response, filename, "用户列表", List.of(
                new ExcelExportColumn<>("用户名", UserExportRow::username), new ExcelExportColumn<>("姓名", UserExportRow::realName),
                new ExcelExportColumn<>("手机号", UserExportRow::phone), new ExcelExportColumn<>("邮箱", UserExportRow::email),
                new ExcelExportColumn<>("角色", UserExportRow::roleNames), new ExcelExportColumn<>("部门", UserExportRow::deptName),
                new ExcelExportColumn<>("状态", value -> value.status() == 1 ? "启用" : "禁用"), new ExcelExportColumn<>("创建时间", UserExportRow::createdTime)
        ), writer -> {
            long page = 1;
            while (true) {
                LambdaQueryWrapper<UserEntity> query = query(keyword);
                access.applyUserScope(query, actor);
                Page<UserEntity> batch = mapper.selectPage(new Page<>(page, EXPORT_BATCH_SIZE, false),
                        query.orderByDesc(UserEntity::getCreatedTime).orderByDesc(UserEntity::getId));
                for (UserExportRow row : toExportRows(batch.getRecords())) writer.write(row);
                if (batch.getRecords().size() < EXPORT_BATCH_SIZE) return;
                page++;
            }
        });
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
        access.requireAssignableRoles(actor, assigned);
    }

    private void replaceRoles(Long userId, List<Long> roleIds) { userRoles.deleteByUserId(userId); roleIds.stream().distinct().forEach(roleId -> userRoles.insert(userId, roleId)); }
    private String temporaryPassword() {
        return PasswordRules.randomCompliantPassword(random, 16);
    }
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
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
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
            boolean manageable = access.canManageUser(actor, user, superAdminTarget,
                    permissionsByUser.getOrDefault(user.getId(), Set.of()), active.stream().map(UserRoleAssignment::getDataScope).toList());
            return new UserListVO(user.getId(), user.getUsername(), user.getRealName(), user.getPhone(),
                    user.getDeptId() == null ? "-" : departmentNames.getOrDefault(user.getDeptId(), "-"),
                    active.stream().map(UserRoleAssignment::getRoleName).collect(Collectors.joining("、")), user.getStatus(),
                    user.getCreatedTime(), manageable, manageable && user.getBuiltin() != 1,
                    manageable && actor.superAdmin() && !superAdminTarget);
        }).toList();
    }

    private List<UserExportRow> toExportRows(List<UserEntity> users) {
        if (users.isEmpty()) return List.of();
        List<Long> userIds = users.stream().map(UserEntity::getId).toList();
        Map<Long, List<UserRoleAssignment>> rolesByUser = roles.selectAssignedByUserIds(userIds).stream()
                .filter(role -> role.getStatus() == 1).collect(Collectors.groupingBy(UserRoleAssignment::getUserId));
        Set<Long> deptIds = users.stream().map(UserEntity::getDeptId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> departmentNames = deptIds.isEmpty() ? Map.of() : depts.selectBatchIds(deptIds).stream()
                .collect(Collectors.toMap(DeptEntity::getId, DeptEntity::getDeptName));
        return users.stream().map(user -> new UserExportRow(user.getUsername(), user.getRealName(), user.getPhone(), user.getEmail(),
                rolesByUser.getOrDefault(user.getId(), List.of()).stream().map(UserRoleAssignment::getRoleName).collect(Collectors.joining("、")),
                user.getDeptId() == null ? "-" : departmentNames.getOrDefault(user.getDeptId(), "-"), user.getStatus(), user.getCreatedTime())).toList();
    }

    private Map<Long, Set<String>> rolePermissions(List<Long> roleIds) {
        if (roleIds.isEmpty()) return Map.of();
        Map<Long, Set<String>> result = new HashMap<>();
        roleMenus.selectPermissionCodesByRoleIds(roleIds).forEach(row ->
                result.computeIfAbsent(row.getRoleId(), ignored -> new java.util.HashSet<>()).add(row.getPermissionCode()));
        return result;
    }

    private record UserExportRow(String username, String realName, String phone, String email,
                                 String roleNames, String deptName, Integer status, LocalDateTime createdTime) { }
}
