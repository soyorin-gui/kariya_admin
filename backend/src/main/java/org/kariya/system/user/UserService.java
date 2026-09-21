package org.kariya.system.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletResponse;
import org.kariya.auth.session.SessionService;
import org.kariya.common.exception.BusinessException;
import org.kariya.common.result.PageResult;
import org.kariya.common.util.ExcelExportColumn;
import org.kariya.common.util.ExcelExportUtil;
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

    public UserService(UserMapper mapper, UserRoleMapper userRoles, DeptMapper depts, RoleMapper roles, PasswordEncoder passwords, SessionService sessions) {
        this.mapper = mapper;
        this.userRoles = userRoles;
        this.depts = depts;
        this.roles = roles;
        this.passwords = passwords;
        this.sessions = sessions;
    }

    public PageResult<UserVO> page(long page, long size, String keyword) {
        Page<UserEntity> result = mapper.selectPage(Page.of(page, size), query(keyword).orderByDesc(UserEntity::getCreatedTime));
        return new PageResult<>(result.getRecords().stream().map(this::toView).toList(), result.getTotal(), page, size);
    }

    @Transactional
    public UserVO create(UserRequest request) {
        if (mapper.selectCount(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername, request.username().trim())) > 0) throw new BusinessException("用户名已存在");
        UserEntity user = new UserEntity();
        user.setUsername(request.username().trim());
        user.setRealName(request.realName());
        user.setPhone(request.phone());
        user.setEmail(request.email());
        user.setDeptId(request.deptId());
        user.setStatus(request.status());
        user.setAuthVersion(1L);
        user.setPasswordHash(passwords.encode("Admin@123456"));
        mapper.insert(user);
        replaceRoles(user.getId(), request.roleIds());
        return toView(user);
    }

    @Transactional
    public UserVO update(Long id, UserRequest request) {
        UserEntity user = require(id);
        if (user.getBuiltin() == 1 && request.status() != 1) throw new BusinessException("内置管理员不能禁用");
        user.setRealName(request.realName());
        user.setPhone(request.phone());
        user.setEmail(request.email());
        user.setDeptId(request.deptId());
        user.setStatus(request.status());
        mapper.updateById(user);
        replaceRoles(id, request.roleIds());
        if (request.status() != 1) sessions.removeAll(id);
        return toView(user);
    }

    @Transactional
    public void remove(Long id) {
        UserEntity user = require(id);
        if (user.getBuiltin() == 1) throw new BusinessException("内置用户不能删除");
        mapper.deleteById(id);
        userRoles.deleteByUserId(id);
        sessions.removeAll(id);
    }

    @Transactional
    public void resetPassword(Long id) {
        UserEntity user = require(id);
        user.setPasswordHash(passwords.encode("Admin@123456"));
        user.setAuthVersion(user.getAuthVersion() + 1);
        mapper.updateById(user);
        sessions.removeAll(id);
    }

    public void export(String keyword, HttpServletResponse response) throws IOException {
        List<UserVO> records = mapper.selectList(query(keyword).orderByDesc(UserEntity::getCreatedTime)).stream().map(this::toView).toList();
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

    private void replaceRoles(Long userId, List<Long> roleIds) { userRoles.deleteByUserId(userId); roleIds.stream().distinct().forEach(roleId -> userRoles.insert(userId, roleId)); }
    private UserEntity require(Long id) { UserEntity user = mapper.selectById(id); if (user == null) throw new BusinessException("用户不存在"); return user; }
    private UserVO toView(UserEntity user) {
        List<RoleEntity> assignedRoles = roles.selectByUserId(user.getId());
        DeptEntity department = depts.selectById(user.getDeptId());
        return new UserVO(user.getId(), user.getUsername(), user.getRealName(), user.getPhone(), user.getEmail(), user.getDeptId(), department == null ? "-" : department.getDeptName(), assignedRoles.stream().map(RoleEntity::getId).toList(), assignedRoles.stream().map(RoleEntity::getRoleName).collect(Collectors.joining("、")), user.getStatus(), user.getCreatedTime());
    }
}
