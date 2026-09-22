package org.kariya.common.exception;

/**
 * 认证已失效：访问令牌过期、Redis 会话已被清理、账号被停用或删除。
 * <p>
 * 与 {@link BusinessException}（业务规则拒绝，HTTP 400）区分开：本异常统一映射为 HTTP 401，
 * 前端拦截器依赖该状态码触发静默续期，续期失败才跳转登录页；若沿用 400，
 * 前端无法区分"参数/业务错误"与"登录已失效"，也就无法自动恢复。
 */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
