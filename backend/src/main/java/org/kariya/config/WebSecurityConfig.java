package org.kariya.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.kariya.security.filter.JwtAuthenticationFilter;
import org.kariya.security.handler.SecurityResponseWriter;
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

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOrigins(List.of(
                "http://localhost:5173",
                "http://127.0.0.1:5173"
        ));
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("*"));
        c.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource s = new UrlBasedCorsConfigurationSource();
        s.registerCorsConfiguration("/**", c);
        return s;
    }
}
