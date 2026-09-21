package org.kariya.security.filter;

import io.jsonwebtoken.Claims;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.kariya.auth.session.*;
import org.kariya.security.jwt.JwtService;
import org.kariya.system.menu.MenuEntity;
import org.kariya.system.menu.MenuMapper;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final SessionService sessions;
    private final MenuMapper menus;

    public JwtAuthenticationFilter(JwtService jwt, SessionService sessions, MenuMapper menus) {
        this.jwt = jwt;
        this.sessions = sessions;
        this.menus = menus;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        String h = req.getHeader("Authorization");
        if (h != null && h.startsWith("Bearer ")) try {
            Claims c = jwt.parse(h.substring(7));
            String sid = c.get("sid", String.class);
            LoginSession s = sessions.find(sid);
            if (s != null && s.username().equals(c.getSubject()) && s.authVersion() == c.get("authVersion", Number.class).longValue()) {
                List<SimpleGrantedAuthority> authorities = menus.selectByUserId(s.userId()).stream()
                        .map(MenuEntity::getPermissionCode).filter(code -> code != null && !code.isBlank())
                        .map(SimpleGrantedAuthority::new).toList();
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(s.username(), null, authorities));
            }
        } catch (Exception ignored) {
        }
        chain.doFilter(req, res);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        return req.getRequestURI().startsWith("/api/auth/") || req.getRequestURI().startsWith("/swagger") || req.getRequestURI().startsWith("/v3/");
    }
}
