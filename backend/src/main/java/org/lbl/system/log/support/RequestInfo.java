package org.lbl.system.log.support;

import jakarta.servlet.http.HttpServletRequest;
import org.lbl.security.proxy.TrustedProxyResolver;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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
    /** 未配置可信代理时，无法确定来源的占位值（限流 key 需要一个稳定字符串）。 */
    private static final String UNKNOWN_IP = "unknown";

    /**
     * 静态工具类持有 IoC 容器里的解析器。
     * <p>
     * 这里确实打破了"静态工具类不该依赖 Bean"的一般原则，但成因是 {@link RequestInfo}
     * 被设计成静态调用（见类注释），而 IP 推导现在需要配置（可信代理列表）。
     * 备选方案是在每个调用点注入解析器，那会把 Web 细节扩散到更多 Service；
     * 或者把解析逻辑做成纯静态 + 静态配置，那会让配置注入变得别扭。
     * 折中做法：解析器仍由 Spring 管理（可注入、可测试），这里只留一个由它自己在
     * 构造时写入的引用；未初始化时所有调用安全退化为"只看 TCP 源地址"。
     */
    private static volatile TrustedProxyResolver resolver;

    private RequestInfo() {
    }

    /** 由 {@link TrustedProxyResolver} 在构造时调用，不要在业务代码里调用。 */
    public static void useResolver(TrustedProxyResolver trustedProxyResolver) {
        resolver = trustedProxyResolver;
    }

    /**
     * 真实客户端 IP：审计日志与登录限流都用它，两者必须一致。
     * <p>
     * 是否采信 {@code X-Forwarded-For} / {@code Forwarded} 由 {@code lbl.security.trusted-proxies}
     * 决定 —— 只有请求确实来自可信代理时才会解析转发链（见 {@link TrustedProxyResolver}）。
     * 未配置可信代理时等价于"直连"，返回 TCP 对端地址。
     */
    public static String clientIp() {
        HttpServletRequest request = currentRequest();
        if (request == null) return null;
        TrustedProxyResolver current = resolver;
        if (current == null) return truncate(request.getRemoteAddr(), IP_MAX_LENGTH);
        return truncate(current.resolve(request.getRemoteAddr(),
                request.getHeader("X-Forwarded-For"), request.getHeader("Forwarded")), IP_MAX_LENGTH);
    }

    /**
     * 用于安全限流的来源标识。
     * <p>
     * 与 {@link #clientIp()} 同源：以前这里刻意只取 TCP 地址以保证不可伪造，代价是
     * 反向代理部署下全站共享同一个限流 key（一个人失败 20 次锁住所有人）。现在改成
     * "仅当来源是可信代理时才解析转发链"，既保留了不可伪造性，又能在代理后正确区分用户；
     * 拿不到地址时返回 {@code "unknown"} 而不是 null，避免限流 key 退化成同一个空值。
     */
    public static String rateLimitIp() {
        String ip = clientIp();
        return ip == null || ip.isBlank() ? UNKNOWN_IP : ip;
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
