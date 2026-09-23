package org.lbl.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.lbl.security.filter.JwtAuthenticationFilter;
import org.lbl.security.handler.SecurityResponseWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class WebSecurityConfig {
    private static final Logger log = LoggerFactory.getLogger(WebSecurityConfig.class);

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http, JwtAuthenticationFilter jwt, ObjectMapper json) throws Exception {
        return http
                .csrf(c -> c.disable())
                .cors(c -> {
                })
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        // 只放行认证入口。springdoc/swagger 依赖已移除，原先的 /swagger-ui/** 与
                        // /v3/api-docs/** 放行规则一并删掉——安全配置应当准确反映"什么是对外公开的"，
                        // 留着已不存在的路径放行，会在将来重新引入文档依赖时悄悄把整个接口面暴露出去。
                        // 若以后重新引入 springdoc，记得同时补回这两条放行规则。
                        .requestMatchers("/api/auth/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        // 未携带令牌 / 令牌无效 / 会话已失效 → 401：前端拦截器据此静默续期，续期失败才跳登录页。
                        .authenticationEntryPoint((request, response, ex) ->
                                SecurityResponseWriter.write(response, json, HttpServletResponse.SC_UNAUTHORIZED, "登录状态已失效，请重新登录"))
                        // 已认证但缺少所需权限 → 403：前端据此展示"无权限"提示，且不应触发续期。
                        .accessDeniedHandler((request, response, ex) ->
                                SecurityResponseWriter.write(response, json, HttpServletResponse.SC_FORBIDDEN, "没有访问该资源的权限")))
                .addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * 跨域来源改为配置项，不再写死 localhost。
     * <p>
     * 之前硬编码了 http://localhost:5173 与 http://127.0.0.1:5173，于是非本地部署
     * （前端与后端不同源时）所有带 Authorization 头的请求都会在 preflight 阶段被拒，
     * 表现为"接口全 403/失败，但后端日志里什么都没有"。而 allowCredentials(true)
     * 又要求来源必须精确匹配、不能用通配符，所以这项配置必须显式维护。
     * <p>
     * 留空表示不放行任何跨域来源：同源部署（Nginx 把前端静态文件和 /api 放在同一个域下）
     * 本来就不走 CORS，空值是安全且可用的默认。
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(SecurityProperties security) {
        List<String> origins = security.corsAllowedOriginsList();
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOrigins(origins);
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("*"));
        c.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource s = new UrlBasedCorsConfigurationSource();
        if (origins.isEmpty()) {
            log.info("No CORS origins configured; cross-origin browser requests will be rejected. "
                    + "Set lbl.security.cors-allowed-origins when the frontend is served from another origin.");
        } else {
            log.info("CORS allowed origins: {}", origins);
        }
        s.registerCorsConfiguration("/**", c);
        return s;
    }
}
