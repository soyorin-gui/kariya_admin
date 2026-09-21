package org.kariya.auth.controller;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import org.kariya.auth.model.*;
import org.kariya.auth.service.AuthService;
import org.kariya.auth.session.*;
import org.kariya.common.exception.BusinessException;
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
        if (sid == null) throw new BusinessException("登录状态已失效");
        return Result.ok(Map.of("accessToken", auth.refresh(sid)));
    }

    @PostMapping("/touch")
    public Result<Void> touch(@CookieValue(value = REFRESH_COOKIE, required = false) String sid) {
        if (sid == null) throw new BusinessException("登录状态已失效");
        auth.touch(sid);
        return Result.ok(null);
    }

    @PostMapping("/logout")
    public Result<Void> logout(@CookieValue(value = REFRESH_COOKIE, required = false) String sid, HttpServletResponse response) {
        if (sid != null) sessions.remove(sid);
        response.addHeader("Set-Cookie", ResponseCookie.from(REFRESH_COOKIE, "").httpOnly(true).path("/api/auth").maxAge(0).build().toString());
        return Result.ok(null);
    }

    @GetMapping("/me")
    public Result<Map<String, Object>> me(@CookieValue(value = REFRESH_COOKIE, required = false) String sid) {
        if (sid == null) throw new BusinessException("登录状态已失效");
        LoginSession s = sessions.find(sid);
        if (s == null) throw new BusinessException("登录状态已失效");
        UserEntity user = users.selectById(s.userId());
        if (user == null || user.getStatus() != 1) throw new BusinessException("登录状态已失效");
        List<RoleEntity> r = roles.selectByUserId(s.userId());
        List<MenuEntity> m = menus.selectByUserId(s.userId());
        return Result.ok(Map.of("user", Map.of("id", user.getId(), "username", user.getUsername(), "realName", user.getRealName()), "roles", r, "permissions", m.stream().map(MenuEntity::getPermissionCode).filter(Objects::nonNull).toList(), "menus", m));
    }

    private String extractSid(String token) {
        return jwt.parse(token).get("sid", String.class);
    }
}
