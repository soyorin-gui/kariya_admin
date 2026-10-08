package org.lbl.system.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletResponse;
import org.lbl.common.util.ExcelExportColumn;
import org.lbl.common.util.ExcelExportUtil;
import org.lbl.security.context.AccessPolicy;
import org.lbl.system.dept.entity.DeptEntity;
import org.lbl.system.dept.mapper.DeptMapper;
import org.lbl.system.role.mapper.RoleMapper;
import org.lbl.system.user.entity.UserEntity;
import org.lbl.system.user.mapper.UserMapper;
import org.lbl.system.user.vo.UserRoleAssignment;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 大批量用户导出用例，与用户增删改事务解耦。 */
@Service
public class UserExportService {
    private static final long BATCH_SIZE = 500;
    private final UserMapper users;
    private final RoleMapper roles;
    private final DeptMapper depts;
    private final AccessPolicy access;

    public UserExportService(UserMapper users, RoleMapper roles, DeptMapper depts, AccessPolicy access) {
        this.users = users;
        this.roles = roles;
        this.depts = depts;
        this.access = access;
    }

    public void export(String keyword, HttpServletResponse response) throws IOException {
        AccessPolicy.Actor actor = access.actor("system:user:export");
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
                Page<UserEntity> batch = users.selectPage(new Page<>(page, BATCH_SIZE, false),
                        query.orderByDesc(UserEntity::getCreatedTime).orderByDesc(UserEntity::getId));
                for (UserExportRow row : rows(batch.getRecords())) writer.write(row);
                if (batch.getRecords().size() < BATCH_SIZE) return;
                page++;
            }
        });
    }

    private LambdaQueryWrapper<UserEntity> query(String keyword) {
        return new LambdaQueryWrapper<UserEntity>().and(keyword != null && !keyword.isBlank(), condition ->
                condition.like(UserEntity::getUsername, keyword).or().like(UserEntity::getRealName, keyword));
    }

    private List<UserExportRow> rows(List<UserEntity> values) {
        if (values.isEmpty()) return List.of();
        List<Long> userIds = values.stream().map(UserEntity::getId).toList();
        Map<Long, List<UserRoleAssignment>> rolesByUser = roles.selectAssignedByUserIds(userIds).stream()
                .filter(role -> role.getStatus() == 1).collect(Collectors.groupingBy(UserRoleAssignment::getUserId));
        Set<Long> deptIds = values.stream().map(UserEntity::getDeptId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> departmentNames = deptIds.isEmpty() ? Map.of() : depts.selectByIds(deptIds).stream()
                .collect(Collectors.toMap(DeptEntity::getId, DeptEntity::getDeptName));
        return values.stream().map(user -> new UserExportRow(user.getUsername(), user.getRealName(), user.getPhone(), user.getEmail(),
                rolesByUser.getOrDefault(user.getId(), List.of()).stream().map(UserRoleAssignment::getRoleName).collect(Collectors.joining("、")),
                user.getDeptId() == null ? "-" : departmentNames.getOrDefault(user.getDeptId(), "-"), user.getStatus(), user.getCreatedTime())).toList();
    }

    private record UserExportRow(String username, String realName, String phone, String email,
                                 String roleNames, String deptName, Integer status, LocalDateTime createdTime) { }
}
