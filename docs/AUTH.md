# 登录、鉴权、会话与数据存储

> 这一篇回答四件事：**登录的具体逻辑是什么 / 鉴权是怎么判的 / 数据存进了哪些表 / Redis 里那些 key 都是干什么的。**
> 全部结论都标注了文件与行号，可以直接翻代码核对。

---

## 一、总体设计：为什么是"服务端会话 + 短寿命 JWT"

这是理解整个鉴权体系的前提。

| 方案 | 优点 | 缺点 |
| --- | --- | --- |
| 纯 JWT | 无状态、不查库 | **无法主动失效**。停用账号、改密码、踢下线都做不到，只能等 token 过期 |
| 纯 Session | 可随时失效 | 每个请求都要查存储；跨域/跨端传递要靠 Cookie |
| **本项目：两者结合** | 兼得 | 稍复杂 |

**本项目的做法**：

- **access token**：JWT，只活 **15 分钟**（`lbl.security.access-token-minutes: 15`），放在前端**内存**里，随 `Authorization: Bearer` 头发送。它只是"入场券"，泄露了最多用 15 分钟。
- **refresh 凭据**：一个 48 字节随机串，叫 `sid`，放在 **httpOnly Cookie**（名字 `lbl_refresh`）里，JS 读不到。同时 **Redis 里有一份服务端会话**。
- **每个请求都校验 Redis 会话**（`JwtAuthenticationFilter`）。JWT 里的 `sid` 指向的会话必须存在、用户状态必须正常、`authVersion` 必须一致 —— 三者任一不满足就视为未认证。

**因此"主动失效"是这样实现的**：删掉 Redis 里的会话 → 该会话签发的所有 JWT 立刻失效（即使 JWT 本身还没过期）。

### 关键字段：`sys_user.auth_version`

一个自增计数器。**凡是"必须让已发出的凭据立刻作废"的操作，都把 `auth_version + 1`**，然后校验时对比 JWT/会话里的 `authVersion` 与数据库里的值，不等就拒绝。

| 触发点 | 位置 |
| --- | --- |
| 管理员改用户信息（含部门/角色/状态） | `UserService.update` (`UserService.java:237`) |
| 管理员重置密码 | `UserService.resetPassword` (`UserService.java:268`) |
| 用户自己改密码 | `UserService.changeOwnPassword` (`UserService.java:308`) |

这三处同时还调 `sessions.removeAll(userId)` 删掉该用户的所有 Redis 会话。**双保险**：即使 Redis 删除失败，`authVersion` 不一致也会让旧凭据失效。

> ⚠️ **改代码时的硬性约定**：任何"改变这个账号能被谁登录 / 能不能登录"的操作，都必须 `authVersion++` + `sessions.removeAll()`。你在 `DepartmentChangeService` 里能看到它**没有**这么做 —— 因为"部门变更"不影响登录资格（只影响数据范围），这是刻意的。

---

## 二、登录的完整逻辑

### 2.1 账号密码登录

**入口**：`POST /api/auth/login` → `AuthController.login` (`AuthController.java:65-77`) → `AuthService.login` (`AuthService.java:44-73`)

```
AuthService.login(request)
 │
 ├─① 限流检查：attempts.isLocked(账号+来源, 来源)
 │     ├─ 命中 → 记 LOCKED 日志 → 抛 TooManyRequestsException(429) "登录尝试过于频繁，请 15 分钟后再试"
 │     │
 ├─② 查库：sys_user（按 username）+ sys_local_credential（按 userId）
 │
 ├─③ 统一校验（5 个条件，任一不通过都走同一条失败路径）：
 │     user == null                       → 用户名不存在
 │     user.status != 1                   → 账号已停用
 │     credential == null                 → 没有密码凭据（纯三方登录账号）
 │     credential.enabled != 1            → 密码登录被禁用
 │     !BCrypt.matches(密码, 哈希)         → 密码错误
 │     └─ recordFailure() → 记 FAILURE 或 LOCKED 日志 → 抛 BusinessException("用户名或密码错误")
 │
 ├─④ 通过：recordSuccess()  ← 清空「账号+来源」与「来源级」两级失败计数
 │
 ├─⑤ passwordChangePolicy.requiresChange(credential)
 │     ├─ credential.password_change_required == 1  → true（管理员刚重置过）
 │     └─ password_max_age_days > 0 且 密码已超期  → true
 │
 ├─⑥ sessions.create(userId, username, authVersion, rememberMe, passwordChangeRequired)
 │
 └─⑦ 返回 LoginResult(jwt.issue(sid, ...), UserProfile(id, username, realName, passwordChangeRequired))
 │
 └─ Controller 层：
      Set-Cookie: lbl_refresh=<sid>; HttpOnly; Secure(可配); SameSite=Lax; Path=/api/auth
                  Max-Age = 记住我 ? 14天 : 会话级
      若浏览器原本带着另一个 sid → 先 sessions.remove(旧 sid)   ← 见下
```

**"同一浏览器再次登录替换旧会话"**（`AuthController.java:71-73`）：否则每登录一次就在 Redis 里累加一台"新设备"，用户去"登录设备"页会看到一堆自己。这同时也能回收"登录已成功但紧接着 `/auth/me` 因网络失败、用户重试"留下的旧会话。

**对外统一返回"用户名或密码错误"，但日志区分真实原因**（`AuthService.java:88-96`）：
- 对外的动机是**防账号枚举** —— 不能让攻击者通过错误信息区分"用户不存在"和"密码错误"。
- 日志的动机是**可排查性** —— 否则"某人说登不上"时根本看不出是密码错、账号被停用还是用户名打错了。日志写进 `sys_login_log.message`。

### 2.2 登录限流：为什么是两层

`auth/service/LoginAttemptGuard.java`。全项目认证类入口的滥用控制**唯一实现处**。

| 层级 | 阈值 | 窗口 | 锁定时长 | 防的是什么 |
| --- | --- | --- | --- | --- |
| 账号 + 来源 | 5 次 | 15 分钟 | 15 分钟 | 针对单个账号的密码爆破 |
| 来源（IP） | 20 次 | 15 分钟 | 15 分钟 | **密码喷洒**（每个账号只试几次） |

**为什么单靠"按账号锁定"不够**：攻击者可以故意锁死已知员工的账号（拒绝服务）。所以同时维护"账号+来源"的严格限制和来源级的宽松限制。

**两个容易忽略的正确性细节**：

1. **账号标识必须忽略大小写**（`LoginAttemptGuard.loginKey`，第 64-68 行）：
   MySQL 默认排序规则下用户名比较不区分大小写，如果限流 key 区分大小写，攻击者交替使用 `Admin` / `admin` 就能把计数拆成两份，账号级锁定永远攒不满。同时**截断到 64 字符**（与 `sys_user.username` 列长一致），否则可以用超长用户名撑大 Redis key。

2. **成功后要连来源级计数一起清**（`LoginAttemptGuard.recordSuccess`，第 102-112 行）：
   办公网出口 NAT 下所有人共享一个来源。如果某个同事反复输错密码攒满 20 次，会把整个出口 IP 锁 15 分钟、所有人被挡在门外。校验成功说明这个来源此刻是正常的，应当放行。

**来源（IP）是怎么确定的**：`RequestInfo.rateLimitIp()` → `TrustedProxyResolver`。**只有请求确实来自配置的可信代理时，才采信 `X-Forwarded-For`**，否则直接用 TCP 源地址。详见第五节。

**Redis key 里不保存 IP 原值**，只存它的 SHA-256（`LoginAttemptGuard.hash`）。

### 2.3 图形验证码

`auth/service/CaptchaService.java`（273 行）。**只用于 `/api/auth/register`**（登录不要求验证码）。

| 项 | 值 |
| --- | --- |
| 字符表 | `23456789ABCDEFGHJKLNPQRSTUVXYZ`（30 个字符） |
| 长度 | 4 位 |
| TTL | 2 分钟 |
| 画布 | 148 × 44 |
| 出题限流 | 同来源 60 次 / 分钟 |
| 开关 | `lbl.security.captcha-enabled`（默认 true） |

**三个关键设计**：

1. **必须服务端出题、服务端校验。** 纯前端画的验证码等于没有——攻击者根本不打开注册页，直接对接口发请求就绕过去了。"页面上有个验证码"和"接口要求验证码"是两件事。

2. **一次性消费**（`CaptchaService.java:127`）：用 `getAndDelete` 取答案，**无论对错都立刻失效**。如果允许对同一个 `captchaId` 反复提交，攻击者拿到一个 id 就能在 2 分钟内高速穷举（答案空间只有 30⁴ ≈ 81 万，脚本几分钟就能撞完）。前端失败后会自动换一张。

3. **字符表刻意去掉了易混字符与最宽字形**：
   - 去掉 `0/O`、`1/I/L` —— 用户看不清时唯一的补救是"换一张"，这类误判是纯体验损失、没有安全收益。
   - **去掉 `M`、`W`** —— 这是实测出来的：字符逐个随机旋转会撑宽包围盒，即使把画布加宽到步长 29.6px，连续四个 `W` 仍会在像素上粘连成一块（实测 blocks=2，正确应是 4），用户根本数不出这是四个字符。代价是答案空间 −23%，可以忽略。

4. **启动时探测字体可用性**（`CaptchaService.requireRenderableFont`，第 229-237 行）：
   `java.awt` 依赖系统字体，精简 Linux 容器（alpine/slim）默认没装任何字体，那种环境下生成的是**纯色空白图**——用户永远猜不中、注册永远失败，而后端不报任何错。所以启动时主动画一次字并检测像素，画不出来就**拒绝启动**并给出两条出路（装字体 / 设 `CAPTCHA_ENABLED=false`）。
   捕获的是 `Throwable` 而不是 `Exception`：无图形环境下会抛 `HeadlessException` 甚至 `AWTError`（属于 `Error`），只 catch `Exception` 会漏掉。

### 2.4 密码规则

`auth/service/PasswordRules.java` —— **全站新密码规则的唯一来源**。

- 长度 **8–32** 位
- 允许可见 ASCII（不含空格）
- 至少包含**大写字母 / 小写字母 / 数字 / 特殊字符中的三类**

**适用范围（非常重要）**：只用于**设置新密码**的入口（自助注册、三方开户、管理员新增用户、本人改密）。
**绝不能**用于校验登录密码或"原密码" —— 历史密码可能是旧规则下的其他形态，套上新规则会让老用户立刻登不进去。登录侧只保留 `@Size(max = 72)`（BCrypt 的长度上限）。

**前端有一份副本**：`frontend/src/utils/passwordPolicy.ts`（前端无法 import Java）。改规则时**两边必须同步**。`PasswordRulesTest` 用边界密码钉住了"常量与正则一致"这件事。

**随机临时密码生成**（`randomCompliantPassword`）：前三位分别从大写/小写/数字里取（保证复杂度约束一定成立），再随机打乱位置——否则生成的密码永远是"大写开头、第二位小写、第三位数字"的固定形状。

### 2.5 强制改密

`auth/service/PasswordChangePolicy.java`。两个独立条件收敛到同一个出口：

1. **管理员重置过密码**：`password_change_required = 1`。管理员手上有一个刚生成的密码，属于"管理员已知的凭据"，必须先换掉。
2. **密码超期**：`lbl.security.password-max-age-days > 0` 时，距上次改密超过该天数即视为过期。**当前默认 0 = 关闭**。

**只对"用密码登录"的会话生效**（`appliesToPasswordLogin`）：这是**刻意的产品决定，不是遗漏**。通过第三方登录的用户不受强制改密约束——他们不想改密码就一直用三方登录即可。
直接后果有两条，都是明确接受了的：
- 一个既绑定了三方、密码又已过期的用户，可以改用三方登录绕过这道闸门；
- 因此密码过期策略对"有第三方登录方式"的账号事实上没有强制力。

代码注释里明确写了：**不要把这里的 `authMethod` 判断当成 bug 去掉**。

**强制改密时"什么都看不到"是怎么实现的**（`AuthController.java:132-146` + `JwtAuthenticationFilter.java:63-66`）：

| 位置 | 行为 |
| --- | --- |
| JWT 过滤器 | `pendingPasswordChange = authMethod == "PASSWORD" && session.passwordChangeRequired` → **authorities 设为空列表** |
| `/auth/me` | `permissions = []` **且** `menus = []` |
| 前端 | `AppLayout` 弹出一个**不可关闭**的改密弹窗（`closable={false}` `maskClosable={false}` `keyboard={false}`），"取消"按钮变成"退出登录" |

⚠️ 注释里记着一个真实踩过的坑：**此前只清空了 `permissions`、`menus` 仍返回全量**，于是首登用户的侧边栏是完整的，但每个请求都会因为 authorities 为空被 403 —— **前端菜单和后端权限对不上**。所以这两个字段必须"同进同退"。

**改密接口自己的防爆破**（`UserService.changeOwnPassword`，第 274-312 行）：
这个接口只需要会话、不需要任何权限码（首登用户 authorities 为空也必须能改密码），所以它天然是一个"带身份的原密码爆破点"——拿到一个已登录会话（例如借用他人未锁屏的电脑）后可以无限次猜原密码，猜中就能彻底接管账号、把真正的主人挤下线。
因此加了 **5 次 / 15 分钟** 的限制，用 Redis 计数（`auth:password-change:fail:<userId>`）。键里用 user id 而不是用户名，避免用户名出现在 key 里。
另外它**只算一次 BCrypt 校验**（复用第一次结果判断"新旧是否相同"），并对外统一返回"原密码不正确，或新密码与当前密码相同"——区分这两种情况会向尚未通过验证的调用者泄露"原密码是对的"。

### 2.6 自助注册

`POST /api/auth/register` → `RegistrationService.register` (`RegistrationService.java:58-69`)

```
① captcha.verify(captchaId, captchaCode)   ← ★ 顺序刻意的
② PasswordRules.requireConfirmed(password, confirmPassword)
③ createUser(...)：查重(含逻辑删除) → INSERT sys_user → 分配 basic_role
④ savePassword(...)：BCrypt → INSERT sys_local_credential
⑤ issue(...)：建会话 + 签发令牌
```

**为什么验证码必须在最前面**：`createUser` 是一次 SELECT + 一次 INSERT，`savePassword` 是一次 BCrypt（约几十毫秒 CPU）。放在后面的话，"刷接口"的成本就已经付掉了，验证码只剩下挡垃圾账号的作用。

**注册用户的默认身份**：`registration_source = 'LOCAL'`、`dept_id = NULL`、角色 = `basic_role`（`init_data.sql:70-71` 里该角色只有"首页"一项权限）。所以新注册的人登录后只能看到首页——这是刻意的，管理员必须显式授权。

---

## 三、鉴权（每个请求是怎么判的）

### 3.1 `JwtAuthenticationFilter` 的判定链

`security/filter/JwtAuthenticationFilter.java`。**它排在 Spring Security 过滤器链里 `UsernamePasswordAuthenticationFilter` 之前。**

```
shouldNotFilter(req)？
  ├─ uri 以 /api/auth/ 开头 且 不以 /api/auth/onboarding/ 开头 → 跳过（这些接口自己处理身份）
  ├─ /swagger*、/v3/*  → 跳过
  └─ 否则继续

读 Authorization: Bearer <token>
  ├─ 没有或前缀不对 → 直接放行（未认证，最后由 SecurityFilterChain 输出 401）
  └─ 有 → jwt.parse(token)   ← 验签名 + 验过期，失败抛 JwtException

【分支 A：开户确认态】
  会话存在 && session.onboarding() && token.subject == "onboarding:" + session.onboardingId
  → principal = OnboardingPrincipal(...)
  → authorities = [onboarding:access, onboarding:account:create, onboarding:account:bind]
  → 放行

【分支 B：正式会员】以下条件全部满足才算认证成功：
  ① session != null
  ② user = users.selectById(session.userId) != null
  ③ user.status == 1
  ④ user.authVersion == session.authVersion              ← 账号信息变过就作废
  ⑤ session.username == token.subject                    ← 与令牌主体一致
  ⑥ session.authVersion == token["authVersion"]           ← 与会话一致
  通过 → authorities = menus.selectByUserId(userId).permissionCode 集合
         （若 pendingPasswordChange，则 authorities = 空）
         principal = CurrentUser(id, username, deptId)
         authentication.setDetails(sid)   ← 业务接口从这里取"当前会话 id"
  不通过 → 什么都不设 → 后续被 401

异常处理：
  JwtException / IllegalArgumentException → log.debug（正常过期/篡改，交给 401）
  其它 Exception（Redis 不可用、会话数据损坏） → log.warn（否则无法与"令牌过期"区分）
```

**为什么 `details` 里要塞 `sid`**（`AccountSessionController.java:52-61` 有解释）：
刷新 Cookie 的 `Path=/api/auth`，所以**业务接口根本收不到它**。但"退出指定设备"这类功能需要知道"哪个是当前会话"。从已验签的 JWT 里把 sid 带出来，既不用放宽 Cookie 的 Path（会扩大暴露面），也防止设备管理误删当前会话。

**为什么开户态的 `isOnboarding` 判定要重算 subject**：开户态会话在 Redis 里没有 `userId`，只能靠 `onboardingId` 定位。让令牌主体与它严格对应，能防止"拿一个会员令牌却试图以开户态身份操作"。

### 3.2 方法级权限：`@PreAuthorize`

```java
@GetMapping
@PreAuthorize("hasAuthority('system:user:list')")
Result<PageResult<UserListVO>> page(...)   // UserController.java:38-39
```

全套权限码（来自 `init_data.sql` + `SystemPermissionInitializer`）：

| 模块 | 权限码 |
| --- | --- |
| 用户 | `system:user:list` / `add` / `update` / `delete` / `export` / `reset-password` |
| 角色 | `system:role:list` / `add` / `update` / `delete` / `grant` |
| 部门 | `system:dept:list` / `add` / `update` / `delete` |
| 菜单 | `system:menu:list` / `add` / `update` / `delete` |
| 登录日志 | `system:loginlog:list` / `delete` |
| 操作日志 | `system:operatelog:list` / `delete` |
| 特殊 | `onboarding:access` / `onboarding:account:create` / `onboarding:account:bind` |

**`hasAnyAuthority` 的用法**（新增和编辑共用同一个表单数据接口）：
```java
@PreAuthorize("hasAnyAuthority('system:user:add', 'system:user:update')")
```
还有 `hasAnyAuthority('system:dept:add', 'system:dept:update')`（`DeptController.java:37`）。

**"只要登录、不要具体权限"的写法**（个人中心类接口）：
```java
@PreAuthorize("isAuthenticated() and !hasAuthority('onboarding:access')")
```
这个表达式出现在 `AccountSessionController`、`AccountProfileController`、`AccountIdentityController`、`NotificationController`、`RealtimeController`、`DepartmentChangeController`。
`!hasAuthority('onboarding:access')` 这一半**不能省** —— 否则"还没注册/还没绑定"的开户临时身份也能访问个人中心。

### 3.3 URL 级权限（`WebSecurityConfig`）

顺序**很重要**，Spring Security 按声明顺序匹配：

```java
.requestMatchers("/ws/**").permitAll()                       // WebSocket 靠一次性 ticket 自己鉴权
.requestMatchers("/api/auth/onboarding/**").authenticated()  // ★ 必须排在下面那条之前
.requestMatchers("/api/auth/**").permitAll()
.anyRequest().authenticated()
```

**为什么 `/api/auth/onboarding/**` 一定要单独提前声明**（`WebSecurityConfig.java:42-51` 有完整说明）：
这两个接口靠 `@PreAuthorize` 兜底，而方法级权限拒绝抛的是 `AccessDeniedException` → 被全局异常处理器转成 **403**。而前端的静默续期**只在 401 时触发**。于是访问令牌一过期（15 分钟），用户点"创建账号 / 绑定账号"只会看到"没有访问该资源的权限"，**既不续期也不跳登录页，整个页面卡死**。让它在"未认证"时走认证入口点返回 401，续期链路才能正常接上。

**为什么把 `/swagger-ui/**`、`/v3/api-docs/**` 的放行规则删掉了**（`WebSecurityConfig.java:52-55`）：
springdoc 依赖已移除。安全配置应当准确反映"什么是对外公开的"，留着已不存在的路径放行，会在将来重新引入文档依赖时**悄悄把整个接口面暴露出去**。如果以后重新引入 springdoc，记得同时补回这两条。

### 3.4 三个不同的拒绝路径，都必须是同一套语义

| 场景 | 抛出者 | 处理者 | 结果 |
| --- | --- | --- | --- |
| 没带令牌 / 令牌无效 / 会话失效 | `JwtAuthenticationFilter` 不设置认证 | `authenticationEntryPoint` | **401** |
| 已认证但 `@PreAuthorize` 不通过 | `AuthorizationDeniedException` | `GlobalExceptionHandler#forbidden` | **403** |
| 已认证但过滤器链规则不通过 | `AccessDeniedException` | `accessDeniedHandler` | **403** |

401 与 403 的区分是前端逻辑的基础（401 续期、403 不续期），不能混。

---

## 四、会话与"记住我"

`auth/session/SessionService.java` + `LoginSession.java`

### 4.1 会话里存了什么

`LoginSession` 是一个 record（`LoginSession.java:8-12`），**序列化成 JSON 存在 Redis**：

| 字段 | 含义 |
| --- | --- |
| `principalType` | `MEMBER`（正式会员）或 `ONBOARDING`（三方开户确认态） |
| `userId` / `username` / `authVersion` | 身份与失效标记 |
| `rememberMe` | 是否"记住我" |
| `authMethod` | `PASSWORD` 或 `EXTERNAL` |
| `providerKey` | 三方登录时的提供方（local / github / wechat / …） |
| `onboardingId` / `issuer` / `subject` / `displayName` / `email` / `employeeNo` | 仅开户态使用 |
| `passwordChangeRequired` | 是否需要强制改密（会话快照） |
| `createdTime` / `lastActiveTime` | 创建与最后活动时间 |
| `loginIp` / `userAgent` | 登录来源（供"登录设备"页展示） |

**为什么 `passwordChangeRequired` 是会话快照而不是每请求实时算**（`AuthController.java:131`）：到期状态只在密码登录/刷新会话时计算并写入会话，业务请求途中读快照，避免"操作到一半突然掉权限"。

### 4.2 两套超时：空闲 + 绝对

| 模式 | 空闲超时 | 绝对上限 | 配置项 |
| --- | --- | --- | --- |
| 普通会话 | 2 小时 | 无 | `idle-hours: 2` |
| 记住我 | 7 天 | **14 天** | `remembered-idle-days: 7`、`remembered-absolute-days: 14` |
| 开户确认态 | 30 分钟 | 2 小时 | 代码常量（`SessionService.java:21-22`） |

- **空闲超时**由 `touch(sid)` 续期。前端 `UserActivityManager` 在用户**真实交互**（click / keydown / input / pointerdown / scroll）时、距上次超过 5 分钟才发一次 `POST /auth/touch`。服务端仍会执行空闲超时限制。
- **绝对上限**：记住我会话额外写一个 `auth:session:absolute:<sid>` key（TTL 14 天），**永不被续期**。`touch()` 时取两者剩余时间的**较小值**作为新 TTL。所以"持续操作也不能超过 14 天"是成立的。
- **开户态的 TTL 刻意不随 `rememberMe` 变化**（`SessionService.java:63-70`）：开户态自身的语义是"没注册、没绑定就失效"，勾了记住我也不该延长它。
  但 `rememberMe` 值本身**被存进会话**并**带过这一跳**——它的唯一用途是被 `RegistrationService` 在创建/绑定账号时读走，决定**正式会员会话**是否长期有效。
  ⚠️ 注释里记着这曾经是个 bug：这个值被写死成 `false`，于是勾了"记住我"的用户走完开户确认后拿到的是会话级 Cookie，**关掉浏览器就要重新登录 —— 用户的选择在最后一跳被静默丢弃**。

### 4.3 会话索引与设备管理

| Redis key | 类型 | 作用 |
| --- | --- | --- |
| `auth:session:<sid>` | String(JSON) | 会话主体 |
| `auth:session:absolute:<sid>` | String | 只对"记住我"和开户态存在，绝对上限标记 |
| `auth:user:sessions:<userId>` | Set | 该用户的所有 sid，用于"列出我的设备 / 踢出其他设备 / 全部退出" |

**设备列表对外用"引用"而不是真实 sid**（`SessionService.reference()`）：`SHA-256(sid)` 取前 22 个字符（Base64URL）。所以"退出指定设备"接口收的是这个引用，**真实 sid 不出现在任何响应里**。

`listMemberSessions()` 会顺手清理脏数据：sid 在 Set 里但会话已不存在、或属于其他用户的，直接从 Set 里移除。

### 4.4 会话失效的所有触发点

| 触发 | 效果 |
| --- | --- |
| `remove(sid)`（退出登录） | 删 session + absolute + 从 user Set 移除 + **关闭对应 WebSocket** |
| `removeAll(userId)` | 删该用户所有会话 + 关闭所有 WebSocket |
| `removeAllExcept(userId, currentSid)` | "退出其他设备" |
| `removeByReference(userId, reference)` | "踢出指定设备"（管理员或本人） |
| `authVersion++` | 让所有旧令牌失效（纵深防御） |
| `user.status = 0` | 过滤器校验时拒绝 |

**"关闭对应 WebSocket"很关键**：`SessionService.remove()` 里调 `realtime.closeSession(sid)`。否则会出现"网页看着还活着、实时消息还在推，其实凭据已经失效"的诡异状态。

### 4.5 刷新凭据 Cookie 的三个硬约束

1. **`HttpOnly`** —— JS 读不到，降低 XSS 窃取风险。
2. **`Path=/api/auth`** —— 业务请求不会携带它，缩小暴露面。代价是业务接口要 sid 时必须从 `authentication.getDetails()` 取。
3. **`SameSite=Lax`** —— refresh/touch/logout 都是 POST，跨站请求不会带上这个 Cookie，**相当于免费拿到一层 CSRF 防护**。这也正是项目敢 `.csrf(c -> c.disable())` 的原因。
   代价：本地开发用 `127.0.0.1` 访问前端、API 却指向 `localhost` 时会被判定为跨站而丢 Cookie。**统一用 `localhost` 访问即可。**

**签发/清除必须走同一个出口**（`AuthController.refreshCookie()`，第 170-188 行）：登录与退出必须产生属性完全一致的 Cookie。注释里记着：此前退出路径少写了 `sameSite`，虽然 HttpOnly + Path 对得上、浏览器大多也能删掉，但属性不一致属于**随时会变成"退不掉"的隐患**。
`OnboardingAccountController.setCookie()` 是第二处手写（注释明确提醒"必须与 `AuthController#refreshCookie` 完全一致"），这是**一个应该合并的重复点**。

---

## 五、真实客户端 IP 的获取

`security/proxy/TrustedProxyResolver.java`（243 行）

**为什么必须有这个东西**：审计日志里的 IP 是溯源证据，登录限流的 key 也依赖它。

- 如果**无条件相信** `X-Forwarded-For`，任何人都能塞一个假 IP → ① 审计日志失去价值；② 登录限流被轻易绕过（每次换一个假 IP，账号级锁定永不触发）。
- 如果**完全不看**转发头，部署在 Nginx 后面时所有请求的 TCP 源地址都是网关 → 全站用户共享同一个限流 key，**一个人失败 20 次就把所有人锁在门外**。

**正确做法：显式声明哪些地址是可信代理，只信它们的转发头。**

```
lbl.security.trusted-proxies: 127.0.0.1,10.0.0.0/8,2001:db8::/32
```

留空 = 一个转发头都不信，直接返回 TCP 源地址（**直连部署下的正确行为**）。

**算法：从右往左找第一个不可信地址**（与 nginx realip 模块一致）。
`X-Forwarded-For` 形如 `客户端, 代理1, 代理2`。攻击者可以伪造左边的部分（那是他自己加进去的），但**右边由我们的可信代理追加，不可伪造**。所以从最右端开始向左走，跳过所有可信代理，遇到的第一个不可信地址就是真实的上一层客户端。

还支持 RFC 7239 的 `Forwarded` 头（只取 `for=` 参数）。

**三个细节处理**：
- **端口必须去掉**：`203.0.113.7:44321` 与规则里的 `203.0.113.7` 匹配不上，可信代理会被误判为不可信，整条链就废了。
- **CIDR 用字节前缀匹配而不是字符串前缀**：`203.0.113.7` 与 `203.0.113.70` 的字符串前缀相同但网络不同。
- **IPv4-mapped IPv6 要还原**：双栈主机上 `getRemoteAddr()` 可能返回 `::ffff:10.0.0.1`。不还原的话配置里的 `10.0.0.1` 规则会因为"16 字节 vs 4 字节"匹配失败而被判不可信 → **"可信代理配了却完全不生效、所有人还是共享网关地址"这类静默失败极难排查**。

**配置写错时只告警并跳过，不让应用起不来**——因为降级方向是安全的（退回"不信转发头"）。

> ⚠️ **搬去内网部署到 Nginx 后面时，`trusted-proxies` 必须填。** 否则 IP 全是网关地址、且限流会让所有用户互相误伤。

---

## 六、数据存储

### 6.1 MySQL 表清单（共 13 张）

出处：`backend/src/main/resources/db/init_schema.sql`

#### 组织与账号

| 表 | 用途 | 关键字段 |
| --- | --- | --- |
| `sys_user` | 用户主表 | `username`(UK)、`employee_no`(UK)、`employee_no_verified`、`registration_source`(LOCAL/ADMIN/GITHUB/WECHAT…)、`status`、**`auth_version`**、`builtin`、`deleted`、审计四件套 |
| `sys_dept` | 部门（**物化路径树**） | `parent_id`、**`ancestors`**（如 `"0,1,2"`）、`dept_code`(UK)、`leader_user_id`、`sort_order`、`status`、`builtin`、`deleted` |
| `sys_local_credential` | 本地密码凭据（**与 user 一对一**） | `user_id`(PK)、`password_hash`(BCrypt)、`password_change_required`、`enabled`、`password_changed_time` |
| `sys_external_identity` | 第三方身份绑定 | `user_id`、`provider_key`、`issuer`、`subject`、UK `(provider_key, issuer, subject)`、UK `(user_id, provider_key)`、`display_name_snapshot`、`email_snapshot`、`last_login_time` |

**为什么密码单独一张表**：纯三方登录的账号可以**没有密码**（`sys_local_credential` 里没有对应行）。这让"设置密码"变成一个显式动作，也让"解绑最后一个登录方式"的检查变得简单（有凭据且有密码才算有密码）。

**`sys_external_identity` 的两个唯一索引的分工**：
- `(provider_key, issuer, subject)` → 一个外部身份只能绑一个账号；
- `(user_id, provider_key)` → 一个账号在同一平台只能绑一个身份。
这两条在 `RegistrationService.bind()` 和 `ExternalLoginPersistence.bind()` 里都有对应的应用层预检（越权/重复绑定时的友好提示），数据库唯一索引是最后的兜底。

#### 权限模型（RBAC）

| 表 | 用途 | 说明 |
| --- | --- | --- |
| `sys_role` | 角色 | `role_code`(UK)、**`data_scope`**(ALL/DEPT_AND_CHILDREN/DEPT/SELF)、`status`、`builtin`、`deleted` |
| `sys_menu` | 菜单/按钮（**同一张表**） | `menu_type`(DIR/MENU/BUTTON)、`route_name`、`route_path`(UK)、`component`、`permission_code`(UK)、`icon`、`visible`、`keep_alive`、`builtin`、`deleted` |
| `sys_user_role` | 用户 ↔ 角色 | 复合 PK `(user_id, role_id)` |
| `sys_role_menu` | 角色 ↔ 菜单 | 复合 PK `(role_id, menu_id)` |

**`sys_menu` 用一张表同时表达"目录 / 页面 / 按钮"三种东西**，靠 `menu_type` 区分：
- `DIR`：只有 `menu_name` + `icon`，侧边栏的分组；
- `MENU`：有 `route_path` + `component`（对应前端 `src/pages/` 下的真实文件）；
- `BUTTON`：有 `permission_code`，不对应任何路由，只用于授权与后端校验。

**⚠️ 没有 `sys_role_dept` 表。** 数据范围目前只能从"用户自己的部门"推导，**不支持"给角色指定若干个部门"**。这不是遗漏的遗留代码，而是**这个功能压根还没做** —— 完整落地方案见 `DATA-SCOPE-ROLE-DEPT.md`。

#### 审计日志

| 表 | 用途 | 索引 |
| --- | --- | --- |
| `sys_login_log` | 登录/登出/失败/锁定记录 | `(login_time)` |
| `sys_operation_log` | 操作审计（AOP 自动记录） | `(created_time)`、`(module, created_time)` |

`sys_login_log` 有 `result`(SUCCESS/FAILURE/LOCKED) 和 `message`（**真实失败原因在这里，不在接口响应里**）。
`sys_operation_log` 有 `duration_ms`（耗时）、`module`、`action`。

#### 样例业务模块

| 表 | 用途 |
| --- | --- |
| `sys_department_change_request` | 部门变更申请主单：`requester_id`、`from_dept_id`、`target_dept_id`、`reason`、`status`、`current_step`、`version`(乐观锁)、`finished_time` |
| `sys_department_change_step` | 审批步骤：`request_id` + `step_order`(UK)、`step_type`(SOURCE/TARGET)、`dept_id`、`assigned_user_id`、`status`、`decided_by`、`decision_reason`、`decided_time` |
| `sys_notification` | 站内通知：`recipient_id`、`type`、`title`、`content`、`business_type`、`business_id`、`read_time` |

**这三张表是业务开发的参考模板**：主单-明细结构、两步审批状态机、乐观锁 `version`、`business_type`/`business_id` 关联业务对象的通知模式。

### 6.2 数据库约定（新增表请照抄）

1. **逻辑删除**：`deleted TINYINT NOT NULL DEFAULT 0` + `deleted_time DATETIME NULL`。实体上标 `@TableLogic`。
   ⚠️ **唯一索引看不到 `deleted`**，所以查重必须用"含已删除"口径（见 `ARCHITECTURE.md` 1.7）。
2. **审计四件套**：`created_by`、`created_time`、`updated_by`、`updated_time`。由 `MybatisConfig.auditHandler`（`MetaObjectHandler`）自动填充，`created_by` 从 `SecurityContext` 的 `CurrentUser` 取。
3. **`builtin TINYINT`**：系统内置记录（admin 用户、根部门、super_admin 角色、内置菜单）标记，**不可删除**（只有部分可改）。
4. **`status TINYINT`**：1=启用，0=停用。停用的角色不授予任何权限；停用的部门不接收新用户（但在其中的人不受影响）。
5. **`sort_order INT`**：同层排序。

### 6.3 初始数据（`init_data.sql`）

| 内容 | 值 |
| --- | --- |
| 部门 | 1 根部门(ROOT, builtin) / 2 技术部(TECH) / 3 产品部(PRODUCT) |
| 角色 | 1 超级管理员(`super_admin`, ALL, builtin) / 2 系统管理员(`system_admin`, DEPT_AND_CHILDREN, builtin) / 3 基础用户(`basic_role`, SELF, builtin) |
| 用户 | 1 `admin` / `Admin@123`（BCrypt 哈希），`builtin=1`，**`password_change_required=1`** |
| 菜单 | 首页 + 系统管理（用户/角色/部门/菜单/登录日志/操作日志）+ 24 个按钮权限 |
| 授权 | `super_admin` = 全部；`system_admin` = 仅首页 + 用户管理 + 用户的增删改查（**不含重置密码与导出**）；`basic_role` = 仅首页 |

---

## 七、Redis key 清单（完整）

**这是"Redis 里到底存了什么"的权威答案。** 全部 key 前缀集中在 6 个文件里。

| Key 模式 | 类型 | TTL | 写入方 | 用途 |
| --- | --- | --- | --- | --- |
| `auth:session:<sid>` | String(JSON) | 记住我 7天 / 否则 2小时；开户态 30min | `SessionService` | **服务端会话主体**。每个请求都会被读取校验 |
| `auth:session:absolute:<sid>` | String("1") | 14天（开户态 2小时） | `SessionService` | "记住我"的**绝对上限标记**，永不被续期。`touch()` 取它与空闲 TTL 的较小值 |
| `auth:user:sessions:<userId>` | Set | 随最长会话 | `SessionService` | 该用户全部 sid 索引 → 登录设备列表 / 踢出其他设备 / 全部退出 |
| `auth:login:fail:account-source:<loginKey>:<ipHash>` | String(计数) | 15分钟 | `LoginAttemptGuard` | 「账号+来源」失败计数，达到 5 次触发锁定 |
| `auth:login:lock:account-source:<loginKey>:<ipHash>` | String("1") | 15分钟 | `LoginAttemptGuard` | 「账号+来源」锁定标记 |
| `auth:login:fail:source:<ipHash>` | String(计数) | 15分钟 | `LoginAttemptGuard` | 来源级失败计数（防密码喷洒），达到 20 次 |
| `auth:login:lock:source:<ipHash>` | String("1") | 15分钟 | `LoginAttemptGuard` | 来源级锁定标记 |
| `auth:quota:<bucket>:<ipHash>` | String(计数) | 各入口自定 | `LoginAttemptGuard` | 通用来源配额。现有 bucket：`captcha`(60/分钟)、`external-start`(30/分钟) |
| `auth:captcha:<captchaId>` | String(4位码) | **2分钟** | `CaptchaService` | 图形验证码答案（**明文**），校验时 `getAndDelete` 一次性消费 |
| `auth:password-change:fail:<userId>` | String(计数) | 15分钟 | `UserService` | 修改本人密码时"原密码错误"计数，5 次触发限流 |
| `auth:external:transaction:<state>` | String(JSON) | **10分钟** | `ExternalLoginService` | 三方登录事务：providerKey、PKCE verifier、redirectUri、returnTo、rememberMe、targetUserId、initiatingSid。回调时 `getAndDelete` 一次性消费 |
| `realtime:ticket:<ticket>` | String("userId\|sid") | **60秒** | `RealtimeTicketService` | WebSocket 握手票据，`getAndDelete` 一次性消费 |

**几个值得注意的点**：

- **`auth:login:*` 前缀是刻意不改名的**（`LoginAttemptGuard.java:152-154`）：改名不会带来收益，却会让上线瞬间所有在途计数"凭空归零"，增加排查限流问题的不确定性。查 key 时按这个前缀找。
- **所有限流 key 里的 IP 只存 SHA-256**，不存原值。
- **所有"一次性"的东西都用 `getAndDelete`**：验证码、三方登录 state、WebSocket 票据。这是防重放的核心手段。
- **Redis 不可用时**：`JwtAuthenticationFilter` 会走进 `catch (Exception)` 分支并 `log.warn`，请求以未认证身份继续 → 401。也就是说 **Redis 挂了 = 全站登录态失效**，但不会 500。

### 用 redis-cli 排查的常用命令

```bash
# 看某人的所有会话 sid
SMEMBERS auth:user:sessions:1

# 看某个会话内容（含登录 IP、UA、登录方式、记住我）
GET auth:session:<sid>

# 清理某人的全部登录状态（用户被锁在外面时的应急手段）
# ⚠️ 应用层不会感知，但这些 sid 对应的令牌下次请求就会失效
SMEMBERS auth:user:sessions:1 | xargs -I{} redis-cli DEL auth:session:{} auth:session:absolute:{}
DEL auth:user:sessions:1

# 看谁正在被限流锁着
KEYS auth:login:lock:*

# 解除某个账号+来源的锁定（把 <loginKey> 换成小写用户名、<ipHash> 换成哈希值）
DEL auth:login:lock:account-source:<loginKey>:<ipHash>

# 看是否有卡住的验证码 / 三方登录事务（一般不用管，都带 TTL）
KEYS auth:captcha:*
KEYS auth:external:transaction:*
```

**应急手段：直接改库让某人重新登录**（当 Redis 也不方便操作时）：
```sql
UPDATE sys_user SET auth_version = auth_version + 1 WHERE username = 'xxx';
```
下一次请求时过滤器就会发现 `authVersion` 不匹配，把该用户所有凭据判为失效。
