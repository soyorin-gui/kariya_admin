package org.kariya.system.user;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.kariya.common.result.*;
import org.kariya.system.dept.DeptEntity;
import org.kariya.system.dept.DeptMapper;
import org.kariya.system.log.OperationLog;
import org.kariya.system.role.RoleEntity;
import org.kariya.system.role.RoleMapper;
import org.kariya.security.context.AccessPolicy;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/system/users")
public class UserController {
    private final UserService service;
    private final DeptMapper depts;
    private final RoleMapper roles;
    private final AccessPolicy access;

    public UserController(UserService service, DeptMapper depts, RoleMapper roles, AccessPolicy access) {
        this.service = service;
        this.depts = depts;
        this.roles = roles;
        this.access = access;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('system:user:list')")
    Result<PageResult<UserVO>> page(@RequestParam(defaultValue = "1") long pageNum, @RequestParam(defaultValue = "10") long pageSize, @RequestParam(required = false) String keyword) {
        return Result.ok(service.page(pageNum, pageSize, keyword));
    }

    @GetMapping("/form-options")
    @PreAuthorize("hasAnyAuthority('system:user:add', 'system:user:update')")
    Result<UserFormOptions> formOptions() {
        AccessPolicy.Actor actor = access.actor();
        return Result.ok(new UserFormOptions(
                depts.selectList(new LambdaQueryWrapper<DeptEntity>().eq(DeptEntity::getStatus, 1).orderByAsc(DeptEntity::getSortOrder)).stream()
                        .filter(value -> actor.superAdmin() || actor.all() || actor.departments().contains(value.getId()))
                        .map(value -> new UserFormOptions.Option(value.getId(), value.getDeptName())).toList(),
                roles.selectList(new LambdaQueryWrapper<RoleEntity>().eq(RoleEntity::getStatus, 1)).stream()
                        .filter(value -> access.canAssignRole(actor, value))
                        .map(value -> new UserFormOptions.Option(value.getId(), value.getRoleName())).toList()
        ));
    }

    /**
     * 新增用户时的即时重名校验，供表单在输入阶段给出提示。
     * 只授予 system:user:add 的人需要它——重名提示只在新增场景有意义（编辑时用户名不可改）。
     */
    @GetMapping("/username-available")
    @PreAuthorize("hasAuthority('system:user:add')")
    Result<UsernameAvailability> usernameAvailable(@RequestParam String username) {
        return Result.ok(service.checkUsername(username));
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('system:user:export')")
    @OperationLog(module = "用户管理", action = "导出用户")
    void export(@RequestParam(required = false) String keyword, HttpServletResponse response) throws java.io.IOException {
        service.export(keyword, response);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:user:add')")
    @OperationLog(module = "用户管理", action = "新增用户")
    Result<UserCreated> create(@Valid @RequestBody UserRequest r) {
        return Result.ok(service.create(r), "新增用户成功");
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:update')")
    @OperationLog(module = "用户管理", action = "修改用户")
    Result<UserVO> update(@PathVariable Long id, @Valid @RequestBody UserRequest r) {
        return Result.ok(service.update(id, r), "修改用户成功");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:delete')")
    @OperationLog(module = "用户管理", action = "删除用户")
    Result<Void> delete(@PathVariable Long id) {
        service.remove(id);
        return Result.ok(null, "删除用户成功");
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('system:user:reset-password')")
    @OperationLog(module = "用户管理", action = "重置密码")
    Result<Map<String, String>> reset(@PathVariable Long id) {
        return Result.ok(Map.of("temporaryPassword", service.resetPassword(id)), "密码重置成功");
    }

    /**
     * 修改本人密码。刻意不加 @PreAuthorize：首次登录的用户 authorities 是空的（见 JwtAuthenticationFilter），
     * 必须让"待改初始密码"的账号能够调用它。
     */
    @PutMapping("/me/password")
    @OperationLog(module = "个人中心", action = "修改密码")
    Result<Void> changeOwnPassword(@Valid @RequestBody PasswordChangeRequest request) {
        service.changeOwnPassword(request);
        return Result.ok(null, "密码修改成功，请重新登录");
    }
}
