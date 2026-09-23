package org.lbl.system.user.controller;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import org.lbl.common.result.*;
import org.lbl.system.log.aspect.OperationLog;
import org.lbl.system.user.request.PasswordChangeRequest;
import org.lbl.system.user.request.UserRequest;
import org.lbl.system.user.service.UserService;
import org.lbl.system.user.vo.UserCreated;
import org.lbl.system.user.vo.UserFormOptions;
import org.lbl.system.user.vo.UserVO;
import org.lbl.system.user.vo.UsernameAvailability;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/system/users")
public class UserController {
    /**
     * Controller 只依赖 Service。
     * <p>
     * 以前这里还注入了 DeptMapper / RoleMapper，在 form-options 里当场查库拼装返回值——
     * 那让 Controller 与持久层直接耦合。现在取数全部下沉到 {@link UserService#formOptions()}，
     * 本类不再 import 任何 Mapper，分层边界得以守住。
     */
    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('system:user:list')")
    Result<PageResult<UserVO>> page(@RequestParam(defaultValue = "1") long pageNum, @RequestParam(defaultValue = "10") long pageSize, @RequestParam(required = false) String keyword) {
        return Result.ok(service.page(pageNum, pageSize, keyword));
    }

    @GetMapping("/form-options")
    @PreAuthorize("hasAnyAuthority('system:user:add', 'system:user:update')")
    Result<UserFormOptions> formOptions() {
        return Result.ok(service.formOptions());
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
