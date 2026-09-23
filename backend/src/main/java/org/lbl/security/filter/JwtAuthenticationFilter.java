package org.lbl.security.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.lbl.auth.session.*;
import org.lbl.security.context.CurrentUser;
import org.lbl.security.jwt.JwtService;
import org.lbl.system.menu.entity.MenuEntity;
import org.lbl.system.menu.mapper.MenuMapper;
import org.lbl.system.user.entity.UserEntity;
import org.lbl.system.user.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private final JwtService jwt;
    private final SessionService sessions;
    private final MenuMapper menus;
    private final UserMapper users;

    public JwtAuthenticationFilter(JwtService jwt, SessionService sessions, MenuMapper menus, UserMapper users) {
        this.jwt = jwt;
        this.sessions = sessions;
        this.menus = menus;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        String h = req.getHeader("Authorization");
        if (h != null && h.startsWith("Bearer ")) try {
            Claims c = jwt.parse(h.substring(7));
            String sid = c.get("sid", String.class);
            LoginSession s = sessions.find(sid);
            UserEntity user = s == null ? null : users.selectById(s.userId());
            if (user != null && user.getStatus() == 1 && user.getAuthVersion().equals(s.authVersion())
                    && s.username().equals(c.getSubject()) && s.authVersion() == c.get("authVersion", Number.class).longValue()) {
                List<SimpleGrantedAuthority> authorities = (user.getPasswordChangeRequired() == 1 ? List.<MenuEntity>of() : menus.selectByUserId(s.userId())).stream()
                        .map(MenuEntity::getPermissionCode).filter(code -> code != null && !code.isBlank())
                        .map(SimpleGrantedAuthority::new).toList();
                CurrentUser principal = new CurrentUser(user.getId(), user.getUsername(), user.getDeptId());
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, authorities));
            }
        } catch (JwtException | IllegalArgumentException ex) {
            // 令牌过期/被篡改：属于正常的"未认证"场景，交给 SecurityFilterChain 输出 401，前端会尝试静默续期。
            log.debug("Bearer token rejected: {}", ex.getMessage());
        } catch (Exception ex) {
            // 例如 Redis 不可用、会话数据损坏。这里继续放行会让请求以未认证身份走到 401，
            // 若不留下日志就很难和"令牌过期"区分开，因此必须告警。
            log.warn("Unable to resolve bearer token, request continues unauthenticated", ex);
        }
        chain.doFilter(req, res);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        return req.getRequestURI().startsWith("/api/auth/") || req.getRequestURI().startsWith("/swagger") || req.getRequestURI().startsWith("/v3/");
    }
}
