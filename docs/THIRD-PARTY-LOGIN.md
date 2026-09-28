# 三方登录（GitHub / 微信 / Google / UIAS）真实可用性与启用步骤

> 你的问题：**"除去 uias，微信和 GitHub 的三方认证登录如果我开启配置是否可以使用了？如果不能，我还需要怎么做？"**
>
> **直接回答：**
>
> | 结论 | 说明 |
> | --- | --- |
> | **代码是完整的，不是空壳** | OAuth2 授权码流程写得很扎实，微信的各种坑（`appid`、GET 换 token、`#wechat_redirect`、userinfo 用查询参数传 `access_token`/`openid`）**都处理了**，GitHub 需要的 `Accept: application/json` **也设了** |
> | **但它从未被端到端验证过** | `auth/external/` 包**零测试**，你也确认从没试过 |
> | ❌ **只开配置一定不行** | 原因有三个（见第 1 节），其中两个是**配置位置问题**，一个是**外部平台的注册要求** |
> | ⚠️ **GitHub 大概率能跑通**（改完配置位置 + 补一个小遗漏） | 本地也能测 |
> | ❌ **微信在本机不可能跑通** | 微信开放平台强制要求 `redirect_uri` 域名已备案注册，`localhost` / 裸 IP 一律拒绝。见 3.3 |
> | ⚠️ **UIAS 已有接入骨架** | 流程、会话、工号匹配和本地 RBAC 已接通；仍需在内网实现 SDK 与 ESF 适配器，见 `UIAS-INTEGRATION.md` |
>
> 下面按"为什么不行 → 每个平台的具体情况 → 完整启用清单 → 验证步骤"展开。

---

## 一、为什么"只开配置"一定不行

### 1.1 【已修复】提供方目录此前被放进 `.gitignore` 文件

此前 `lbl.external-auth.providers` 仅存在于被忽略的 `application-local.yml`，新克隆或生产 profile 会得到空的 `providers` Map，因而登录页不会渲染任何三方入口。

现在提供方目录已恢复到受版本控制的 `backend/src/main/resources/application.yml`：GitHub、Google、微信和禁用的 UIAS 占位都会稳定存在；**所有 client id / client secret 仍只从环境变量读取，绝不提交到仓库**。因此新环境会显示禁用的入口，只有完整设置开关和凭据后才能使用。

**核对一下服务的判定逻辑**（`ExternalLoginService.java:76-83`）：

```java
public boolean isProviderUsable(String providerKey) {
    ExternalAuthProvider provider = properties.getProviders().get(providerKey);
    if (provider == null || !provider.isEnabled()) return false;
    if ("SAML".equalsIgnoreCase(provider.getProtocol())) return false;   // ★ SAML 永远 false
    return hasText(provider.getAuthorizationUri()) && hasText(provider.getTokenUri())
        && hasText(provider.getUserInfoUri()) && hasText(provider.getClientId())
        && hasText(provider.getClientSecret());                          // ★ 5 个都不能为空
}
```

**注意最终决定按钮是否"可用"的是这个 `isProviderUsable()`，不是 `enabled`。** 而 `enabled` 又是 `${GITHUB_LOGIN_ENABLED:false}` —— 所以现在连 `enabled` 都是 false。

> **前端表现**（`frontend/src/pages/login/index.tsx:61-69`、`:121-140`）：按钮**渲染出来但有 `is-disabled` 样式**，`onClick` 只弹 `message.info('GitHub登录尚未配置')`。`title` tooltip 会显示"GitHub（未启用）"。
> 所以"开了配置却没反应"的第一嫌疑永远是这段：**`isProviderUsable()` 任意一项为空 → 按钮就是禁用的，而且界面上不会告诉你缺哪一项。**

### 1.2 【致命】没有任何一个平台注册过应用

仓库里搜不到任何真实的 `client-id` / `client-secret`：

| 环境变量 | 当前位置 | 默认值 |
| --- | --- | --- |
| `GITHUB_CLIENT_ID` / `GITHUB_CLIENT_SECRET` | `application.yml` | 空 |
| `WECHAT_APP_ID` / `WECHAT_APP_SECRET` | `application.yml` | 空 |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | `application.yml` | 空 |

（这是**好事** —— 说明没把密钥提交进仓库。但也说明**没有可用凭据**。）

### 1.3 【外部约束】微信在本机/内网不可能跑通

微信开放平台（`open.weixin.qq.com`，注意**不是**公众平台）要求：

1. 必须有**已审核通过的"网站应用"**（个人开发者基本申请不下来，需要企业资质）；
2. `redirect_uri`（即回调地址）的**域名必须与后台登记的"授权回调域"完全一致**；
3. `localhost`、裸 IP、非标准端口一律被拒绝。

而本项目的回调地址是**推导出来的**（不是用户能传的，这点是对的）：

```java
// ExternalLoginService.java:392-394
private String callbackUri(String provider) {
    return properties.getBackendBaseUrl().replaceAll("/$", "") + "/api/auth/external/" + encode(provider) + "/callback";
}
```

默认 `backendBaseUrl = http://localhost:8080`（`ExternalAuthProperties.java:11`）→ 回调地址是 `http://localhost:8080/api/auth/external/wechat/callback` → **微信会直接返回"redirect_uri 参数错误"**。

**所以微信要验证，你必须有一个公网 HTTPS 域名**（内网域名不行，微信服务器要能访问到你的回调），把 `BACKEND_BASE_URL` 指向它，并在微信后台登记那个域名的授权回调域。**这在内网环境里基本做不到** —— 这也意味着**微信登录大概率不适合你的内网场景**。

---

## 二、完整流程（"点了 GitHub 按钮之后发生了什么"）

| # | 调用方 → 目标 | 端点 | 代码 |
| --- | --- | --- | --- |
| 1 | 浏览器 → 后端 | `GET /api/auth/external/providers` | `ExternalAuthController.java:37-40` |
| 2 | 浏览器（整页跳转） → 后端 | `GET /api/auth/external/github/start?returnTo=%2F&rememberMe=true` → **302** | `ExternalAuthController.java:42-48`（`beginLogin` 有 30 次/分钟的来源配额，`:42-43`、`:90`） |
| 3 | 浏览器 → GitHub | `GET https://github.com/login/oauth/authorize?client_id=…&redirect_uri=…&response_type=code&scope=read%3Auser+user%3Aemail&state=…&code_challenge=…&code_challenge_method=S256` | `ExternalLoginService.java:123-136` |
| 4 | GitHub → 后端 | `GET {BACKEND_BASE_URL}/api/auth/external/github/callback?code=…&state=…` | `ExternalAuthController.java:61-83` |
| 5 | 后端 → GitHub | `POST https://github.com/login/oauth/access_token`（表单：`client_id, client_secret, code, redirect_uri, grant_type, code_verifier`；头：`Content-Type: application/x-www-form-urlencoded`、**`Accept: application/json`**） | `ExternalLoginService.java:227-254` |
| 6 | 后端 → GitHub | `GET https://api.github.com/user`（头：`Accept: application/json`、`Authorization: Bearer …`） | `ExternalLoginService.java:262-283` |
| 7 | 后端 → Redis / MySQL | 建会话 / 建开户态 / 写绑定关系 | `SessionService`、`ExternalLoginPersistence.java:58-103` |
| 8 | 后端 → 浏览器 | **302** `{FRONTEND_BASE_URL}/auth/callback?returnTo=/`（或 `?error=…`） | `ExternalLoginService.java:396-401` |
| 9 | 浏览器 → 后端 | `POST /api/auth/refresh`（靠 httpOnly Cookie 换 accessToken） | `frontend/src/pages/auth/CallbackPage.tsx:25` |
| 10 | 浏览器 → 后端 | `GET /api/auth/me` | `CallbackPage.tsx:26` |
| 11a | 已绑定过的身份 | 直接进系统 | `CallbackPage.tsx:30` |
| 11b | 新身份（**ONBOARDING**） | 跳 `/account/setup`，二选一："创建账号" 或 "绑定已有账号" | `OnboardingAccountController.java:41-61` |

**要注册给平台的回调地址（必须完全一致，含协议、主机、端口、路径）**：

```
GitHub:  {BACKEND_BASE_URL}/api/auth/external/github/callback
微信:     {BACKEND_BASE_URL}/api/auth/external/wechat/callback
Google:  {BACKEND_BASE_URL}/api/auth/external/google/callback
```

> **注意回调指向的是后端，不是前端。** `{FRONTEND_BASE_URL}/auth/callback` 只是后端回调完成后再次 302 的落点，**不需要**在任何平台登记。

---

## 三、逐个平台的真实情况

### 3.1 抽象层：通用性很好，只有一处硬编码

整条流程是**数据驱动**的（`ExternalAuthProperties.Provider`），每个平台的怪癖都靠配置项表达：

| 平台怪癖 | 机制 | 行号 |
| --- | --- | --- |
| 用 `appid` 而不是 `client_id` | `client-id-parameter: appid`，授权和换 token 两处都用它 | `:124`、`:229` |
| 用 `secret` 而不是 `client_secret` | `client-secret-parameter: secret` | `:230` |
| 换 token 用 GET | `token-method: GET` 分支把表单拼成查询串 | `:238-244` |
| URL 要带 `#wechat_redirect` 片段 | `authorization-fragment: wechat_redirect` | `:135-136` |
| PKCE 开关（微信必须关掉） | `pkce-enabled: false` | `:129-132`、`:234` |
| userinfo 参数需要 `{access_token}` / `{openid}` 插值 | `expand()` 从**换 token 的响应**里取值 | `:265`、`:384-390` |
| 额外的授权/换 token 参数 | `authorization-parameters` / `token-parameters` 最后合并 | `:133`、`:235` |
| 支持嵌套的 claim 路径（`a.b.c`） | `claim()` 逐段下钻 | `:314-322` |
| 平台返回表单格式而不是 JSON | `parseBody()` 回退到表单解码 | `:303-312` |
| 平台用 **HTTP 200 + `errcode`** 报错（微信） | `describeProviderError()` 同时识别 `error` 和 `errcode` | `:335-341` |

**唯一的硬编码**在 `ExternalLoginService.java:209`：

```java
String subject = claim(profile, provider.getSubjectClaim());
if (subject == null && "wechat".equals(providerKey)) subject = stringValue(tokenResponse.get("openid"));
```

它按**配置里的 key 字符串 `"wechat"`** 判断，而不是按"这个提供方声明了自己需要从 token 响应兜底 subject"。如果你把 key 改成 `weixin`（或接第二个微信应用），这个兜底会**静默消失** —— 表现为登录报"外部认证结果缺少稳定用户标识"，而真正的原因是 unionid 没取到。

> **建议改成配置项**：在 `ExternalAuthProperties.Provider` 里加 `subjectFallbackClaim`（例如微信配 `subject-fallback-claim: openid`），然后把 `:209` 改成读它。几十行改动，但消掉了"改名就静默坏掉"的隐患。
>
> 同样性质的硬编码还有 `"uias".equalsIgnoreCase(...)` 的路由分支；它目前只服务于一个明确的企业认证提供方。若未来要接入多个企业 IdP，应把它演进为按 provider 类型注册的适配器，而不是继续累积字符串判断。

**另外注意：`protocol: OIDC` 只是一个标签。** `getProtocol()` 只被用来拒绝 SAML（`:79`、`:105`）和做展示（`:71`）。代码里**没有任何 `id_token` 解析、没有 JWKS 验签、没有 `nonce`** —— Google 走的是"拿 access_token 调 userinfo 端点"。这在安全性上可以接受（userinfo 走 TLS 且由 access_token 认证），但它**不是真正的 OIDC**。

### 3.2 GitHub —— 大概率能跑通（改完配置位置即可）

| 检查项 | 结论 |
| --- | --- |
| **`Accept: application/json` 有没有设** | ✅ **设了**（`ExternalLoginService.java:245-246`）。GitHub 的 token 端点默认返回表单格式，但 `parseBody()` 也支持表单解码，所以双保险 |
| `subject-claim: id` 的数字/字符串一致性 | ✅ **没问题**。`claim()` 走 `String.valueOf(12345678)` → `"12345678"`，存进 `sys_external_identity.subject VARCHAR(255)`，查询也传同样的字符串形式（`ExternalIdentityMapper.find`），无类型不一致 |
| **`/user/emails` 兜底** | ❌ **没有**。`userInfoUri` 只有一个 URI，`userInfo()` 只发**一次**请求，全项目没有任何地方调 `/user/emails`。GitHub 用户在"邮箱设为私密"时 `/user` 返回 `email: null` → 邮箱快照为空、开户表单预填为空 |
| PKCE | ⚠️ **默认开着**（`ExternalAuthProperties.java:41` 的 `pkceEnabled = true`，而 `github:` 配置块没有关掉它）→ 会发 `code_challenge` / `code_verifier`。GitHub 已支持 OAuth 应用的 PKCE，所以**理论上没问题，但未实测**。如果 GitHub 拒绝多余的 `code_verifier`，把 `github` 的 `pkce-enabled` 设为 `false` |
| `User-Agent` | ⚠️ GitHub 的 REST API 要求带 `User-Agent`。代码**没有显式设置**，依赖 JDK `HttpClient` 自带的默认值。属于隐式依赖，实测时留意 |
| 本地能否测试 | ✅ **能**。回调地址是 `http://localhost:8080/...`，GitHub 允许（微信不允许） |

**结论：GitHub 只要补上配置位置（第 1.1 节）+ 填 client id/secret，就应该能用。**
**建议顺手补上 `/user/emails` 兜底**（可选，只影响邮箱快照的完整度，不影响登录）：
```java
// 在 userInfo() 拿到 profile 后，如果 email 为空且 provider 是 GitHub，
// 再调一次 GET https://api.github.com/user/emails，
// 取其中 primary && verified == true 的那条的 email。
```
（这需要在 `Provider` 里加一个可选的 `emailFallbackUri` 配置，而不是再硬编码 `"github"`。）

### 3.3 微信 —— 逻辑正确，但**本机/内网跑不通**

**逐项核对（按微信的坑）**：

| 微信的要求 | 本项目的配置/代码 | 结论 |
| --- | --- | --- |
| 授权地址是 `https://open.weixin.qq.com/connect/qrconnect` | `application-local.yml:81` | ✅ |
| 参数名是 `appid` 不是 `client_id` | `client-id-parameter: appid`（`:86`） | ✅ |
| 需要 `response_type=code` | 代码硬编码（`:126`）+ 配置里也写了一份（`:94-95`） | ✅ 重复但无害（后者覆盖前者，值相同） |
| 需要 `scope=snsapi_login` | `scopes: [snsapi_login]`（`:91`） | ✅ |
| 必须发 `#wechat_redirect` 片段 | `authorization-fragment: wechat_redirect`（`:90`） | ✅ |
| **不能**带 PKCE 参数 | `pkce-enabled: false`（`:89`） | ✅ |
| 换 token 用 **GET** | `token-method: GET`（`:88`） | ✅ |
| 换 token 参数是 `appid`/`secret` | `client-secret-parameter: secret`（`:87`） | ✅ |
| **`openid` 在换 token 的响应里，不在 userinfo 里** | `expand()` 从 token 响应插值；`user-info-parameters.openid: '{openid}'`（`:103`） | ✅ **这是最容易错的一步，做对了** |
| userinfo 的 `access_token` 必须是**查询参数**（不是 Authorization 头） | `user-info-parameters.access_token: '{access_token}'`（`:102`），并且代码注释里明确写了"漏掉这一行时微信固定返回 41001" | ✅ |
| userinfo 需要 `lang` | `lang: zh_CN`（`:104`） | ✅ |
| 微信用 **HTTP 200 + errcode** 报错 | `describeProviderError()` 识别 `errcode`/`errmsg`（`:335-341`），并在换成 token 失败时把它写进日志（`:202-203`） | ✅ |
| `unionid` 只在开放平台账号绑定后才返回 | `subject-claim: unionid`（`:92`）+ `:209` 的 `openid` 兜底 | ⚠️ **能跑，但有个隐患，见下** |

**⚠️ 隐患：`subject` 会随着"是否绑定开放平台"而改变。**

- 现在配的是 `subject-claim: unionid`。用户**没绑定**开放平台账号时 userinfo 里没有 `unionid` → `claim()` 返回 null → `:209` 兜底用 `openid`。**第一次登录能成功。**
- 但将来（或换个应用）一旦拿到 `unionid`，存进 `sys_external_identity.subject` 的值就**变成了另一个字符串**。同一个人再次登录时：
  1. `identities.find(...)` 查不到（`ExternalLoginPersistence.java:79`）→ 又被推进开户确认流程；
  2. 他选"绑定已有账号" → 被 `RegistrationService.java:179-181` 拒绝：「该账号已经绑定了同类型的登录身份，请先解除原绑定」；
  3. 他选"创建新账号" → **产生重复账号**。
- 而 `openid` **从来没有被持久化**，所以事后无法对账/迁移。

**建议**：
- 方案一（最稳）：直接把 `subject-claim` 改成 `openid`。`openid` 对"同一个微信开放平台应用"是稳定的，满足"稳定用户标识"的要求。
- 方案二：保留 `unionid`，但**同时把 `openid` 也存下来**（给 `sys_external_identity` 加一列），并设计一个 unionid 出现时的升级/合并路径。
- 无论哪种，都把 `:209` 的 `"wechat".equals(providerKey)` 换成配置项。

**❌ 真正的阻碍：本机/内网测不了。**

微信强制要求 `redirect_uri` 域名与后台登记的**授权回调域**一致，`localhost`、裸 IP、非标准端口都会被拒（报 `redirect_uri 参数错误` 或"域名与后台配置不一致"）。

要验证微信，你必须：
1. 申请到**已审核通过的网站应用**（需要企业资质）；
2. 有一个**公网可访问的域名**（内网域名不行 —— 微信的服务器要能访问到你的回调地址）；
3. 把 `BACKEND_BASE_URL` 指向那个域名；
4. 在微信后台把"授权回调域"登记为那个域名（**不带协议、不带路径、不带端口**）。

**结论：微信登录对你的内网场景基本不适用。** 如果内网需要"扫码登录"，现实做法是接入公司自己的统一认证（也就是 UIAS 那条路），而不是微信。

**另外两个小注意点**：
- 微信昵称可能超过 128 字符 → `sys_external_identity.email_snapshot` / `display_name_snapshot` 都是 `VARCHAR(128)`（`init_schema.sql:66-67`），会插入失败。
- 开户表单里 `realName` 会用昵称预填（`frontend/src/pages/account/setup/index.tsx:72`），但**没有 `maxLength` 限制**，而 `sys_user.real_name` 是 `VARCHAR(64)`、后端 `OnboardingAccountRequest.java:15` 有长度校验 → 超长昵称会导致提交报错。

### 3.4 Google —— 配置在，但你没问；简单说一下

- `protocol: OIDC` 但走的是 userinfo 端点（见 3.1 末尾），**没有 id_token 验签、没有 nonce**。
- 配置项看起来是对的（`issuer: https://accounts.google.com`、`subject-claim: sub`、`scopes: [openid, profile, email]`）。
- **但国内网络环境下访问 `accounts.google.com` 和 `oauth2.googleapis.com` 基本不通**，所以实际价值有限。
- 想用的话和 GitHub 一样的步骤，加一个 `<BACKEND_BASE_URL>/api/auth/external/google/callback` 到 Google Cloud Console 的授权重定向 URI。

### 3.5 UIAS —— 流程骨架已完成，等待内网 SDK/ESF 适配器

`/api/auth/external/uias/start` 与 callback 已接入现有外部认证入口：它创建 Redis 一次性事务、跳转到配置的 UIAS 地址、消费 SDK 验证后的唯一工号、查询员工目录，并仅允许已预置且已验证工号的本地员工账号登录。

项目不携带公司 SDK 和 ESF 网络实现；内网部署只需实现 `UiasAssertionConsumer`（调用 SDK 的 `consumer.consume(request)`）和 `EnterpriseDirectoryPort`（返回在职状态、姓名、邮箱）。二者未提供时 UIAS 按钮保持禁用。详细配置、账号策略和实现边界见 `UIAS-INTEGRATION.md`。

---

## 四、完整启用清单（GitHub 为例，微信把 provider 换成 `wechat`）

### 步骤 1：确认版本库内的 provider 配置没有被 profile 覆盖

`application.yml` 已包含 `lbl.external-auth`，任何环境都能拿到提供方列表。不要再把同名的 `providers` 放入 `application-local.yml`、`application-dev.yml` 或 `application-prod.yml`：profile 文件的同名 Map 可能整体覆盖基础配置。

```yaml
# 已存在于 application.yml；仅作结构参考
lbl:
  external-auth:
    backend-base-url: ${BACKEND_BASE_URL:http://localhost:8080}
    frontend-base-url: ${FRONTEND_BASE_URL:http://localhost:5173}
    providers:
      github:
        enabled: ${GITHUB_LOGIN_ENABLED:false}
        display-name: GitHub
        icon: github
        protocol: OAUTH2
        authorization-uri: https://github.com/login/oauth/authorize
        token-uri: https://github.com/login/oauth/access_token
        user-info-uri: https://api.github.com/user
        client-id: ${GITHUB_CLIENT_ID:}
        client-secret: ${GITHUB_CLIENT_SECRET:}
        scopes: [read:user, user:email]
        subject-claim: id
        display-name-claim: login
        email-claim: email
```

客户端密钥只通过步骤 3 的环境变量注入。

### 步骤 2：注册应用并记下凭据

**GitHub**（Settings → Developer settings → OAuth Apps → New OAuth App）：

| 字段 | 填什么 |
| --- | --- |
| Application name | 你的系统名 |
| Homepage URL | `https://你的域名/` |
| **Authorization callback URL** | **`https://你的域名/api/auth/external/github/callback`**（本地测试用 `http://localhost:8080/api/auth/external/github/callback`） |

拿到 **Client ID** 和 **Client secret**。

> ⚠️ **回调地址必须完全一致**：含协议、主机、端口、路径。GitHub 严格匹配，差一个字符就是 `redirect_uri_mismatch`。
> 一个 OAuth App **只能登记一个**回调地址。如果需要同时支持本地和内网两套，就建两个 App。

**微信**（开放平台 → 网站应用，需审核）：

| 字段 | 填什么 |
| --- | --- |
| 授权回调域 | `你的域名`（**不带** `https://`、**不带**路径、**不带**端口） |

实际生效的回调是 `https://你的域名/api/auth/external/wechat/callback` —— 路径部分由代码决定，域必须匹配登记值。

### 步骤 3：设置环境变量

```bash
# GitHub
GITHUB_LOGIN_ENABLED=true
GITHUB_CLIENT_ID=<刚拿到的 Client ID>
GITHUB_CLIENT_SECRET=<刚拿到的 Client secret>

# 公网地址（微信必需；GitHub 本地测试时保持默认 localhost 即可）
BACKEND_BASE_URL=https://你的域名
FRONTEND_BASE_URL=https://你的域名

# ★ 如果前端和后端不同源，必须配 CORS
#   application-prod.yml 里这项默认是"空" = 不放行任何跨域
CORS_ALLOWED_ORIGINS=https://你的域名
```

如果运行后端的机器不能直连 GitHub / Google，浏览器里的代理并不会自动交给 Java 使用。此时设置一个支持 HTTP CONNECT 的代理：

```bash
EXTERNAL_AUTH_PROXY_HOST=127.0.0.1
EXTERNAL_AUTH_PROXY_PORT=7890
```

`7890` 只是常见本地代理的 HTTP/mixed 端口示例；请填实际的 HTTP 代理端口，不能填 SOCKS-only 端口。启动日志出现 `External authentication HTTP proxy enabled` 即表示已生效。

### 步骤 4：确认数据库和 Redis

```sql
-- 表必须存在（新装库已经有，老库要手工执行 —— 见 INTRANET-MIGRATION.md 第 5 节）
SHOW TABLES LIKE 'sys_external_identity';

-- 开户"创建账号"路径需要这个内置角色（init_data.sql:12 已包含）
SELECT id, role_code, data_scope, status FROM sys_role WHERE role_code = 'basic_role' AND status = 1;
```

**Redis 必须可达** —— `state` + PKCE verifier 都存在 Redis（`ExternalLoginService.java:119`，TTL 10 分钟）。

⚠️ **老库注意**：`sys_external_identity` 的建表语句**源码里已经没有了**（`db/upgrade__schema.sql` 被删），但它完整保存在
**已经打好的 jar** 里：`backend/target/lbl-shit-1.0.0.jar` → `BOOT-INF/classes/db/upgrade__schema.sql`（UTF-8，内容完好）。
提取命令见 `INTRANET-MIGRATION.md` 第 5.2 节。

---

## 五、验证步骤

### 5.1 先确认"按钮是启用的"

```bash
curl -s http://localhost:8080/api/auth/external/providers | jq
```

**期望**：
```json
{
  "code": 200,
  "data": [
    { "key": "github", "displayName": "GitHub", "icon": "github", "protocol": "OAUTH2", "enabled": true }
  ]
}
```

- 返回 `[]` → provider 配置不在生效的 profile 里（回到步骤 1）。
- `enabled: false` → `id` / `secret` / 三个 URI 里有空值（回到 1.1 的 `isProviderUsable` 逻辑）。

**⚠️ 这是个不需要认证的接口**（`/api/auth/**` 全放行），所以可以用 curl 直接测。

### 5.2 走一遍登录

| # | 操作 | 期望 |
| --- | --- | --- |
| 1 | 打开 `/login` | GitHub 图标**不再是灰色禁用**，tooltip 里没有"（未启用）" |
| 2 | 点 GitHub 图标 | 跳到 `github.com/login/oauth/authorize`，地址栏里能看到 `redirect_uri=<BACKEND_BASE_URL>/api/auth/external/github/callback`、`state=…`、`code_challenge=…` |
| 3 | 点 Authorize | 回到 `{FRONTEND_BASE_URL}/auth/callback?returnTo=/`，然后进系统 或 进 `/account/setup` |
| 4 | **首次登录**（新身份） | 落到 `/account/setup`，看到"绑定已有账号 / 创建新账号"两个选项 |
| 5 | 选"创建新账号"，填资料提交 | 见 5.3 的数据库断言 |
| 6 | 退出，再用同一个 GitHub 账号登录 | **直接进系统**，不再出现开户确认；`sys_external_identity.last_login_time` 更新 |
| 7 | 用密码账号登录 → 「登录与安全」→ 点 GitHub"立即绑定" | 跳 GitHub → 授权 → 回到 `/account/security`，身份显示"已绑定"；**当前会话没有被换掉**（这是刻意设计，见 `ExternalLoginPersistence.java:151-158`） |
| 8 | 点"解除绑定" | 成功；如果这是最后一个可用登录方式，会被拒绝（提示"解绑后将不再有可用的登录方式"） |

### 5.3 数据库断言

```sql
-- 新建的账号：registration_source 应该是 'GITHUB'
SELECT id, username, real_name, registration_source, status, auth_version
FROM sys_user WHERE registration_source = 'GITHUB';

-- 应该恰好一行；subject 是 GitHub 的数字 id（字符串形式）
SELECT * FROM sys_external_identity WHERE provider_key = 'GITHUB' OR provider_key = 'github';

-- 应该有 basic_role
SELECT ur.* FROM sys_user_role ur
JOIN sys_user u ON u.id = ur.user_id
WHERE u.registration_source = 'GITHUB';

-- 登录日志：应该有"外部认证成功，等待创建或绑定账号（外部标识=<id>）"
--           和"通过外部身份创建账号并登录（github）"两条 SUCCESS
SELECT username, result, message, login_ip, login_time
FROM sys_login_log ORDER BY id DESC LIMIT 10;
```

### 5.4 失败路径（**这些才是排查时真正用得上的**）

| 场景 | 期望行为 | 排查入口 |
| --- | --- | --- |
| 伪造 `state` | 302 到 `/auth/callback?error=外部登录状态无效或已过期` | — |
| 用户在 GitHub 上点了"取消" | 302 到 `/auth/callback?error=外部登录已取消或认证失败` | `ExternalAuthController.java:69-73` |
| `redirect_uri` 不匹配 | GitHub 直接报 `redirect_uri_mismatch`，**根本不会跳到后端**。后端日志里什么都看不到 | 检查步骤 2 的登记值 |
| 换 token 失败 | 后端日志：`External token exchange returned no access_token: provider=github code=…, description=…` | `ExternalLoginService.java:202-203`。**这条日志是排查的第一现场** |
| token 端点返回非 2xx | 后端日志：`External token endpoint rejected the exchange: status=… ` | `:250-252` |
| userinfo 被拒 | 后端日志：`External user-info endpoint rejected the request: status=…` | `:273-275` |
| 缺稳定标识 | 前端看到"外部认证结果缺少稳定用户标识"；日志里 `logProviderError("user-info", ...)` 会给出平台的真实错码 | `:207-210`、`:343-348` |
| 该外部身份已绑别的账号 | 前端看到「该外部身份已经绑定其他账号」 | `RegistrationService.java:178` |
| 尝试绑定一个没有密码的账号 | 前端看到的是「用户名或密码错误」（**故意模糊**），但 `sys_login_log.message` 里会写"该账号未设置登录密码，无法用密码绑定" | `RegistrationService.java:135-140` |

登录失败也会写 `sys_login_log`（FAILURE），日志主体是 `onboarding:github` 或 `bind:github`（`ExternalLoginService.java:223-225`）—— 这是刻意设计：失败时还没有真实用户名可用，用可检索的前缀代替。

---

## 六、这个实现里已经做对的地方（别改坏）

| 做得好的点 | 位置 |
| --- | --- |
| **`state` 一次性消费**（Redis `getAndDelete`，TTL 10 分钟） | `ExternalLoginService.java:288` |
| `code_verifier` 存在同一个事务对象里，一起消费 | `LoginTransaction.java`、`:234` |
| **网络 IO 与数据库事务彻底分离**（两次外部请求各有 15 秒超时，包在事务里会占死 Hikari 连接池） | `ExternalLoginPersistence` 类注释 |
| **成功日志写在事务提交之后**（避免一个请求占两条连接，也避免被 catch 误判成失败） | `ExternalLoginService.java:180-184` |
| **GET 换 token 时日志里不打印完整 URI**（否则 `client_secret` 就写进日志文件了） | `:248-249` |
| 失败响应体截断 + 只提取可识别的错误字段（避免把 `invalid client_secret=xxx` 整段落盘） | `:358-363`、`:365-382` |
| **内部异常不写进 302 的 Location**（否则表名/列名/SQL 会出现在地址栏、Referer 和网关访问日志里） | `ExternalAuthController.java:88-95` |
| **`returnTo` 做了开放重定向防护**（必须以单 `/` 开头且不是 `//`），前端再校验一次同源 | `:403-405`、`frontend/src/router/menu.ts:55-65` |
| **绑定流程的会话固定**：复用发起绑定时那个浏览器的会话，而不是另开一个，并校验"属于同一个用户 + 同一个 authVersion" | `ExternalLoginPersistence.java:151-158` |
| **绑定时的密码校验复用登录限流器**（否则它是一个不限速的密码爆破旁路） | `RegistrationService.java:98-116` |
| **开户态是受限身份**：只有 3 个 authority，且 `AccountIdentityController` 明确拒绝它 | `JwtAuthenticationFilter.java:51-54` |
| **没有按邮箱自动关联账号** | ✅ 这是对的。全项目只有 `identities.find(provider, issuer, subject)` 和按 `username` 查用户，**邮箱只作为展示快照写入**。不要加"邮箱相同就自动绑定"—— GitHub 的 email 可能是私密/未验证，微信根本没有 email |

---

## 七、需要修的 4 个问题（按严重程度）

### 🔴 7.1 `state` 没有和"发起登录的浏览器"绑定 → 登录 CSRF

**问题**：`/start` 不种任何 Cookie，`LoginTransaction` 也不记录浏览器指纹（`LoginTransaction.java` 只有 providerKey / verifier / redirectUri / returnTo / rememberMe / targetUserId / initiatingSid）。所以回调（`ExternalLoginService.java:150`）会**为任何拿着这个 URL 的浏览器**完成登录。

**攻击方式**：
1. 攻击者用自己的 GitHub 账号发起登录，拿到 `code` 和 `state`（在 GitHub 重定向到后端之前截住 URL —— 攻击者控制自己的浏览器，所以很容易）；
2. 把这个 callback URL 发给受害者；
3. 受害者打开 → **被静默登录进攻击者的账号**，并拿到一个 14 天的 cookie（而且 `rememberMe` 也是攻击者控制的）；
4. 受害者以为这是自己的账号，开始在里面录入数据 → **数据全进了攻击者的账号**。

**为什么现在危害有限**：只有通过"内部统一认证"自动创建账号的系统才真正危险。对 GitHub/微信这种"要用户主动去开户确认"的流程，受害者还会看到一个开户确认页，可能察觉不对。**但一旦接入 UIAS（自动建号），这就是一个真实的账号接管漏洞。**

**修法**（几十行）：`begin()` 里额外写入一个短寿命的 httpOnly Cookie：

```java
// begin() 里，和写 Redis 事务一起
String browserBinding = token(24);
// 存进 LoginTransaction，同时在响应上种一个 Cookie：
// Set-Cookie: <name>_ext_state=<browserBinding>; HttpOnly; SameSite=Lax; Path=/api/auth; Max-Age=600
// complete() 里比对：Cookie 值与事务里的不一致 → 拒绝
```

> ⚠️ 注意 `/start` 和 `/callback` 都需要能读写这个 Cookie，而 Cookie 的 `Path` 已经是 `/api/auth`（覆盖 `/api/auth/external/**`），所以 Path 不用改。
> **顺带说明**：**绑定**流程是安全的 —— 它把 `initiatingSid` 钉在 Cookie 上并在两处重新校验（`:156-162`、`ExternalLoginPersistence.java:68-73`）。

### 🟠 7.2 未认证的 callback 完全没有限流 → 日志表放大

`/api/auth/**` 是全放行的（`WebSecurityConfig.java:56`），而且 `JwtAuthenticationFilter.shouldNotFilter()` 对 `/api/auth/**` 直接跳过（`:84-88`）。所以：

- 任何匿名请求都能打到 `GET /api/auth/external/github/callback?code=x&state=y`；
- `complete()` 在**每一条**失败路径上都会往 `sys_login_log` 插一行 FAILURE（`:169-170`、`:177`）；
- 这个入口**没有任何限流**（对比 `/start` 有 30 次/分钟的来源配额）。

**后果**：一段脚本就能持续往审计表里灌垃圾，把"翻某天的日志"变成全表扫描（而且日志保留策略是每天凌晨才跑一次）。

**修法**：在 `complete()` 开头（或 controller 的 callback 里）加一句：

```java
attempts.requireSourceQuota("external-callback", 60, Duration.ofMinutes(1));
```

### 🟡 7.3 网络层的异常被静默吞掉，日志里没有任何诊断信息

`ExternalLoginService.java:255-259` 和 `:278-282`：

```java
} catch (Exception ex) {
    throw new BusinessException("无法连接外部认证服务");   // ← 原始异常完全没有被记录
}
```

`IOException` / `InterruptedException` / TLS 握手失败 / DNS 解析失败**都不会留下任何痕迹**。而且 `complete()` 的 catch 只把业务消息写进登录日志（`:166-171`），所以：

> **一个 DNS 或 TLS 问题，会表现为"用户说三方登录不行"，而日志里干干净净 —— 这正是项目其他地方极力避免的那类问题。**

**修法**：
```java
} catch (Exception ex) {
    log.warn("Failed to reach external service: provider={} stage=token", providerKey, ex);
    throw new BusinessException("无法连接外部认证服务");
}
```
（同理 `consume()` 里 `:290-294` 的 Jackson 解析失败也应该留日志。）

### 🟡 7.4 上面三条之外，还有两个"配置卫生"问题

| 问题 | 影响 | 修法 |
| --- | --- | --- |
| **provider 配置在 git-ignored 文件里** | 新环境三方登录整体消失 | 见第四节步骤 1 |
| **`application-prod.yml` 完全没有 `lbl.external-auth`** | 生产环境一定没有三方登录 | 同上（或单独加一份 prod 配置块） |
| `"wechat".equals(providerKey)` 硬编码 | 改 key 名或接第二个微信应用时静默坏掉 | 换成 `subject-fallback-claim` 配置项 |
| GitHub 缺 `/user/emails` 兜底 | 私密邮箱用户的邮箱快照为空（不影响登录） | 加可选的 `email-fallback-uri` |
| `sys_external_identity.*_snapshot` 是 `VARCHAR(128)` | 超长微信昵称会插入失败 | 加长度截断 |
| 开户表单 `realName` 用昵称预填但无 `maxLength` | 超长昵称提交时报错 | 前端加 `maxLength={64}` |

---

## 八、我的建议（如果你只是想让内网系统能扫码/免密登录）

1. **别做微信。** 内网场景下微信登录基本不可行（需要公网域名 + 企业资质的网站应用 + 审核）。
2. **GitHub 只适合你自己用**（公司同事大多没有 GitHub 账号，而且内网可能访问不到 github.com）。
3. **真正适合内网的是 UIAS 那条路**。当前已有工号匹配、会话、审计与本地 RBAC 的流程骨架；剩余工作集中在接入公司 SDK 的 `consumer.consume(request)` 与 ESF 员工目录查询。详见 `UIAS-INTEGRATION.md`。
4. **如果你的内网能出网且想快速验证这条链路**，用 GitHub 走一遍（本地就能测，成本最低）——**先把配置搬出 git-ignored 文件、修掉 7.2 和 7.3，再走一遍第五节的验证步骤**。走通之后，UIAS 的接入就只是"换一个 `verifyIdentity()` 的实现"而已。
