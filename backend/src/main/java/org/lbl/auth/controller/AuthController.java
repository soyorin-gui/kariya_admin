package org.lbl.auth.controller;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.lbl.auth.model.*;
import org.lbl.auth.service.AuthService;
import org.lbl.auth.service.RegistrationService;
import org.lbl.auth.session.*;
import org.lbl.auth.identity.LocalCredentialEntity;
import org.lbl.auth.identity.LocalCredentialMapper;
import org.lbl.common.exception.UnauthorizedException;
import org.lbl.common.result.Result;
import org.lbl.security.context.AccessPolicy;
import org.lbl.security.jwt.JwtService;
import org.lbl.system.menu.entity.MenuEntity;
import org.lbl.system.menu.mapper.MenuMapper;
import org.lbl.system.role.entity.RoleEntity;
import org.lbl.system.role.mapper.RoleMapper;
import org.lbl.system.user.entity.UserEntity;
import org.lbl.system.user.mapper.UserMapper;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final SessionService sessions;
    private final RoleMapper roles;
    private final MenuMapper menus;
    private final UserMapper users;
    private final JwtService jwt;
    private final RefreshCookieFactory refreshCookies;
    private final LocalCredentialMapper credentials;
    private final RegistrationService registrations;

    public AuthController(AuthService auth, SessionService sessions, RoleMapper roles, MenuMapper menus, UserMapper users,
                          JwtService jwt, RefreshCookieFactory refreshCookies, LocalCredentialMapper credentials,
                          RegistrationService registrations) {
        this.auth = auth;
        this.sessions = sessions;
        this.roles = roles;
        this.menus = menus;
        this.users = users;
        this.jwt = jwt;
        this.refreshCookies = refreshCookies;
        this.credentials = credentials;
        this.registrations = registrations;
    }

    @PostMapping("/login")
    public Result<LoginResult> login(@Valid @RequestBody LoginRequest request,
                                     @CookieValue(value = RefreshCookieFactory.NAME, required = false) String previousSid,
                                     HttpServletResponse response) {
        LoginResult result = auth.login(request);
        String sid = extractSid(result.accessToken());
        // 同一浏览器再次显式登录应替换旧会话，而不是在 Redis 中累加一台“新设备”。
        // 这也能回收“登录已成功，但紧接着 /auth/me 因网络失败”后用户重试留下的旧会话。
        if (previousSid != null && !previousSid.isBlank() && !previousSid.equals(sid)) sessions.remove(previousSid);
        response.addHeader("Set-Cookie", refreshCookies.createFor(sid, sessions.find(sid)).toString());
        return Result.ok(result, "登录成功");
    }

    @PostMapping("/register")
    public Result<LoginResult> register(@Valid @RequestBody RegistrationRequest request, HttpServletResponse response) {
        SessionGrant grant = registrations.register(request);
        response.addHeader("Set-Cookie", refreshCookies.createFor(grant.sid(), sessions.find(grant.sid())).toString());
        return Result.ok(new LoginResult(grant.accessToken(), grant.user()), "注册成功");
    }

    @PostMapping("/refresh")
    public Result<Map<String, Object>> refresh(@CookieValue(value = RefreshCookieFactory.NAME, required = false) String sid) {
        if (sid == null) throw new UnauthorizedException("登录状态已失效");
        AuthService.RefreshGrant grant = auth.refresh(sid);
        return Result.ok(Map.of("accessToken", grant.accessToken(),
                "passwordChangeRequired", grant.passwordChangeRequired()));
    }

    @PostMapping("/touch")
    public Result<Void> touch(@CookieValue(value = RefreshCookieFactory.NAME, required = false) String sid) {
        if (sid == null) throw new UnauthorizedException("登录状态已失效");
        auth.touch(sid);
        return Result.ok(null);
    }

    @PostMapping("/logout")
    public Result<Void> logout(@CookieValue(value = RefreshCookieFactory.NAME, required = false) String sid, HttpServletResponse response) {
        auth.logout(sid);
        // 清除时属性必须与签发时一致（path / secure / sameSite），否则浏览器会当成另一个 Cookie，
        // 结果是"点了退出但凭据还留着"。
        response.addHeader("Set-Cookie", refreshCookies.clear().toString());
        return Result.ok(null);
    }

    @GetMapping("/me")
    public Result<Map<String, Object>> me(@CookieValue(value = RefreshCookieFactory.NAME, required = false) String sid) {
        if (sid == null) throw new UnauthorizedException("登录状态已失效");
        LoginSession s = sessions.find(sid);
        if (s == null) throw new UnauthorizedException("登录状态已失效");
        if (s.onboarding()) {
            Map<String, Object> onboarding = new LinkedHashMap<>();
            onboarding.put("providerKey", s.providerKey());
            onboarding.put("displayName", s.displayName());
            onboarding.put("email", s.email());
            onboarding.put("employeeNo", s.employeeNo());
            return Result.ok(Map.of(
                    "principalType", "ONBOARDING",
                    "onboarding", onboarding,
                    "permissions", List.of("onboarding:access", "onboarding:account:create", "onboarding:account:bind"),
                    "menus", List.of(),
                    "routes", List.of()));
        }
        UserEntity user = users.selectById(s.userId());
        if (user == null || user.getStatus() != 1 || !user.getAuthVersion().equals(s.authVersion())) throw new UnauthorizedException("登录状态已失效");
        LocalCredentialEntity credential = credentials.selectById(user.getId());
        // 到期状态只在密码登录/刷新会话时计算；这里读取会话快照，避免业务请求途中突然丢权限。
        boolean pendingPasswordChange = "PASSWORD".equals(s.authMethod()) && s.passwordChangeRequired();
        List<RoleEntity> assignedRoles = roles.selectByUserId(s.userId());
        // 待改密的账号必须是"什么都看不到"的状态：此前只清空了 permissions，menus 仍返回全量，
        // 于是首登用户的侧边栏是完整的，但每个请求都会因为 authorities 为空被 403 —— 前端菜单和
        // 后端权限对不上。两个字段必须同进同退。
        List<MenuEntity> visibleMenus = pendingPasswordChange ? List.of() : menus.selectByUserId(s.userId());
        // superAdmin 与后端其它地方同一口径：必须角色 status=1 才算（见 AccessPolicy.isSuperAdmin）。
        // 这里用"含停用角色"的查询，再用统一判定过滤，避免"停用的 super_admin 仍被前端当成超管"。
        boolean superAdmin = AccessPolicy.containsSuperAdmin(roles.selectAssignedByUserId(s.userId()));
        return Result.ok(Map.of("principalType", "MEMBER", "user", Map.of("id", user.getId(), "username", user.getUsername(), "realName", user.getRealName(),
                "passwordChangeRequired", pendingPasswordChange,
                "hasPassword", credential != null && Integer.valueOf(1).equals(credential.getEnabled()),
                "superAdmin", superAdmin), "roles", assignedRoles,
                "permissions", pendingPasswordChange ? List.of() : visibleMenus.stream().map(MenuEntity::getPermissionCode).filter(Objects::nonNull).toList(),
                "menus", visibleMenus, "routes", routeCatalog()));
    }

    /**
     * 全站已配置的页面路由目录，只含 routePath 与菜单名，不含任何权限信息。
     * <p>
     * 前端需要它来区分两种"打不开"：路径存在但我没被授权（403），以及路径压根没配过（404）。
     * 这不算新增的信息泄露：传统静态路由方案里，整张路由表本来就随前端 JS 包公开给所有人。
     */
    private List<Map<String, Object>> routeCatalog() {
        return menus.selectList(new LambdaQueryWrapper<MenuEntity>()
                        .eq(MenuEntity::getMenuType, "MENU")
                        .eq(MenuEntity::getStatus, 1)
                        .orderByAsc(MenuEntity::getId))
                .stream()
                .filter(menu -> menu.getRoutePath() != null && !menu.getRoutePath().isBlank())
                .map(menu -> Map.<String, Object>of("routePath", menu.getRoutePath(), "menuName", menu.getMenuName()))
                .toList();
    }

    private String extractSid(String token) {
        return jwt.parse(token).get("sid", String.class);
    }

}
