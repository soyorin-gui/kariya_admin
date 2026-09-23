package org.lbl.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

/**
 * 安全相关配置。
 *
 * @param jwtSecret             签发 access token 的 HMAC 密钥
 * @param accessTokenMinutes    access token 有效期（分钟）
 * @param idleHours             非"记住我"会话的空闲超时（小时）
 * @param rememberedIdleDays    "记住我"会话的空闲超时（天）
 * @param rememberedAbsoluteDays "记住我"会话的绝对上限（天），持续操作也不得超过
 * @param secureCookie          是否为刷新 Cookie 附加 Secure 属性。本地 http 开发必须为 false，
 *                              否则浏览器直接丢弃 Cookie（表现为"能登录、一刷新就掉登录态"）；
 *                              生产 https 必须为 true。
 * @param corsAllowedOrigins    允许跨域访问的来源，逗号分隔。空表示不放行任何跨域来源
 *                              （同源部署时前端与后端同域，本来就不需要 CORS）。
 * @param trustedProxies        可信反向代理的地址，逗号分隔，支持 CIDR（如 10.0.0.0/8）。
 *                              只有来自这些地址的请求，其 X-Forwarded-For 才会被采信；
 *                              留空则完全不看转发头，直接用 TCP 源地址。见 {@code TrustedProxyResolver}。
 */
@ConfigurationProperties(prefix = "lbl.security")
public record SecurityProperties(String jwtSecret, long accessTokenMinutes, long idleHours, long rememberedIdleDays,
                                 long rememberedAbsoluteDays, boolean secureCookie, String corsAllowedOrigins,
                                 String trustedProxies) {

    /** 解析成列表；配置缺失或为空时返回空列表，调用方需自行决定"空"的含义。 */
    public List<String> corsAllowedOriginsList() {
        return split(corsAllowedOrigins);
    }

    public List<String> trustedProxiesList() {
        return split(trustedProxies);
    }

    private static List<String> split(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }
}
