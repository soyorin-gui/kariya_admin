package org.kariya.system.log;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

/**
 * 从当前请求上下文提取审计字段，供登录日志与操作日志共用。
 * <p>
 * 之所以不把 HttpServletRequest 一路透传进 Service：登录日志是在 AuthService 里写的，
 * 为了拿一个 IP 而给业务方法加一个 Servlet 参数，会把 Web 层细节渗进业务层。
 */
public final class RequestInfo {
    /** 与 sys_login_log.login_ip / request_ip 的 VARCHAR(64) 对齐。 */
    private static final int IP_MAX_LENGTH = 64;
    /** 与 sys_login_log.user_agent 的 VARCHAR(500) 对齐。 */
    private static final int USER_AGENT_MAX_LENGTH = 500;
    /** 反向代理透传的客户端 IP 头，按优先级取第一个有效值。 */
    private static final List<String> IP_HEADERS = List.of("X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP");

    private RequestInfo() {
    }

    /**
     * 客户端 IP：优先取代理透传头（部署在 Nginx 后面时 remoteAddr 只会是网关地址），
     * 取不到再退回 TCP 源地址。X-Forwarded-For 可能是逗号分隔的链路，取第一段即真实客户端。
     */
    public static String clientIp() {
        HttpServletRequest request = currentRequest();
        if (request == null) return null;
        for (String header : IP_HEADERS) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank() && !"unknown".equalsIgnoreCase(value)) {
                return truncate(value.split(",")[0], IP_MAX_LENGTH);
            }
        }
        return truncate(request.getRemoteAddr(), IP_MAX_LENGTH);
    }

    public static String userAgent() {
        HttpServletRequest request = currentRequest();
        return request == null ? null : truncate(request.getHeader("User-Agent"), USER_AGENT_MAX_LENGTH);
    }

    private static HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        return attributes instanceof ServletRequestAttributes servlet ? servlet.getRequest() : null;
    }

    /** 超长即截断：日志写入不能因为一个超长 User-Agent 就抛 SQL 异常。 */
    private static String truncate(String value, int max) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
