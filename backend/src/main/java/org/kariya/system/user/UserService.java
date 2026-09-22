package org.kariya.system.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletResponse;
import org.kariya.auth.session.SessionService;
import org.kariya.common.exception.BusinessException;
import org.kariya.common.result.PageResult;
import org.kariya.common.util.ExcelExportColumn;
import org.kariya.common.util.ExcelExportUtil;
import org.kariya.security.context.AccessPolicy;
import org.kariya.system.dept.DeptEntity;
import org.kariya.system.dept.DeptMapper;
import org.kariya.system.role.RoleEntity;
import org.kariya.system.role.RoleMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {
    private final UserMapper mapper;
    private final UserRoleMapper userRoles;
    private final DeptMapper depts;
    private final RoleMapper roles;
    private final PasswordEncoder passwords;
    private final SessionService sessions;
    private final AccessPolicy access;
    private final SecureRandom random = new SecureRandom();

    public UserService(UserMapper mapper, UserRoleMapper userRoles, DeptMapper depts, RoleMapper roles, PasswordEncoder passwords, SessionService sessions, AccessPolicy access) {
        this.mapper = mapper;
        this.userRoles = userRoles;
        this.depts = depts;
        this.roles = roles;
        this.passwords = passwords;
        this.sessions = sessions;
        this.access = access;
    }

    public PageResult<UserVO> page(long page, long size, String keyword) {
        if (page < 1 || size < 1 || size > 100) throw new BusinessException("分页参数无效，每页最多 100 条");
        AccessPolicy.Actor actor = access.actor();
        LambdaQueryWrapper<UserEntity> query = query(keyword);
        access.applyUserScope(query, actor);
        Page<UserEntity> result = mapper.selectPage(Page.of(page, size), query.orderByDesc(UserEntity::getCreatedTime).orderByDesc(UserEntity::getId));
        return new PageResult<>(result.getRecords().stream().map(user -> toView(user, actor)).toList(), result.getTotal(), page, size);
    }

    @Transactional
    public UserCreated create(UserRequest request) {
        AccessPolicy.Actor actor = access.actor();
        validateAssignment(actor, request);
        requireUsernameAvailable(request.username().trim());
        UserEntity user = new UserEntity();
        user.setUsername(request.username().trim());
        user.setRealName(request.realName());
        user.setPhone(request.phone());
        user.setEmail(request.email());
        user.setDeptId(request.deptId());
        user.setStatus(request.status());
        user.setAuthVersion(1L);
        String temporaryPassword = temporaryPassword();
        user.setPasswordHash(passwords.encode(temporaryPassword));
        user.setPasswordChangeRequired(1);
        mapper.insert(user);
        replaceRoles(user.getId(), request.roleIds());
        return new UserCreated(toView(user, actor), temporaryPassword);
    }

    @Transactional
    public UserVO update(Long id, UserRequest request) {
        AccessPolicy.Actor actor = access.actor();
        UserEntity user = require(id);
        access.requireManageUser(actor, user);
        validateAssignment(actor, request);
        if (user.getBuiltin() == 1 && request.status() != 1) throw new BusinessException("内置管理员不能禁用");
        if (user.getBuiltin() == 1 && request.roleIds().stream().map(roles::selectById)
                .noneMatch(role -> role != null && "super_admin".equals(role.getRoleCode()))) {
            throw new BusinessException("内置管理员必须保留超级管理员角色");
        }
        user.setRealName(request.realName());
        user.setPhone(request.phone());
        user.setEmail(request.email());
        user.setDeptId(request.deptId());
        user.setStatus(request.status());
        replaceRoles(id, request.roleIds());
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
        mapper.deleteById(id);
        userRoles.deleteByUserId(id);
        sessions.removeAll(id);
    }

    @Transactional
    public String resetPassword(Long id) {
        AccessPolicy.Actor actor = access.actor();
        UserEntity user = require(id);
        access.requireManageUser(actor, user);
        if (!actor.superAdmin() || roles.selectAssignedByUserId(id).stream().anyMatch(role -> "super_admin".equals(role.getRoleCode()))) {
            throw new BusinessException("仅超级管理员可重置普通用户密码");
        }
        String temporaryPassword = temporaryPassword();
        user.setPasswordHash(passwords.encode(temporaryPassword));
        user.setPasswordChangeRequired(1);
        user.setAuthVersion(user.getAuthVersion() + 1);
        mapper.updateById(user);
        sessions.removeAll(id);
        return temporaryPassword;
    }

    @Transactional
    public void changeOwnPassword(PasswordChangeRequest request) {
        UserEntity user = access.actor().user();
        if (!passwords.matches(request.oldPassword(), user.getPasswordHash())) throw new BusinessException("原密码不正确");
        if (passwords.matches(request.newPassword(), user.getPasswordHash())) throw new BusinessException("新密码不能与原密码相同");
        user.setPasswordHash(passwords.encode(request.newPassword()));
        user.setPasswordChangeRequired(0);
        user.setAuthVersion(user.getAuthVersion() + 1);
        mapper.updateById(user);
        sessions.removeAll(user.getId());
    }

    public void export(String keyword, HttpServletResponse response) throws IOException {
        AccessPolicy.Actor actor = access.actor();
        LambdaQueryWrapper<UserEntity> query = query(keyword);
        access.applyUserScope(query, actor);
        List<UserVO> records = mapper.selectList(query.orderByDesc(UserEntity::getCreatedTime).orderByDesc(UserEntity::getId))
                .stream().map(user -> toView(user, actor)).toList();
        String filename = "用户列表_" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now()) + ".xlsx";
        ExcelExportUtil.write(response, filename, "用户列表", List.of(
                new ExcelExportColumn<>("用户名", UserVO::username), new ExcelExportColumn<>("姓名", UserVO::realName),
                new ExcelExportColumn<>("手机号", UserVO::phone), new ExcelExportColumn<>("邮箱", UserVO::email),
                new ExcelExportColumn<>("角色", UserVO::roleNames), new ExcelExportColumn<>("部门", UserVO::deptName),
                new ExcelExportColumn<>("状态", value -> value.status() == 1 ? "启用" : "禁用"), new ExcelExportColumn<>("创建时间", UserVO::createdTime)
        ), records);
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

    private void validateAssignment(AccessPolicy.Actor actor, UserRequest request) {
        DeptEntity department = depts.selectById(request.deptId());
        if (department == null || department.getStatus() != 1) throw new BusinessException("部门不存在或已停用");
        if (!actor.superAdmin() && !actor.departments().contains(request.deptId()) && !actor.all()) {
            throw new BusinessException("不能将用户分配到数据范围之外的部门");
        }
        if (request.status() != 0 && request.status() != 1) throw new BusinessException("用户状态无效");
        List<RoleEntity> assigned = new ArrayList<>();
        for (Long roleId : request.roleIds()) {
            RoleEntity role = roleId == null ? null : roles.selectById(roleId);
            if (role == null || !access.canAssignRole(actor, role)) throw new BusinessException("包含无权分配或已停用的角色");
            assigned.add(role);
        }
        access.requireAssignableRoles(actor, assigned);
    }

    private void replaceRoles(Long userId, List<Long> roleIds) { userRoles.deleteByUserId(userId); roleIds.stream().distinct().forEach(roleId -> userRoles.insert(userId, roleId)); }
    private String temporaryPassword() {
        byte[] bytes = new byte[18];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    private UserEntity require(Long id) { UserEntity user = mapper.selectById(id); if (user == null) throw new BusinessException("用户不存在"); return user; }
    private UserVO toView(UserEntity user, AccessPolicy.Actor actor) {
        List<RoleEntity> assignedRoles = roles.selectByUserId(user.getId());
        DeptEntity department = depts.selectById(user.getDeptId());
        boolean manageable = access.canManageUser(actor, user);
        boolean superAdminTarget = roles.selectAssignedByUserId(user.getId()).stream()
                .anyMatch(role -> "super_admin".equals(role.getRoleCode()));
        return new UserVO(user.getId(), user.getUsername(), user.getRealName(), user.getPhone(), user.getEmail(), user.getDeptId(),
                department == null ? "-" : department.getDeptName(), assignedRoles.stream().map(RoleEntity::getId).toList(),
                assignedRoles.stream().map(RoleEntity::getRoleName).collect(Collectors.joining("、")), user.getStatus(),
                user.getCreatedTime(), manageable, manageable && user.getBuiltin() != 1,
                manageable && actor.superAdmin() && !superAdminTarget);
    }
}
