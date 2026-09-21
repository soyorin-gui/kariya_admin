package org.kariya.system.user;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.kariya.common.result.*;
import org.kariya.system.dept.DeptEntity;
import org.kariya.system.dept.DeptMapper;
import org.kariya.system.role.RoleEntity;
import org.kariya.system.role.RoleMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/system/users")
public class UserController {
    private final UserService service;
    private final DeptMapper depts;
    private final RoleMapper roles;

    public UserController(UserService service, DeptMapper depts, RoleMapper roles) {
        this.service = service;
        this.depts = depts;
        this.roles = roles;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('system:user:list')")
    Result<PageResult<UserVO>> page(@RequestParam(defaultValue = "1") long pageNum, @RequestParam(defaultValue = "10") long pageSize, @RequestParam(required = false) String keyword) {
        return Result.ok(service.page(pageNum, pageSize, keyword));
    }

    @GetMapping("/form-options")
    @PreAuthorize("hasAnyAuthority('system:user:add', 'system:user:update')")
    Result<UserFormOptions> formOptions() {
        return Result.ok(new UserFormOptions(
                depts.selectList(new LambdaQueryWrapper<DeptEntity>().eq(DeptEntity::getStatus, 1).orderByAsc(DeptEntity::getSortOrder)).stream().map(value -> new UserFormOptions.Option(value.getId(), value.getDeptName())).toList(),
                roles.selectList(new LambdaQueryWrapper<RoleEntity>().eq(RoleEntity::getStatus, 1)).stream().map(value -> new UserFormOptions.Option(value.getId(), value.getRoleName())).toList()
        ));
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('system:user:export')")
    void export(@RequestParam(required = false) String keyword, HttpServletResponse response) throws java.io.IOException {
        service.export(keyword, response);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:user:add')")
    Result<UserVO> create(@Valid @RequestBody UserRequest r) {
        return Result.ok(service.create(r), "新增用户成功");
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:update')")
    Result<UserVO> update(@PathVariable Long id, @Valid @RequestBody UserRequest r) {
        return Result.ok(service.update(id, r), "修改用户成功");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:delete')")
    Result<Void> delete(@PathVariable Long id) {
        service.remove(id);
        return Result.ok(null, "删除用户成功");
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('system:user:reset-password')")
    Result<Void> reset(@PathVariable Long id) {
        service.resetPassword(id);
        return Result.ok(null, "密码重置成功");
    }
}
