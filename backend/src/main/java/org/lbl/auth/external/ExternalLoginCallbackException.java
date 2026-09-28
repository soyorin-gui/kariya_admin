package org.lbl.auth.external;

import org.lbl.common.exception.BusinessException;

/**
 * 外部认证已经确认属于“给当前账号绑定身份”的流程，但在回调中被业务规则拒绝。
 *
 * <p>它不是新的错误类型，而是把安全的站内返回地址带回 Controller：绑定失败不能像匿名登录
 * 失败那样把用户送去登录页，否则看起来就像现有会话被踢掉了。</p>
 */
public class ExternalLoginCallbackException extends BusinessException {
    private final String returnTo;

    public ExternalLoginCallbackException(String message, String returnTo) {
        super(message);
        this.returnTo = returnTo;
    }

    public String getReturnTo() {
        return returnTo;
    }
}
