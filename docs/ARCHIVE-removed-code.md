# 归档：本次清理中删除的代码（可复原）

> **为什么需要这个文件**：被删掉的这几个文件/方法，**大部分在 git 里没有历史**（`backend/src/main/java/org/lbl/auth/external/` 整个目录从未被提交过 —— `git status` 显示为 `??`）。
> 一旦删除就**无法用 git 找回**。所以把源码原文留在这里，作为唯一的复原依据。

删除日期：本次清理
删除原因：它们都是"没有调用方"的代码。其中 3 个是**有意的接口预留**（端口-适配器模式的扩展点），按使用者要求清除。

---

## 一、后端预留接口（3 个文件，整文件删除）

### 1. `backend/src/main/java/org/lbl/auth/external/SamlIdentityVerifier.java`

**作用**：接入公司统一认证（UIAS / SAML）的扩展点。接口签名已设计好：实现类负责验签、Recipient 校验、有效期校验与重放校验。

```java
package org.lbl.auth.external;

import org.lbl.auth.identity.VerifiedIdentity;

/** UIAS starter 接入点：实现类负责验签、Recipient、有效期及重放校验。 */
public interface SamlIdentityVerifier {
    VerifiedIdentity verify(String providerKey, String samlResponse, String relayState);
}
```

### 2. `backend/src/main/java/org/lbl/auth/external/EnterpriseDirectoryPort.java`

**作用**：接入内网 ESB/ESF 人事系统、按工号查人员资料的预留端口。

```java
package org.lbl.auth.external;

import java.util.Map;
import java.util.Optional;

/** 内网 ESB/ESF 人员资料查询预留端口；当前版本不提供网络实现。 */
public interface EnterpriseDirectoryPort {
    Optional<EnterpriseProfile> findByEmployeeNo(String employeeNo);

    record EnterpriseProfile(String employeeNo, String displayName, String deptCode,
                             String employmentStatus, Map<String, Object> attributes) {
    }
}
```

### 3. `backend/src/main/java/org/lbl/auth/external/NoopEnterpriseDirectoryAdapter.java`

**作用**：上面那个端口的"未配置时的默认实现"，永远返回 `Optional.empty()`。注意它虽然带 `@Component`，但**没有任何类注入它**。

```java
package org.lbl.auth.external;

import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class NoopEnterpriseDirectoryAdapter implements EnterpriseDirectoryPort {
    @Override
    public Optional<EnterpriseProfile> findByEmployeeNo(String employeeNo) {
        return Optional.empty();
    }
}
```

---

## 二、后端冗余方法（4 处，已从原文件删除）

### 4. `SessionService.refreshToken()`（原 `auth/session/SessionService.java:185`）

**为什么删**：一行转发到 private 的 `token()`，**0 个调用方**。而且名字有误导性 —— 它不"刷新"任何东西，只是生成一个新的随机串。

```java
public String refreshToken() { return token(); }
```

### 5. `SessionService.create(...)` 四参重载（原 `auth/session/SessionService.java:36-38`）

**为什么删**：加了 `passwordChangeRequired` 参数后留下的旧签名。唯一调用点（`AuthService.login`）走的是五参版本。

```java
public String create(Long userId, String username, long authVersion, boolean rememberMe) {
    return create(userId, username, authVersion, rememberMe, false);
}
```

### 6. `UserRoleMapper.selectRoleIds(Long userId)`（原 `system/user/mapper/UserRoleMapper.java:11`）

**为什么删**：查用户的角色 id，但 `RoleMapper.selectByUserId` / `selectAssignedByUserId` 已覆盖同样的事，**0 个调用方**。

```java
@Select("SELECT role_id FROM sys_user_role WHERE user_id = #{userId}")
List<Long> selectRoleIds(Long userId);
```

### 7. `LoginAttemptGuard.requireSourceQuota(bucket, source, limit, window)` 四参重载（原 `auth/service/LoginAttemptGuard.java:127`）

**为什么删**：包私有重载，注释写着"便于测试"，但**项目里没有任何 `LoginAttemptGuard` 的测试**，只有 `:123` 内部调用。已合并进三参公开方法。

```java
/** 同上，但显式指定来源，便于测试。 */
void requireSourceQuota(String bucket, String source, int limit, Duration window) {
    String key = quotaKey(bucket, source);
    Long count = redis.opsForValue().increment(key);
    if (count != null && count == 1L) redis.expire(key, window);
    if (count != null && count > limit) {
        throw new TooManyRequestsException("操作过于频繁，请稍后再试");
    }
}
```

> 💡 **如果你以后想给限流写单测**：`LoginAttemptGuard.currentSource()` 依赖 `RequestInfo.rateLimitIp()` → 需要 servlet 请求上下文，所以那个"显式指定来源"的重载是唯一的可测缝。
> 复原方式：把上面的方法体恢复为独立重载，三参方法改为转发 `requireSourceQuota(bucket, currentSource(), limit, window)`。

### 8. `PasswordChangePolicy.appliesToPasswordLogin(authMethod, credential)`（原 `auth/service/PasswordChangePolicy.java:64-66`）

**为什么删**：**这个方法从未被调用过**。4 个调用点全部自己内联了同样的判断。

```java
/**
 * 本次会话是否应当被拦在系统外，直到改完密码。
 * <p>
 * {@code authMethod} 为 {@code "PASSWORD"} 表示这次会话是用账号密码换来的
 * （见 {@code SessionService#createMember}）；第三方登录写入的是 {@code "EXTERNAL"}。
 */
public boolean appliesToPasswordLogin(String authMethod, LocalCredentialEntity credential) {
    return "PASSWORD".equals(authMethod) && requiresChange(credential);
}
```

**⚠️ 这个不是普通的冗余，删它之前必须先理解下面这件事** —— 已把结论写进 `PasswordChangePolicy.requiresChange` 的 javadoc：

**「强制改密」这条规则无法用单一方法表达，因为 4 个调用点里有 2 个拿不到凭据：**

| 调用点 | 能用凭据吗 | 为什么 |
| --- | --- | --- |
| `AuthService.refresh` | ✅ 能 | 会话刷新时按需查 `sys_local_credential` |
| `AuthService.me`（`/auth/me`） | ❌ 不能 | **必须读会话快照** `session.passwordChangeRequired()`，否则业务请求途中会因凭据变化而突然掉权限 |
| `JwtAuthenticationFilter` | ❌ 不能 | 同上（每个请求都读库会拖慢过滤器） |
| `RegistrationService.issue` | ✅ 能 | 会话刚创建，刚查过凭据 |

所以 `appliesToPasswordLogin` 永远无法成为唯一入口 —— 保留它只会给人一种"改这里就能改全局"的错误印象。删掉它、把规则与 4 个调用点写进 `requiresChange` 的 javadoc，才是诚实的做法。

---

## 三、删除后仍然保留的 SAML 相关代码（**未删，说明原因**）

删除 `SamlIdentityVerifier` 后，`ExternalLoginService` 里还有两处对 SAML 的**运行时拦截**：

```java
// isProviderUsable(): 判定"这个登录方式能不能用"
if ("SAML".equalsIgnoreCase(provider.getProtocol())) return false;

// begin(): 发起登录时直接拒绝
if ("SAML".equalsIgnoreCase(provider.getProtocol())) {
    throw new BusinessException("系统不支持 SAML 协议登录");
}
```

**保留原因**：它们判定的是**配置里的 `protocol: SAML` 字符串**，与那个接口无关。保留它们意味着"即使有人在 `application-local.yml` 里把某个 provider 的 protocol 改成 SAML 并启用，也不会走进一条没有实现的路径"。**这是安全网，不是死代码。**

同理保留 `application-local.yml` 里那段 `enabled: false` 的 `uias` provider 配置 —— 它让 `providers` 列表稳定（登录页会显示一个灰色的"内部统一认证"按钮），也留下了"当初考虑过 UIAS"这条线索。

**如果将来真要接 UIAS，需要做的**（详见 `THIRD-PARTY-LOGIN.md` 第 3.5 节）：
1. 恢复上面的接口或重新设计；
2. 写一个真正的验签实现（XML 签名、Recipient、Audience、NotBefore/NotOnOrAfter、重放缓存）；
3. 加一个 **HTTP POST 的 ACS 端点**接收 `SAMLResponse`（现在只有 GET 的 `/start` 和 `/callback`）；
4. 在 `begin()` 里放宽必填校验（`:108-112` 要求 token-uri / user-info-uri / client-secret 全部非空，而 SAML 没有这些概念）。
