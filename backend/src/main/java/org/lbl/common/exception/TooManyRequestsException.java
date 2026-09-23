package org.lbl.common.exception;

/**
 * 请求过于频繁：命中频率限制（登录失败次数、原密码错误次数等）。
 * <p>
 * 单独成一个异常类型，是为了能映射成 HTTP 429 而不是 400。前端对状态码有明确约定
 * （401 = 会话失效要续期、403 = 无权限、400 = 业务规则拒绝），而"被限流"三者都不是：
 * 它不是参数错误，重试也没用，必须等窗口过去。混进 400 会让前端和排障的人把
 * "你被限流了"误读成"你填错了"。
 */
public class TooManyRequestsException extends RuntimeException {
    public TooManyRequestsException(String message) {
        super(message);
    }
}
