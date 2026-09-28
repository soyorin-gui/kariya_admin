# UIAS 内部统一认证接入说明

当前代码已提供 UIAS 的流程编排，但**不携带公司内网 SDK 或 ESF 实现**。因此默认状态下“内部统一认证”入口保持禁用；只有开关、SDK 适配器和员工目录适配器同时具备时才会启用。

## 流程

`/api/auth/external/uias/start` 创建一个 Redis 一次性事务并跳转 UIAS；UIAS 回调
`/api/auth/external/uias/callback?state=...` 后，系统调用 SDK 取得唯一工号，再查询员工目录。

员工必须满足以下条件才会登录成功：

1. UIAS SDK 返回有效工号；
2. ESF/ESB 确认该员工在职；
3. 本地 `sys_user` 存在同一 `employee_no`、`employee_no_verified=1` 且账号启用。

不满足第 3 条时会拒绝登录，不会自动创建 `basic_role` 账号。管理员应先为员工预置工号、部门和角色。

## 配置

```bash
UIAS_LOGIN_ENABLED=true
UIAS_ENTRY_URL=https://uias.example/IDP/
UIAS_CALLBACK_URL=https://admin.example/api/auth/external/uias/callback
UIAS_ISSUER=company-uias
# 默认 ssotarget；若 SDK/IdP 使用其它参数名再覆盖
UIAS_TARGET_PARAMETER=ssotarget
```

这些环境变量均映射到同一处 `lbl.external-auth.providers.uias` 配置；不再有独立的 `lbl.uias` 开关或配置块。

UIAS 后台登记的回调地址应为 `UIAS_CALLBACK_URL`，并且必须保留其查询参数 `state`。SDK 自己的证书、`SAMLResponse` 参数名、Recipient 校验和断言有效期仍按 SDK 文档配置；生产环境必须开启 Recipient 校验。

## 需要在内网项目中实现的两个端口

`org.lbl.auth.uias.UiasAssertionConsumer`：注入 SDK 的 `AssertionConsumer`，调用
`consumer.consume(request)` 并返回工号。不要记录原始 SAMLResponse。

`org.lbl.auth.uias.EnterpriseDirectoryPort`：通过 ESF/ESB 按工号返回姓名、邮箱和在职状态。网络实现需设置连接/读取超时，且不得将内部响应原文透传给浏览器。

这两个 Bean 未实现时，UIAS 按钮会显示“暂未启用”，避免配置不完整时把用户送入无法完成的认证流程。
