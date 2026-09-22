package org.kariya.auth.controller;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.kariya.auth.model.*;
import org.kariya.auth.service.AuthService;
import org.kariya.auth.session.*;
import org.kariya.common.exception.UnauthorizedException;
import org.kariya.common.result.Result;
import org.kariya.security.jwt.JwtService;
import org.kariya.system.menu.*;
import org.kariya.system.role.*;
import org.kariya.system.user.UserEntity;
import org.kariya.system.user.UserMapper;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final String REFRESH_COOKIE = "kariya_refresh";
    private final AuthService auth;
    private final SessionService sessions;
    private final RoleMapper roles;
    private final MenuMapper menus;
    private final UserMapper users;
    private final JwtService jwt;

    public AuthController(AuthService auth, SessionService sessions, RoleMapper roles, MenuMapper menus, UserMapper users, JwtService jwt) {
        this.auth = auth;
        this.sessions = sessions;
        this.roles = roles;
        this.menus = menus;
        this.users = users;
        this.jwt = jwt;
    }

    @PostMapping("/login")
    public Result<LoginResult> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        LoginResult result = auth.login(request);
        String sid = extractSid(result.accessToken());
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, sid).httpOnly(true).sameSite("Lax").path("/api/auth").maxAge(request.rememberMe() ? Duration.ofDays(7) : Duration.ofSeconds(-1)).build();
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
        response.addHeader("Set-Cookie", ResponseCookie.from(REFRESH_COOKIE, "").httpOnly(true).path("/api/auth").maxAge(0).build().toString());
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
        return Result.ok(Map.of("user", Map.of("id", user.getId(), "username", user.getUsername(), "realName", user.getRealName(),
                "passwordChangeRequired", pendingPasswordChange,
                "superAdmin", assignedRoles.stream().anyMatch(role -> "super_admin".equals(role.getRoleCode()))), "roles", assignedRoles,
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
