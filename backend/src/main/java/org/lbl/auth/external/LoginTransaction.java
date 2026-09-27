package org.lbl.auth.external;

/**
 * 一次外部登录的"事务"：发起授权时写入 Redis（{@code auth:external:transaction:{state}}），
 * 回调时按 state 取回并<b>立刻删除</b>（一次性消费）。
 * <p>
 * 单独成类而不是继续做 {@code ExternalLoginService} 的私有内部类，原因很实际：
 * 持久化那一侧（{@link ExternalLoginPersistence}）需要在事务边界之外拿到它，
 * 而它本身就是"这次外部登录的全部上下文"，值得有自己的名字。
 * <p>
 * 注意它是直接序列化成 JSON 存进 Redis 的，因此<b>字段名就是存储格式</b>：
 * 改名会导致升级瞬间所有在途登录事务反序列化失败（表现为"外部登录状态已损坏"）。
 *
 * @param providerKey  发起时用的登录方式
 * @param verifier     PKCE 的 code_verifier，未启用 PKCE 时为空串
 * @param redirectUri  发起时使用的回调地址，换取令牌时必须原样回传
 * @param returnTo     登录成功（或绑定成功）后前端要跳回的站内路径
 * @param rememberMe   是否"记住我"。绑定流程固定为 false，真正生效的是 {@code targetUserId} 那条分支
 * @param targetUserId 非空表示这是一次<b>绑定</b>（把外部身份绑到该用户），为空表示<b>登录</b>
 * @param initiatingSid 发起绑定时的原会话；登录流程为空
 */
public record LoginTransaction(String providerKey, String verifier, String redirectUri, String returnTo,
                               boolean rememberMe, Long targetUserId, String initiatingSid) {
}
