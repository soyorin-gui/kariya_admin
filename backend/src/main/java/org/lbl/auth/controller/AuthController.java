package org.lbl.auth.controller;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.lbl.auth.model.*;
import org.lbl.auth.service.AuthService;
import org.lbl.auth.session.*;
import org.lbl.common.exception.UnauthorizedException;
import org.lbl.common.result.Result;
import org.lbl.config.SecurityProperties;
import org.lbl.security.context.AccessPolicy;
import org.lbl.security.jwt.JwtService;
import org.lbl.system.menu.entity.MenuEntity;
import org.lbl.system.menu.mapper.MenuMapper;
import org.lbl.system.role.entity.RoleEntity;
import org.lbl.system.role.mapper.RoleMapper;
import org.lbl.system.user.entity.UserEntity;
import org.lbl.system.user.mapper.UserMapper;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final String REFRESH_COOKIE = "lbl_refresh";
    /** Cookie 的 Path 限定在认证接口下：业务请求不会携带它，缩小暴露面。 */
    private static final String REFRESH_COOKIE_PATH = "/api/auth";
    /**
     * SameSite 用 Lax 而不是 None：refresh/touch/logout 都是 POST，跨站请求不会带上这个 Cookie，
     * 相当于免费拿到一层 CSRF 防护。代价是本地开发用 127.0.0.1 访问、API 却指向 localhost 时
     * 会被判定为跨站而丢 Cookie —— 统一用 localhost 访问即可（或给 vite 配 proxy）。
     */
    private static final String REFRESH_COOKIE_SAME_SITE = "Lax";
    private final AuthService auth;
    private final SessionService sessions;
    private final RoleMapper roles;
    private final MenuMapper menus;
    private final UserMapper users;
    private final JwtService jwt;
    private final boolean secureCookie;

    public AuthController(AuthService auth, SessionService sessions, RoleMapper roles, MenuMapper menus, UserMapper users,
                          JwtService jwt, SecurityProperties security) {
        this.auth = auth;
        this.sessions = sessions;
        this.roles = roles;
        this.menus = menus;
        this.users = users;
        this.jwt = jwt;
        this.secureCookie = security.secureCookie();
    }

    @PostMapping("/login")
    public Result<LoginResult> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        LoginResult result = auth.login(request);
        String sid = extractSid(result.accessToken());
        ResponseCookie cookie = refreshCookie(sid, request.rememberMe() ? Duration.ofDays(14) : Duration.ofSeconds(-1));
        response.addHeader("Set-Cookie", cookie.toString());
        return Result.ok(result, "登录成功");
    }

    @PostMapping("/refresh")
    public Result<Map<String, String>> refresh(@CookieValue(value = REFRESH_COOKIE, required = false) String sid) {
        if (sid == null) throw new UnauthorizedException("登录状态已失效");
        return Result.ok(Map.of("accessToken", auth.refresh(sid)));
    }

    @PostMapping("/touch")
    public Result<Void> touch(@CookieValue(value = REFRESH_COOKIE, required = false) String sid) {
        if (sid == null) throw new UnauthorizedException("登录状态已失效");
        auth.touch(sid);
        return Result.ok(null);
    }

    @PostMapping("/logout")
    public Result<Void> logout(@CookieValue(value = REFRESH_COOKIE, required = false) String sid, HttpServletResponse response) {
        auth.logout(sid);
        // 清除时属性必须与签发时一致（path / secure / sameSite），否则浏览器会当成另一个 Cookie，
        // 结果是"点了退出但凭据还留着"。
        response.addHeader("Set-Cookie", refreshCookie("", Duration.ofSeconds(0)).toString());
        return Result.ok(null);
    }

    @GetMapping("/me")
    public Result<Map<String, Object>> me(@CookieValue(value = REFRESH_COOKIE, required = false) String sid) {
        if (sid == null) throw new UnauthorizedException("登录状态已失效");
        LoginSession s = sessions.find(sid);
        if (s == null) throw new UnauthorizedException("登录状态已失效");
        UserEntity user = users.selectById(s.userId());
        if (user == null || user.getStatus() != 1 || !user.getAuthVersion().equals(s.authVersion())) throw new UnauthorizedException("登录状态已失效");
        boolean pendingPasswordChange = user.getPasswordChangeRequired() == 1;
        List<RoleEntity> assignedRoles = roles.selectByUserId(s.userId());
        // 待改密的账号必须是"什么都看不到"的状态：此前只清空了 permissions，menus 仍返回全量，
        // 于是首登用户的侧边栏是完整的，但每个请求都会因为 authorities 为空被 403 —— 前端菜单和
        // 后端权限对不上。两个字段必须同进同退。
        List<MenuEntity> visibleMenus = pendingPasswordChange ? List.of() : menus.selectByUserId(s.userId());
        // superAdmin 与后端其它地方同一口径：必须角色 status=1 才算（见 AccessPolicy.isSuperAdmin）。
        // 这里用"含停用角色"的查询，再用统一判定过滤，避免"停用的 super_admin 仍被前端当成超管"。
        boolean superAdmin = AccessPolicy.containsSuperAdmin(roles.selectAssignedByUserId(s.userId()));
        return Result.ok(Map.of("user", Map.of("id", user.getId(), "username", user.getUsername(), "realName", user.getRealName(),
                "passwordChangeRequired", pendingPasswordChange,
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

    /**
     * 签发/清除刷新 Cookie 的唯一出口。
     * <p>
     * 抽成一个方法的原因：登录与退出必须产生属性完全一致的 Cookie。此前退出路径少写了
     * sameSite，HttpOnly + Path 虽然对得上、浏览器大多也能删掉，但属性不一致属于随时会
     * 变成"退不掉"的隐患，靠两处手写同步迟早会漏。
     * <p>
     * Secure 由 {@code lbl.security.secure-cookie} 决定：本地 http 开发必须为 false，
     * 否则浏览器直接丢弃 Cookie；生产 https 必须为 true，避免凭据明文上网。
     */
    private ResponseCookie refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite(REFRESH_COOKIE_SAME_SITE)
                .path(REFRESH_COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }
}
