package org.lbl.system.user.controller;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import org.lbl.common.result.*;
import org.lbl.system.log.aspect.OperationLog;
import org.lbl.system.user.request.PasswordChangeRequest;
import org.lbl.system.user.request.UserRequest;
import org.lbl.system.user.request.UserCreateRequest;
import org.lbl.system.user.service.UserService;
import org.lbl.system.user.service.UserExportService;
import org.lbl.system.user.service.UserPasswordService;
import org.lbl.system.user.service.UserSessionAdministrationService;
import org.lbl.system.user.vo.UserFormOptions;
import org.lbl.system.user.vo.UserVO;
import org.lbl.system.user.vo.UserListVO;
import org.lbl.system.user.vo.UsernameAvailability;
import org.lbl.auth.session.SessionView;
import org.lbl.auth.session.RefreshCookieFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;

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
    private final UserExportService exports;
    private final UserPasswordService passwordService;
    private final UserSessionAdministrationService sessionAdministration;

    public UserController(UserService service, UserExportService exports, UserPasswordService passwordService,
                          UserSessionAdministrationService sessionAdministration) {
        this.service = service;
        this.exports = exports;
        this.passwordService = passwordService;
        this.sessionAdministration = sessionAdministration;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('system:user:list')")
    Result<PageResult<UserListVO>> page(@RequestParam(defaultValue = "1") long pageNum, @RequestParam(defaultValue = "10") long pageSize, @RequestParam(required = false) String keyword) {
        return Result.ok(service.page(pageNum, pageSize, keyword));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:update')")
    Result<UserVO> detail(@PathVariable Long id) {
        return Result.ok(service.detail(id));
    }

    @GetMapping("/form-options")
    @PreAuthorize("hasAnyAuthority('system:user:add', 'system:user:update')")
    Result<UserFormOptions> formOptions(@RequestParam(defaultValue = "add") String operation) {
        return Result.ok(service.formOptions(operation));
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
        exports.export(keyword, response);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:user:add')")
    @OperationLog(module = "用户管理", action = "新增用户")
    Result<UserVO> create(@Valid @RequestBody UserCreateRequest r) {
        return Result.ok(service.create(r), "新增用户成功");
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:update')")
    @OperationLog(module = "用户管理", action = "修改用户", targetType = "USER", targetId = "#id")
    Result<UserVO> update(@PathVariable Long id, @Valid @RequestBody UserRequest r) {
        return Result.ok(service.update(id, r), "修改用户成功");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:delete')")
    @OperationLog(module = "用户管理", action = "删除用户", targetType = "USER", targetId = "#id")
    Result<Void> delete(@PathVariable Long id) {
        service.remove(id);
        return Result.ok(null, "删除用户成功");
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('system:user:reset-password')")
    @OperationLog(module = "用户管理", action = "重置密码", targetType = "USER", targetId = "#id")
    Result<Map<String, String>> reset(@PathVariable Long id) {
        return Result.ok(Map.of("temporaryPassword", passwordService.reset(id)), "密码重置成功");
    }

    @GetMapping("/{id}/sessions")
    @PreAuthorize("hasAuthority('system:user:update')")
    Result<List<SessionView>> sessions(@PathVariable Long id,
                                      @CookieValue(value = RefreshCookieFactory.NAME, required = false) String currentSid) {
        return Result.ok(sessionAdministration.list(id, currentSid));
    }

    @DeleteMapping("/{id}/sessions/{reference}")
    @PreAuthorize("hasAuthority('system:user:update')")
    @OperationLog(module = "用户管理", action = "踢出用户会话", targetType = "USER_SESSION", targetId = "#id + ':' + #reference")
    Result<Void> removeSession(@PathVariable Long id, @PathVariable String reference) {
        sessionAdministration.remove(id, reference);
        return Result.ok(null, "该会话已退出登录");
    }

    @DeleteMapping("/{id}/sessions")
    @PreAuthorize("hasAuthority('system:user:update')")
    @OperationLog(module = "用户管理", action = "踢出用户全部会话", targetType = "USER", targetId = "#id")
    Result<Void> removeSessions(@PathVariable Long id) {
        sessionAdministration.removeAll(id);
        return Result.ok(null, "该用户的全部会话已退出登录");
    }

    /**
     * 修改本人密码。
     * <p>
     * <b>刻意不要求任何权限码</b>：首次登录的用户 authorities 是空的（见 JwtAuthenticationFilter），
     * 必须让"待改初始密码"的账号能够调用它 —— 因此这里只能写
     * {@code !hasAuthority('onboarding:access')} 这种"排除式"表达式，<b>绝不能</b>改成
     * {@code hasAuthority('xxx')}：那会让待改密用户连改密码都做不到，只能永久卡在强制改密弹窗上。
     * <p>
     * 但<b>必须显式排除开户确认态</b>（ONBOARDING）。它是第三方登录后"还没注册、还没绑定"的临时身份，
     * 不属于任何成员，却同样满足 {@code isAuthenticated()}（见 JwtAuthenticationFilter 里为它构造的
     * 认证对象），所以 {@code anyRequest().authenticated()} 拦不住它。全项目其余成员接口都是这个口径
     * （AccountProfileController / AccountSessionController / AccountIdentityController /
     * NotificationController / DepartmentTransferController / RealtimeController），只有这里漏了。
     * <p>
     * 漏掉的代价不是越权（{@code AccessPolicy.actor()} 按用户名查不到开户态用户，写操作不会执行），
     * 而是<b>错误的状态码与错误的提示</b>：它返回 401，而前端把 401 一律当作"会话过期"去静默续期
     * （开户态的续期还会成功），于是多两次无谓往返、搅动全局续期状态，极端情况下（续期恰好失败）
     * 还会把用户清会话踢回登录页并提示"登录状态已失效"——而事实只是"你没有权限调用这个接口"。
     * 显式拒绝后返回 403：前端不续期、只提示无权限（状态码约定见 utils/request.ts）。
     */
    @PutMapping("/me/password")
    @PreAuthorize("isAuthenticated() and !hasAuthority('onboarding:access')")
    @OperationLog(module = "个人中心", action = "修改密码")
    Result<Void> changeOwnPassword(@Valid @RequestBody PasswordChangeRequest request) {
        passwordService.changeOwn(request);
        return Result.ok(null, "密码修改成功，请重新登录");
    }
}
