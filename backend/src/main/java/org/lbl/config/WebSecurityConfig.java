package org.lbl.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
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
                        // SSE 等异步请求在完成或发生异常时，会由 Servlet 容器以内部分派再次经过安全链。
                        // 此时响应通常已经提交，并且这次分派不应被当成一条新的外部请求重新鉴权；
                        // 否则 AuthorizationFilter 的拒绝结果无法再写入响应，最终产生
                        // "response is already committed"。这里只放行容器控制的分派类型，客户端直接
                        // 请求同一路径时仍是 REQUEST 类型，仍会执行下面的 JWT 与权限校验。
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        // 供容器编排与负载均衡器探测。详情已在 management 配置中关闭，避免泄露依赖信息。
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        // 开户确认接口必须排在下面的认证入口通配规则之前：它需要临时认证态。
                        // 而这里的"认证"就是刷新 Cookie 换来的那个访问令牌。
                        //
                        // 为什么非要有这一条：这两个接口本来是靠 @PreAuthorize 兜底的，
                        // 而方法级权限拒绝抛的是 AccessDeniedException，被 GlobalExceptionHandler
                        // 统一转成 403。前端的静默续期只在 401 时才触发（见 utils/request.ts），
                        // 于是访问令牌一过期（15 分钟），用户点"创建账号/绑定账号"只会看到
                        // "没有访问该资源的权限"，既不续期也不跳登录页，整个页面卡死。
                        // 让它在"未认证"时走认证入口点返回 401，续期链路才能正常接上。
                        .requestMatchers("/api/auth/onboarding/**").authenticated()
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
