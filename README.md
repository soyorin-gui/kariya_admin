# Kariya Admin

一个基于 Spring Boot、React、MySQL 与 Redis 的通用后台管理系统雏形。项目包含本地密码登录、第三方登录、服务端会话、RBAC 权限、部门数据范围、动态菜单、审批、通知、操作审计、WebSocket 实时事件以及 OpenAI 兼容 Agent 框架。

这份 README 面向第一次接手项目的人。阅读完后，应该能够回答下面几个问题：

- 用户从登录到进入页面，前后端分别做了什么；
- 菜单、角色、权限码、数据范围和前端动态路由如何协作；
- 审批、通知和多实例 WebSocket 如何传递数据；
- AI 助手如何调用模型、如何为工具做鉴权，以及当前还缺什么；
- 新增业务模块时，哪些代码必须写，哪些现成封装可以复用。

> 文档中的“逐类、逐文件”不重复列举 Lombok 自动生成的 getter/setter、Java record 自动生成的访问器，以及纯 TypeScript interface 的每一个字段访问器；这些文件会说明其数据职责。包含业务判断的方法会单独解释。

## 1. 技术栈和目录

| 层 | 技术 |
| --- | --- |
| 后端 | Java 17、Spring Boot 3.4、Spring Security、MyBatis-Plus、Validation、Actuator |
| 数据 | MySQL、Redis |
| 实时通信 | 原生 Spring WebSocket、Redis Pub/Sub |
| AI | OpenAI Chat Completions 兼容协议、SSE、Tool Calling 抽象 |
| 前端 | React 18、TypeScript、Vite、React Router、Redux Toolkit、Ant Design |
| 可视化 | ECharts、AntV G6、Monaco Editor |

```text
kariya_admin/
├─ backend/                         Spring Boot 应用
│  ├─ src/main/java/org/lbl/       Java 源码
│  ├─ src/main/resources/          yml 配置与 SQL
│  └─ src/test/java/               单元测试
├─ frontend/                        React SPA
│  ├─ src/                          前端源码
│  └─ dist/                         生产构建产物
└─ README.md                        本文档
```

后端遵循 `Controller → Service → Mapper → 数据库/Redis` 的主分层。Agent 模块额外使用端口与适配器结构，使模型供应商、HTTP/SSE 和业务工具彼此隔离。前端遵循 `页面/组件 → api → request → 后端`，全局身份和通知状态放入 Redux。

## 2. 快速启动

### 2.1 后端

1. 安装 Java 17、Maven、MySQL、Redis。
2. 根据环境设置数据库、Redis、JWT、Cookie 和外部登录配置。不要把生产密码或模型密钥写进仓库。
3. 首次建库执行 `backend/src/main/resources/db/init_schema.sql`。
4. 如果是已有数据库，还要执行相应迁移脚本，例如：
   - `add_role_dept.sql`：角色自定义部门数据范围；
   - `migrate_operation_log_audit.sql`：增强操作审计字段。
5. 在 `backend` 下运行：

```bash
mvn spring-boot:run
```

健康端点：

- `/actuator/health`：总体状态；
- `/actuator/health/liveness`：进程是否存活；
- `/actuator/health/readiness`：数据库与 Redis 是否可用。

健康详情对匿名调用者隐藏，防止泄露数据库和 Redis 信息。

### 2.2 前端

在 `frontend` 下运行：

```bash
npm install
npm run dev
```

生产构建：

```bash
npm run build
```

`VITE_API_BASE_URL` 指向后端 API 根地址。同源部署时可以留空，由浏览器使用当前 origin。

## 3. 总体架构与数据流

```text
浏览器
  │
  ├─ REST/JSON ──> Controller ──> Service ──> Mapper ──> MySQL
  │                    │             └───────────────> Redis
  │                    └─ @PreAuthorize / AccessPolicy
  │
  ├─ SSE ────────> AgentChatController ──> AgentRunner ──> ModelGateway
  │                                           │
  │                                           └─ ToolExecutionService ──> 业务用例
  │
  └─ WebSocket <── RealtimeGateway <── Redis Pub/Sub <── NotificationService
```

几个重要边界：

- JWT 只证明“这个访问令牌对应哪个会话”，真实会话仍保存在 Redis；
- 前端菜单只决定展示和路由体验，后端 `@PreAuthorize` 与 `AccessPolicy` 才是安全边界；
- 通知先落 MySQL，再在事务提交后发送实时事件；WebSocket 丢消息时可通过 REST 补拉；
- Agent 不直接访问 Controller 或数据库，必须通过显式注册、再次鉴权的工具调用业务 Service。

## 4. 一个用户登录后的完整流程

### 4.1 登录

1. `frontend/src/pages/login/index.tsx` 收集用户名、密码和“记住我”。
2. `frontend/src/api/auth.ts` 调用 `POST /api/auth/login`。
3. `AuthController.login()` 调用 `AuthService.login()`：
   - `LoginAttemptGuard` 检查账号与来源 IP 的失败次数；
   - 查询 `sys_user` 与 `sys_local_credential`；
   - BCrypt 校验密码；
   - `PasswordChangePolicy` 判断是否需要强制改密；
   - `SessionService` 在 Redis 创建服务端会话；
   - `JwtService` 签发短期 access token；
   - `LoginLogService` 写登录日志。
4. `RefreshCookieFactory` 把 Redis 会话 ID 写入 HttpOnly Cookie。Cookie 路径限定在 `/api/auth`，普通业务请求不会携带它。
5. 前端把 access token 保存在 Redux 内存中，不写 localStorage。

“记住我”并不是一个永不过期的 JWT：它改变 Redis 会话的空闲期、绝对有效期和 Cookie 持久性。具体时长统一由 `SecurityProperties` 与 `SessionLifetimePolicy` 计算。

### 4.2 恢复身份和进入首页

1. `AuthGuard` 检查 Redux 是否已有 token 和已加载的 profile。
2. 页面刷新后 Redux 内存为空，`refreshAccessTokenOnce()` 使用 HttpOnly Cookie 调 `/api/auth/refresh` 换取新 token。
3. `fetchMe()` 调 `/api/auth/me`，拿到：
   - 当前用户；
   - 权限码集合；
   - 当前用户可访问的完整菜单树；
   - 全站页面路由目录，用来区分 403 和 404。
4. `authSlice.setProfile()` 保存资料并标记 `profileReady=true`。之后普通页面切换不再重复请求 `/auth/me`。
5. 根路由由 `MenuHomeRedirect` 跳向当前账号第一个可见页面；没有任何页面权限时显示明确空状态。

### 4.3 每次业务请求如何认证

1. `utils/request.ts` 从 Redux 取 access token，写入 `Authorization: Bearer ...`。
2. 后端 `JwtAuthenticationFilter` 解析 JWT，随后从 Redis 查会话。
3. 对成员会话，它继续校验用户启用状态、`authVersion`、会话用户名与 JWT subject。
4. 密码待更新用户暂时不加载普通菜单权限，只允许完成改密。
5. 通过后，把 `CurrentUser` 和权限码写入 Spring SecurityContext。
6. Controller 上的 `@PreAuthorize` 校验功能权限；Service 中的 `AccessPolicy` 校验数据范围和目标对象层级。

如果接口返回 401，Axios 和 AI 的原生 fetch 都调用同一个 `refreshAccessTokenOnce()`。多个并发 401 只发一次刷新请求，然后各自重试。刷新失败才清空 Redux 并回登录页。403 表示身份有效但权限不足，不触发刷新。

### 4.4 活跃续期与退出

- `UserActivityManager` 只在真实鼠标、键盘等交互后调用 `/auth/touch`，避免空闲页面制造续期流量；
- `SessionService.touch()` 只延长空闲期限，不突破绝对期限；
- 退出登录删除 Redis 会话、清 Cookie，并通过 realtime 关闭该会话对应的 WebSocket；
- 用户被禁用、角色变化、密码修改等操作会提高 `authVersion` 或删除会话，使旧 JWT 即使尚未到期也无法继续使用。

## 5. 权限、菜单与动态路由

### 5.1 权限模型

```text
用户 sys_user
  └─ sys_user_role
       └─ 角色 sys_role
            ├─ sys_role_menu ──> 菜单/按钮 sys_menu
            └─ sys_role_dept ──> CUSTOM 数据范围部门
```

`sys_menu.menu_type`：

- `DIR`：侧边栏目录，不对应页面；
- `MENU`：页面，包含 `route_path` 与 `component`；
- `BUTTON`：操作权限点，主要提供 `permission_code`，不渲染路由。

角色的数据范围包括 `ALL`、`DEPT_AND_CHILDREN`、`DEPT`、`SELF`、`CUSTOM`。`AccessPolicy` 会按具体操作权限计算数据范围，而不是简单地给用户一个全局范围。

### 5.2 管理员授权之后发生什么

以“角色授权”为例：

1. 角色页面加载可授予菜单树；非超级管理员看不到自己不能再授权的节点。
2. 管理员勾选后调用 `PUT /api/system/roles/{id}/menu-ids`。
3. `RoleService.grantMenus()`：
   - 校验角色是否可管理；
   - 防止授予操作者自己没有的权限；
   - 防止把菜单定义、重置他人密码等平台级权限授给普通角色；
   - 更新 `sys_role_menu`；
   - 找出受影响用户，使其旧会话失效；
   - 写操作日志。
4. 被影响用户需要重新获取身份资料。旧 JWT 因会话或授权版本失效不能继续沿用旧权限。
5. 新登录时 `MenuMapper.selectByUserId()` 根据角色、菜单状态和父子关系返回授权后的菜单树。
6. 前端侧边栏、全局搜索和 `DynamicPage` 全部使用这同一份菜单树。

### 5.3 前端动态路由

静态路由只登记登录、注册、开户、账号安全、通知、审批和错误页。业务菜单统一进入 `DynamicPage`：

1. 当前 pathname 在“我的菜单”中：读取菜单的 `component`；
2. `componentRegistry` 通过 Vite `import.meta.glob` 找到 `src/pages` 下对应 `.tsx`；
3. `React.lazy` 懒加载页面，并缓存组件类型；
4. 找到路由但找不到文件：显示 `PageUnavailable`，指出应创建的文件；
5. 全站目录存在但我的菜单不存在：显示 403；
6. 全站目录也不存在：跳 404。

`visible=0` 只表示不出现在侧边栏，仍允许通过 URL 访问。`keepAlive=1` 的页面切换后不会卸载，由 `DynamicPage` 保持组件实例。

## 6. 后端代码说明

### 6.1 应用、公共设施与配置

| 类/文件 | 作用 |
| --- | --- |
| `LblShitApplication` | Spring Boot 主入口，启用配置属性与定时任务。 |
| `config/AppConfig` | 公共 Bean 和框架级配置。 |
| `config/MybatisConfig` | MyBatis-Plus 分页、字段自动填充等配置。 |
| `config/SecurityProperties` | JWT、会话时长、Cookie、CORS、可信代理等安全配置。 |
| `config/WebSecurityConfig` | Stateless SecurityFilterChain、公开路径、401/403 JSON、CORS、JWT Filter 顺序。 |
| `config/SystemPermissionInitializer` | 启动后补齐系统内置权限数据；需要数据库可用。 |
| `common/result/Result` | 统一响应 `{code,message,data,requestId}`。 |
| `common/result/PageResult` | 统一分页 `{records,total,pageNum,pageSize}`。 |
| `common/filter/RequestLoggingFilter` | 生成/透传 Request ID，写 MDC 与响应头。 |
| `common/exception/*` | 业务异常、未认证、限流异常以及全局异常到 HTTP 状态的映射。 |
| `common/util/TextValues` | 统一字符串 trim/null 处理。 |
| `ExcelExportColumn`、`ExcelExportUtil` | SXSSF 流式 Excel 导出，避免全量记录驻留内存。 |
| `TrustedProxyResolver` | 只信任配置过的反向代理，安全解析真实客户端 IP。 |

### 6.2 认证和会话

#### Controller

| 类 | 公开方法 |
| --- | --- |
| `AuthController` | `login` 登录；`register` 注册；`refresh` 换 token；`touch` 延长空闲期；`logout` 删除会话；`me` 返回身份、权限和菜单。 |
| `CaptchaController` | 获取图形验证码配置和题目。 |
| `ExternalAuthController` | 列出第三方提供方；`start` 创建 OAuth/OIDC 事务并重定向；`callback` 校验回调并登录、绑定或进入开户态。 |
| `OnboardingAccountController` | 第三方身份未绑定时创建新账号或绑定已有账号，并把临时会话替换成正式会话。 |
| `AccountProfileController` | 读取和修改本人的联系方式。 |
| `AccountSessionController` | 查看本人的登录设备、退出某台设备、退出其他设备。 |
| `AccountIdentityController` | 查看、绑定、解绑第三方登录身份。 |

#### Service 和安全组件

| 类 | 方法与职责 |
| --- | --- |
| `AuthService` | `login` 完成本地密码认证；`refresh` 校验 Redis 会话、用户状态和 authVersion 后签新 JWT；`touch` 续空闲期；`logout` 删除会话并记日志。 |
| `RegistrationService` | `register` 普通注册；`createFromOnboarding` 用已验证外部身份开户；`bindFromOnboarding` 把身份绑定到已有账号；内部统一用户名、密码、角色和身份冲突校验。 |
| `CaptchaService` | 创建、存储、消费验证码；签名/一次性校验并限制频率。 |
| `LoginAttemptGuard` | `loginKey/currentSource` 生成限流维度；`isLocked`、`recordFailure`、`recordSuccess` 管理登录失败计数；`requireSourceQuota` 做来源级配额。 |
| `PasswordRules` | 密码复杂度校验、确认密码校验和合规临时密码生成。 |
| `PasswordChangePolicy` | 根据强制改密标志和密码使用天数判断是否必须更新。 |
| `JwtService` | 签发、解析和验证短期 JWT。 |
| `JwtAuthenticationFilter` | 每个 Bearer 请求校验 JWT + Redis 会话 + 用户版本，构造成员或开户态 Authentication。 |
| `SecurityResponseWriter` | 把 Spring Security 的 401/403 写成统一 Result JSON。 |

#### Session 包

| 类 | 方法与职责 |
| --- | --- |
| `LoginSession` | Redis 会话快照，区分 MEMBER/ONBOARDING，保存用户、版本、登录方式、创建/活跃时间等；`touch` 和 `withPasswordChangeRequired` 返回更新后的不可变快照。 |
| `SessionLifetimePolicy` | `idle`、`absolute`、`cookieMaxAge` 从统一安全配置计算空闲期、绝对期和 Cookie 时长。 |
| `RefreshCookieFactory` | `createFor` 签发统一属性的 HttpOnly Cookie；`clear` 生成完全同属性的删除 Cookie。 |
| `SessionService` | `create/createMember` 创建成员会话；`createOnboarding` 创建临时开户会话；`find` 校验并读取；`touch` 续期；`updatePasswordChangeRequired` 更新状态；`listMemberSessions` 生成设备列表；`removeByReference/removeAllExcept/remove/removeAll` 安全删除会话并关闭对应 WebSocket。 |
| `SessionView` | 返回前端的脱敏设备会话，不暴露真实 sid。 |

#### 外部身份

| 类 | 作用 |
| --- | --- |
| `ExternalAuthProperties` | OAuth2/OIDC/SAML 提供方、URL、claim、scope、代理等配置模型。 |
| `ExternalLoginService` | 生成授权地址和 PKCE/state；交换 code；拉取用户资料；校验 OIDC issuer/subject；决定登录、绑定或开户；只通过 `ExternalLoginPersistence` 写数据库。 |
| `ExternalLoginPersistence` | 用独立事务持久化外部身份绑定，处理唯一性冲突。 |
| `LoginTransaction` | Redis 中的一次性 OAuth state/PKCE/returnTo 事务。 |
| `ExternalLoginCallbackException` | 携带安全前端回跳位置的回调异常。 |
| `ExternalIdentityEntity/Mapper` | `sys_external_identity` 的实体与查询。 |
| `LocalCredentialEntity/Mapper` | 本地密码哈希、启用状态、强制改密和改密时间。 |
| `VerifiedIdentity` | 已验证外部身份的内部标准结构。 |
| `uias/*` | 企业统一认证适配端口、断言消费与 UIAS 登录编排；具体企业 SDK 通过 `EnterpriseDirectoryPort` 接入。 |
| `auth/model/*` | 登录、注册、开户、绑定请求与 `LoginResult/SessionGrant` 响应模型。 |

### 6.3 AccessPolicy：真正的数据权限核心

`security/context/AccessPolicy` 是项目最重要的授权类之一：

- `actor()` 读取当前用户、有效角色、权限码、部门树和自定义部门，生成一次请求内的授权快照；
- `actor(permission...)` / `forPermissions()` 为具体操作选择其角色数据范围；
- `prepareRoles()` 批量预取角色权限和 CUSTOM 部门，避免逐行 N+1；
- `roleScope()` 把角色的 ALL/DEPT/SELF/CUSTOM 转为统一 `Scope`；
- `applyUserScope()` 把用户查询限制在允许的数据范围；
- `canManageUser/requireManageUser()` 防止管理范围外、同级或更高权限用户；
- `canAssignRole/requireAssignableRoles()` 防止把自己没有或范围更大的角色授出去；
- `canManageRole/requireManageRole()` 控制角色维护层级；
- `requireGrantableMenus()` 防止下放自身没有的菜单；
- `platformOnlyMenuIds/omitPlatformOnlyMenus/requireNotPlatformOnly()` 集中保护平台级权限；
- `canManageDept/requireCreateDept/requireMoveDept()` 控制部门树维护；
- `requiresPlatformTransferReview()` 判断部门变更是否会改变动态角色范围；
- `Scope.union/contains()` 负责范围合并与包含判断。

`CurrentUser` 是成员 principal；`OnboardingPrincipal` 是临时开户 principal。两者不能混用，成员接口通常显式排除 `onboarding:access`。

### 6.4 用户、角色、菜单、部门

每个模块的 `Entity` 映射数据库，`Mapper` 负责 SQL，`Request` 负责输入校验，`VO` 负责输出，`Controller` 只做 HTTP 编排。

#### 用户模块

| 类 | 主要方法 |
| --- | --- |
| `UserController` | 用户分页、详情、表单选项、用户名可用性、导出、新增、修改、删除、重置密码、会话管理和本人改密端点。 |
| `UserService` | `page/detail/formOptions` 查询；`create/update/remove` 用户事务；`currentContactProfile/updateOwnContact` 本人资料；`checkUsername` 同时检查逻辑删除数据的唯一约束；内部统一角色分配和可管理性计算。 |
| `UserPasswordService` | `reset` 生成临时密码；`changeOwn` 校验旧密码并限制 15 分钟内失败次数；成功后提高 authVersion、踢出全部会话。 |
| `UserSessionAdministrationService` | `list/remove/removeAll` 管理员侧设备会话用例，操作前再次校验目标用户层级。 |
| `UserExportService` | 按数据范围分页读取并流式导出，避免一次加载全部用户。 |
| `UserMapper` | 用户查询、带逻辑删除的用户名计数、行锁、批量权限投影等 SQL。 |
| `UserRoleMapper` | 用户角色中间表增删。 |
| `UserEntity` | `sys_user` 映射；包含 authVersion、内置标记、逻辑删除审计字段。 |
| `request/*` | 用户新增、更新、联系方式、改密的 Validation 输入。 |
| `vo/*` | 列表轻量 VO、编辑详情 VO、表单选项、角色/权限批量投影、联系方式和用户名可用性。 |

#### 角色模块

| 类 | 主要方法 |
| --- | --- |
| `RoleController` | 列表、详情、可授予部门、增改删、读取/保存菜单授权。 |
| `RoleService` | 角色 CRUD；数据范围校验；CUSTOM 部门保存；菜单授权；受影响用户会话失效；内置角色保护。 |
| `RoleMapper` | 角色与用户计数、按用户查询、批量角色投影。 |
| `RoleMenuMapper` | 角色菜单关系、权限码和菜单 ID 的批量查询。 |
| `RoleDeptMapper` | CUSTOM 数据范围的角色部门关系。 |
| `RoleEntity/RoleRequest/RoleVO` | 数据库存储、校验输入和管理界面输出。 |
| `RoleDeptAssignment/Option`、`RolePermissionAssignment`、`RoleUserCount` | Mapper 批量查询使用的轻量投影。 |

#### 菜单模块

| 类 | 主要方法 |
| --- | --- |
| `MenuController` | 菜单树查询、增改删。 |
| `MenuService` | 校验菜单类型、父级、routePath、component、permissionCode；防止环；保护内置/平台菜单；维护菜单后使相关会话失效。 |
| `MenuMapper` | 查询用户授权菜单、全站页面目录、角色菜单和批量权限码；递归补齐父节点。 |
| `MenuEntity/MenuRequest` | 菜单表映射和表单输入。 |

#### 部门模块

| 类 | 主要方法 |
| --- | --- |
| `DeptController` | 部门树、详情、表单选项和增改删。 |
| `DeptService` | 按数据范围返回树；新增/移动时重算 ancestors；阻止循环、越界移动、有用户或子部门时删除。 |
| `DeptMapper` | 部门查询以及用户/子部门引用计数。 |
| `DeptPaths` | 统一 ancestors 路径拼接、子树包含判断，避免各 Service 自己处理字符串。 |
| `DeptEntity/DeptRequest/DeptVO/DeptFormOptions` | 数据表、校验输入和界面所需能力标志。 |

### 6.5 审批与通知

#### 部门变更审批

| 类 | 作用 |
| --- | --- |
| `DepartmentTransferController` | 部门选项、本人资料、申请详情、提交、通过、拒绝、撤销端点。 |
| `DepartmentTransferApprovalService` | 创建原部门/目标部门两阶段步骤；选择负责人；锁定申请避免并发审批；禁止自审；完成后更新用户部门；通知申请人和参与者。 |
| `DepartmentTransferModels` | `Submit/Reject/DeptOption/Profile/Detail/Step` 请求与返回模型。 |
| `DepartmentTransferRequestEntity/Mapper` | 审批主单、本人进行中申请、按 ID 行锁和历史查询。 |
| `DepartmentTransferStepEntity/Mapper` | 每个审批步骤、当前步骤和决策记录。 |

#### 统一待办中心

| 类 | 作用 |
| --- | --- |
| `ApprovalTaskController` | `/api/account/approvals` 分页和 `/pending-count`。 |
| `ApprovalTaskService` | 根据 `pending/processed/mine` 分派查询，超级管理员可处理未指定审批人任务。 |
| `ApprovalTaskMapper` | 将申请、当前步骤、用户和部门联表为待办视图。 |
| `ApprovalTaskView` | 前端列表投影；详情和审批动作继续复用具体业务接口。 |

#### 通知

| 类 | 作用 |
| --- | --- |
| `NotificationController` | 最新通知、未读数、单条已读、全部已读。 |
| `NotificationService.create` | 先插入 MySQL，事务提交后发送 `notification.created`；避免回滚数据产生假通知。 |
| `NotificationService.approvalUpdated` | 事务提交后向审批参与者广播刷新事件。 |
| `NotificationMapper` | 最新列表、未读计数和带 recipient 条件的已读更新。 |
| `NotificationEntity` | `sys_notification` 映射，businessType/businessId 用于跳业务详情。 |

### 6.6 Realtime/WebSocket 详解

WebSocket 与普通 HTTP 的区别：HTTP 是“一问一答”，服务器不能在没有请求时主动告诉浏览器“你有新消息”；WebSocket 在一次 HTTP Upgrade 握手后保留双向长连接，服务端可以随时推送事件。

本项目不直接把 JWT 放在 WebSocket URL，也不依赖浏览器无法自定义的握手 Authorization Header，而是使用一次性 ticket：

```text
前端携带 Bearer token POST /api/account/realtime/ticket
    ↓
RealtimeTicketService 在 Redis 保存 30 秒一次性 ticket → userId + sid
    ↓
前端连接 ws(s)://host/ws/realtime?ticket=...
    ↓
握手拦截器 consume ticket（读取后立刻删除）
    ↓
userId/sid 写入 WebSocketSession attributes
    ↓
RealtimeGateway 把连接放入本实例内存 connections[userId][socketId]
```

逐类说明：

- `RealtimeController.ticket()`：从认证 principal 和 Authentication details 获取 userId/sid，确认会话仍存在后签发 ticket。
- `RealtimeTicketService.issue()`：生成随机 32 字节 URL-safe token，在 Redis 保存 30 秒；`consume()` 用 `getAndDelete` 保证只能握手一次；`key()` 统一 Redis key。
- `RealtimeWebSocketConfig.registerWebSocketHandlers()`：注册 `/ws/realtime`；握手前消费 ticket；写 session attributes；按安全配置设置允许来源。
- `RealtimeGateway.afterConnectionEstablished()`：校验 userId，套 `ConcurrentWebSocketSessionDecorator` 防止并发发送与无限缓冲，登记连接并发 `connection.ready`。
- `afterConnectionClosed()`：从本机连接表移除断开的 socket，并清理空用户桶。
- `send()`：不直接发本机，而是发布 Redis 用户事件，使所有实例收到同一事件。
- `sendLocal()`：订阅事件后只查本实例连接并发送 JSON；关闭或异常连接会被清理。
- `closeSession()`：广播按 sid 强制断开事件；`closeSessionLocal()` 只断开本机匹配会话。
- `heartbeat()`：每 25 秒仅向本机连接发 `connection.ping`，防止代理回收空闲连接，不经 Redis，避免实例数倍增心跳。
- `RealtimeEnvelope`：跨实例事件信封；目前有 `USER_EVENT` 和 `CLOSE_SESSION`。
- `RealtimeEventPublisher.userEvent/closeSession/publish`：序列化信封并发到 `kariya-admin:realtime:events`；Redis 发布失败时 Gateway 降级为本机投递。
- `RealtimeEventSubscriber.onMessage()`：每个实例订阅频道，解析后调用 Gateway 的本地方法。
- `RealtimeSubscriptionStarter.ensureStarted()`：监听容器由组件内部托管；Redis 暂时不可用不阻止 Web 应用启动，每 5 秒重试；`close()` 在停机时释放连接。

为什么还需要 REST 通知列表：Redis Pub/Sub 和 WebSocket 都是瞬时通道，断网期间的事件不会补发。真正的数据已经在 MySQL，前端重连、回到可见标签页或收到任意实时刷新信号后，会重新调用 REST 获取权威状态。

### 6.7 Agent 模块详解

#### 当前能力边界

Agent 框架已经具备模型调用、SSE、工具注册、工具可见性、参数校验、二次鉴权、超时、取消、风险和审批策略抽象。但当前业务源码中**没有正式实现 `AgentTool` 的类**，所以现阶段 AI 可以聊天，却不能声称已经查询或修改系统业务数据。`ApprovalPolicy.REQUIRED` 的确认/恢复协议也尚未完成，因此这类工具会被排除或拒绝执行。

#### 一次对话的调用链

```text
AiAssistant
  ↓ useAgentChat.send()
POST /api/agent/chat (SSE)
  ↓ AgentChatController.chat()
身份快照 + deadline + CancellationToken
  ↓ agentExecutor 异步线程
AgentRunner.run()
  ↓
ModelGateway.complete()
  ├─ 模型直接回答 → message/done SSE
  └─ 模型请求工具 → ToolExecutionService.execute()
                         ├─ 查工具
                         ├─ 再次校验权限
                         ├─ 审批门禁
                         ├─ JSON 参数绑定 + Validation
                         ├─ 工具独立 deadline
                         └─ 执行业务用例并把摘要喂回模型
```

#### api

- `AgentChatRequest`：限制 message 和 history；`HistoryRole` 只允许 USER/ASSISTANT，客户端不能伪造 system 消息。
- `AgentChatController.chat()`：要求 `agent:chat:use`；关闭代理缓冲；创建 runId、SSE、用户权限快照、总 deadline 和取消令牌；提交到独立线程池；注册超时、错误和断开回调。
- `run()`：把 Runner 事件写到 SSE；客户端断开就取消运行；正常结束发送 `done`；异常只向用户返回安全提示。
- `emit()`：发送一个结构化事件，写失败返回 false；`completeQuietly()` 幂等结束 emitter。

#### application

- `AgentRequestMapper.toCommand()`：把 Web 请求转换为应用层命令，限制历史消息总字符数，防止无限上下文。
- `AgentCommand`：本轮消息和可信化后的历史。
- `AgentRunner.run()`：
  1. 通知 observer 开始；
  2. 从 Registry 取得当前用户可见且无需未实现审批的工具；
  3. 组装 system prompt、历史和本轮 user 消息；
  4. 最多循环 `maxSteps`；
  5. 模型无工具调用时输出最终文本；
  6. 有工具调用时逐个执行，把 `modelSummary` 作为 tool message 回填，再让模型继续组织答案；
  7. 业务拒绝返回安全业务消息，未知异常只返回通用错误；
  8. 超过步数返回中止提示。
- `toModelMessage()`：领域消息转模型消息；`notifyStarted/ToolStarted/Completed/Failed` 调 observer；`safely()` 保证 observer 失败不拖垮主运行。

#### domain

| 类 | 作用 |
| --- | --- |
| `AgentActor` | 用户 ID、用户名、超管标记、权限快照；`has/hasAll` 判断工具权限。 |
| `AgentDefinition` | Agent ID、名称和不可被用户覆盖的系统指令。 |
| `AgentMessage` | 传输无关的 SYSTEM/USER/ASSISTANT 消息。 |
| `AgentExecutionContext` | runId、actor、deadline、CancellationToken；`checkpoint()` 是每个慢操作前后的取消/超时检查点。 |
| `CancellationToken` | 线程安全取消；`cancel/isCancelled/throwIfCancelled`。 |
| `AgentEvent` | `message/tool_call/tool_result/error/done` 事件工厂，SSE 只是当前传输方式。 |
| `AgentArtifact` | 表格、图表等结构化输出的通用载体。 |
| `AgentRun` | 最终文本与所有工具结果，供测试、审计和未来会话持久化。 |

#### port 和模型适配器

- `ModelGateway.complete()`：应用层唯一模型端口。
- `ModelRequest/Response/Message/ToolCall/ToolDefinition`：与 OpenAI JSON 解耦的内部模型协议。
- `AgentRunObserver`：运行生命周期扩展点；可用于未来审计、指标、计费，不影响主流程。
- `OpenAiCompatibleModelGateway.complete()`：把内部请求转换为 OpenAI Chat Completions JSON，调用网关，映射文本、tool_calls、usage 和 finish_reason。
- `restClient()`：懒创建线程安全客户端，配置 base URL、Bearer API key、User-Agent 和超时。
- `ensureConfigured()`：缺少 baseUrl/apiKey/model 时给出明确错误。
- `fromWire/toWireMessage/toWireTool()`：隔离第三方协议；响应体不直接写日志，避免提示词或工具参数泄露。
- `OpenAiWireModels`：仅供 HTTP 适配器序列化的 wire records。
- `ModelGatewayException`：模型不可用、协议错误的统一异常。

#### tool

- `AgentTool<I,O>`：业务能力接入点；`descriptor()` 描述名称/权限/风险/审批/Schema/超时，`inputType()` 给参数绑定器，`execute()` 只调用业务用例。
- `ToolDescriptor`：构造时规范默认值，包含工具元数据。
- `ToolRegistry`：启动时收集所有 Spring `AgentTool` Bean，校验工具名和重复；`visibleTo()` 按权限过滤；`find()` 查找执行目标。
- `ToolArgumentBinder.bind/bindJson()`：JSON → 输入类型，并执行 Jakarta Validation；错误转成可理解的业务异常。
- `ToolExecutionService.execute()`：每次执行都重新查工具、鉴权、检查审批，不信任模型或“已展示给模型”这一事实。
- `executeTyped()`：建立工具独立 deadline，执行并强制要求非空 `modelSummary`。
- `ToolResult`：`of()` 返回普通结果；`artifact()` 返回结构化制品。`modelSummary` 给模型，`output/artifact` 给程序和前端，避免把大对象全塞回上下文。
- `ToolRisk`：工具风险等级；`ApprovalPolicy`：是否要求用户确认。

#### 新增第一个 Agent 工具

推荐把实现放在所属业务模块，例如 `system/user/adapter/agent/UserSummaryTool.java`：

1. 定义有 Validation 的输入 record；
2. 实现 `AgentTool<Input, Output>` 并注册为 Spring Bean；
3. descriptor 使用稳定的 snake_case 名称，声明最小权限、风险、审批策略、JSON Schema 和超时；
4. `execute()` 只调用已有 Service，不直接使用 Mapper、Controller 或 SecurityContext；
5. 用 `context.actor()` 传递用户身份，业务 Service 仍需做数据范围校验；
6. 返回简短 `modelSummary`，敏感或大量数据放 artifact；
7. 为“不可见、无权限、参数非法、超时、取消、业务失败”分别写测试；
8. 写操作工具在确认协议完成前不要设置成可直接执行，也不要用 `NOT_REQUIRED` 绕过。

### 6.8 文件上传、日志与审计

| 类 | 作用 |
| --- | --- |
| `FileUploadController` | 上传文件到暂存区并返回 token。 |
| `FileUploadProperties` | 目录、大小、扩展名、MIME、TTL、清理周期。 |
| `StagedFileStorage` | 暂存存储端口；`LocalStagedFileStorage` 是本地文件实现，校验扩展名/大小/MIME、生成随机文件名并定时清理。 |
| `StagedFile/StagedUpload` | 内部文件句柄和前端上传结果。 |
| `LoginLogController/Service/Entity/Mapper` | 登录日志分页、物理删除和写入。 |
| `OperationLog` | 显式标注应审计的写操作，可配置目标类型和 SpEL 目标字段。 |
| `OperationLogAspect` | 环绕方法，记录成功/失败、失败原因和耗时；即使业务事务回滚也调用独立审计事务。 |
| `OperationLogService` | 填充操作人、对象、IP、HTTP、User-Agent、Request ID；分页搜索；批量删除。 |
| `RequestInfo` | 从当前请求和可信代理解析审计字段。 |
| `LogRetentionProperties/Job` | 按配置定时、分批物理清理过期登录/操作日志。 |
| `LogResult` | SUCCESS、FAILURE、LOCKED 等统一状态。 |

## 7. 前端代码说明

### 7.1 启动、状态、请求与路由

| 文件 | 作用 |
| --- | --- |
| `main.tsx` | React 入口，组合 Redux、主题、Ant Design App 与 RouterProvider。 |
| `store/index.ts` | 创建 Redux store，挂载 auth 和 notifications reducer。 |
| `store/hooks.ts` | 类型安全的 dispatch/selector。 |
| `store/authSlice.ts` | token、principal、用户、权限、菜单、路由目录和 profileReady；`clearSession` 清认证；`setAccessToken/setProfile/setPasswordChangeRequired` 更新身份。 |
| `store/notificationSlice.ts` | 通知列表、未读数、加载状态；异步加载和已读操作让顶栏与消息页共享同一状态。 |
| `utils/request.ts` | Axios 实例、Bearer 注入、统一进度、401 单次续期重试、错误传递。 |
| `services/authSession.ts` | 全应用唯一 refresh promise；`authenticatedFetch` 为 SSE/fetch 提供同样的 Bearer 与续期；`expireSession` 只处理一次过期跳转。 |
| `services/progress.ts` | 顶部进度条引用计数，避免并发请求提前结束动画。 |
| `components/common/AuthGuard.tsx` | 恢复 token、加载 `/auth/me`、处理开户态和登录跳转；profile 已就绪后不因 pathname 变化重复请求。 |
| `router/index.tsx` | 静态骨架路由；业务菜单落到 `DynamicPage`。 |
| `router/DynamicPage.tsx` | 权限菜单 → 懒加载页面；判断 403/404；实现 keepAlive 和首页落点。 |
| `router/componentRegistry.tsx` | `import.meta.glob` 自动发现 pages；缓存 React.lazy；提供组件清单和缺失文件提示。 |
| `router/menu.ts` | pageMenus、按路径找菜单、首页、开放重定向校验、祖先目录和面包屑。 |
| `router/iconRegistry.tsx` | 菜单图标白名单解析，避免动态执行任意组件名。 |

### 7.2 API 与类型

`src/api` 每个文件只封装一个后端资源，返回业务 `data`，页面不重复拼 URL：

| 文件 | 资源 |
| --- | --- |
| `auth.ts` | 登录、注册、验证码、刷新、me、退出、第三方登录提供方。 |
| `account.ts` | 本人资料、设备会话和第三方身份。 |
| `user.ts` | 用户 CRUD、表单选项、用户名校验、导出、重置密码和管理会话。 |
| `role.ts` | 角色 CRUD、菜单授权、可授予部门。 |
| `menu.ts` | 菜单树和 CRUD。 |
| `dept.ts` | 部门树、详情、表单选项和 CRUD。 |
| `departmentChange.ts` | 部门变更申请、详情、审批、拒绝和撤销。 |
| `approvals.ts` | 统一待办列表和待办数量。 |
| `notifications.ts` | 通知、未读数、已读以及 WebSocket ticket。 |
| `loginLog.ts`、`operationLog.ts` | 日志分页和批量删除。 |
| `file.ts` | 暂存文件上传。 |

`src/types` 中的 `auth/common/user/role/menu/dept/log/file/agent/table.ts` 是前后端契约和通用泛型。接口字段改变时先同步这里，避免页面到处使用匿名对象或 `any`。

### 7.3 布局、权限与实时通知

- `layouts/AppLayout.tsx`：工作台外壳。根据授权菜单构建侧栏；管理展开项、面包屑、全局搜索、审批角标、通知 Popover、设置、用户菜单、改密 Modal、活动续期和 RealtimeClient。
- `layouts/SiderBrand.tsx`：侧栏品牌区；`layout.css`：侧栏、顶栏、内容区、通知面板和响应式布局。
- `permission/Permission.tsx`：按钮级权限渲染；它只改善界面，不能替代后端鉴权。
- `components/common/GlobalSearch.tsx`：只搜索当前账号有权访问的 MENU，匹配名称、面包屑和 path。
- `services/UserActivityManager.ts`：节流监听真实交互，调用 touch。
- `services/RealtimeClient.ts`：先取一次性 ticket，再连接 WebSocket；`start/stop` 管生命周期；`connect` 注册事件；`scheduleReconnect` 使用指数退避和随机抖动，最高约 30 秒。

通知流程：`AppLayout` 启动时 REST 拉取通知并连接 WebSocket；收到 `notification.created` 或 `approval.request.updated` 时重新拉权威列表和审批数量；浏览器标签重新可见时也补拉。消息页和顶栏共用 `notificationSlice`，不会出现一边已读、一边红点不消失。

### 7.4 AI 前端

- `components/ai/AiAssistant.tsx`：悬浮按钮、聊天面板、消息历史、输入、取消、工具状态、artifact 展示和尺寸位置。
- `components/ai/useAgentChat.ts`：`send` 用 authenticatedFetch 发 POST；读取 `ReadableStream`；按 SSE `data:` 行解析；message 逐段累加，tool_call 更新状态，tool_result 交给 artifact 回调，error 显示安全错误；`cancel` 用 AbortController 取消；卸载自动取消。
- `components/ai/artifacts/AgentArtifactView.tsx`：根据 artifact 类型渲染结构化结果；未来可扩展表格、图表、代码或下载。
- `types/agent.ts`：前后端 Agent event、history 和 artifact 契约。

为什么用 SSE 而不是 WebSocket：AI 这一场景主要是“一个请求产生一串响应”，SSE 简单、天然适配 HTTP 超时与代理；通知是服务端在任意时刻主动推送，所以使用长期 WebSocket。

### 7.5 页面文件

| 目录/文件 | 页面职责 |
| --- | --- |
| `pages/login/*` | 密码登录、验证码、记住我、注册入口、第三方登录和品牌视觉。 |
| `pages/register/*` | 本地注册与密码规则。 |
| `pages/auth/CallbackPage.tsx` | 外部登录回调后的 token/profile 恢复和安全 returnTo。 |
| `pages/account/setup/*` | 第三方身份未绑定时创建或绑定账号。 |
| `pages/account/security/*` | 联系方式、外部身份、设备会话和部门变更申请。 |
| `pages/account/notifications/index.tsx` | 通知列表、已读，以及从业务通知打开部门审批详情。 |
| `pages/account/approvals/index.tsx` | 待我审批、我已处理、我的申请，查看进度并审批/拒绝。 |
| `pages/home/index.tsx` | 工作台首页和内置组件示例。 |
| `pages/system/user/index.tsx` | 用户列表、搜索、分页、增改删、导出、密码重置和设备管理；`UserDialog` 是用户表单。 |
| `pages/system/role/index.tsx` | 角色列表；`RoleDialog` 管基本信息和数据范围；`RolePermissionDialog` 管菜单树授权。 |
| `pages/system/menu/index.tsx` | 菜单树管理；`MenuDialog` 使用实际组件清单和 IconPicker，降低错误配置。 |
| `pages/system/dept/index.tsx` | 部门树管理；`DeptDialog` 根据后端能力标志限制父级选择。 |
| `pages/system/loginlog/index.tsx` | 登录日志筛选和删除。 |
| `pages/system/operatelog/index.tsx` | 操作人、对象、请求、Request ID、失败原因和耗时审计。 |
| `pages/error/Forbidden` | 已知页面但无权限；`NotFound` 是未知路径；`PageUnavailable` 是菜单配置存在但前端文件缺失。 |
| 各页面 `*.css` | 页面局部布局；`pages/system/shared.css` 提供系统页共用卡片、标题和弹窗风格。 |

### 7.6 可复用组件、Hook 和工具

| 封装 | 适用场景 |
| --- | --- |
| `SmartTable` | 后端分页列表。封装搜索区、工具栏、刷新、分页、加载和错误回调；系统列表页优先使用。 |
| `FieldLabel` | 统一表单字段标签和帮助提示。 |
| `StatusTag` | 状态值到 Ant Design Tag 的统一映射。 |
| `FileUpload` | 暂存文件上传，返回后端 token，不要在页面自己处理 multipart。 |
| `IconPicker` | 菜单图标选择，和 iconRegistry 配套。 |
| `BaseChart` | ECharts 生命周期、resize 和 option 更新。 |
| `G6Graph` | 图关系可视化生命周期。 |
| `CodeViewer` | Monaco 代码查看、搜索、格式、全屏和下载。 |
| `CopyButton` | 复制反馈。 |
| `SystemSettings` | 主题和界面设置面板。 |
| `LoadingScreen` | 路由和认证加载态。 |
| `useClipboard` | 剪贴板；`useFullscreen` 管全屏；`useResizeObserver` 监听尺寸。 |
| `apiError.getApiErrorMessage` | 从统一 Result/Axios 错误提取用户提示。 |
| `download` | Blob 下载与文件名处理。 |
| `passwordPolicy` | 与后端一致的密码正则和提示。 |
| `theme/*` | Design Token、亮暗主题类型和 ThemeProvider；新 UI 应使用 token/CSS 变量。 |

`styles/reset.css` 清浏览器默认样式；`global.css` 是全局排版；`theme.css` 定义 `--brand`、`--surface`、`--ink`、`--line` 等变量。新页面不要写一套独立颜色系统。

## 8. 三种角色视角

### 8.1 系统使用者

用户可以通过密码或已配置的第三方身份登录。进入系统后只看到自己的角色授权菜单；全局搜索也只返回这些页面。用户可以管理联系方式、外部身份和自己的设备会话，查看通知与审批进度，发起部门变更。拥有 `agent:chat:use` 时会看到 AI 助手。会话过期通常由前端静默续期，只有绝对过期、账号停用或会话被踢出时回登录页。

### 8.2 管理员

管理员的功能权限来自角色，能看到什么数据还取决于该操作权限对应角色的数据范围。管理员可以在自身授权边界内管理用户、部门、角色和菜单；不能管理同级/更高权限对象，也不能下放自己没有的权限。所有关键写操作被 `@OperationLog` 审计。角色、菜单、账号状态、密码等安全信息变化时，系统会使受影响会话失效，避免用户继续使用旧授权。

超级管理员拥有全局范围，但仍受内置对象保护、自审禁止、审批状态机、唯一约束和操作审计约束。

### 8.3 开发者

开发者应先确定“功能权限”和“数据权限”是两件事：

- Controller 用 `@PreAuthorize` 检查是否允许做这类动作；
- Service 用 `AccessPolicy` 检查允许对哪些数据做动作；
- 前端 `Permission` 只隐藏按钮，不承担安全责任；
- Mapper 只负责查询，不应该读取 SecurityContext。

新增业务时，优先复用 Result、PageResult、SmartTable、request、OperationLog、Permission、FileUpload、Realtime 和现有 Service，不要再实现另一套鉴权、分页、上传、审计或 WebSocket。

## 9. 新增功能菜单的标准流程

以新增“项目管理”页面为例：

### 9.1 数据库与后端

1. 新建业务包，例如：

```text
org.lbl.project
├─ controller/ProjectController.java
├─ service/ProjectService.java
├─ mapper/ProjectMapper.java
├─ entity/ProjectEntity.java
├─ request/ProjectRequest.java
└─ vo/ProjectVO.java
```

2. Entity 只映射表；Request 加 Validation；VO 只返回页面需要的数据。
3. Mapper 写持久化查询；Service 写事务、状态机和数据范围；Controller 返回 `Result/PageResult`。
4. 每个动作配置权限码，例如：
   - `project:list`
   - `project:add`
   - `project:update`
   - `project:delete`
5. Controller 用对应 `@PreAuthorize`，Service 仍需校验目标数据。
6. 新增、修改、删除、导出等操作加 `@OperationLog`，尽量填写 targetType 和 targetId。
7. 需要实时提醒时先持久化，在事务提交后通过 NotificationService 或 RealtimeGateway 发轻量刷新事件。
8. 写 Service 单元测试，覆盖越权、边界状态和并发/重复提交。

### 9.2 前端页面

1. 创建 `frontend/src/pages/project/index.tsx`，默认导出组件。
2. 创建 `api/project.ts` 和 `types/project.ts`，不要在页面散写 URL 和匿名类型。
3. 列表页优先使用 SmartTable；弹窗保持 `system-dialog` 和系统页共享风格。
4. 按钮用 `<Permission code='project:add'>...</Permission>` 控制展示。
5. 颜色、间距使用主题变量和 Ant Design，不复制另一套视觉规范。

### 9.3 菜单配置与授权

在菜单管理创建 MENU：

```text
菜单名称：项目管理
路由地址：/project
前端组件：project/index
菜单类型：MENU
```

再创建 BUTTON 子节点并填写权限码。给角色授权页面和按钮，重新登录目标账号验证：

- 侧边栏是否出现；
- URL 是否能访问；
- 无权限账号是否 403；
- 后端直接调用是否仍拒绝；
- 数据范围是否正确；
- 操作日志是否包含对象和 Request ID。

不需要修改 `router/index.tsx` 或手写组件 map。生产环境新增 `.tsx` 后必须重新构建前端，因为 Vite 的页面清单在构建期生成。

## 10. 开发约定和常见陷阱

- 不要把 refresh sid、JWT、密码或模型 API key写入日志。
- 不要把 refresh token 放 localStorage；当前 HttpOnly Cookie 方案用于降低 XSS 窃取风险。
- 不要因为前端隐藏按钮就省略后端鉴权。
- 不要直接用角色名判断权限；使用 permissionCode 和 AccessPolicy。
- 不要让 Service 返回 Entity 给复杂页面，使用 VO 稳定契约。
- 不要在事务提交前发送“成功”通知；回滚后会产生假事件。
- 不要把大业务数据通过 WebSocket 推送；推轻量事件，再 REST 补拉权威数据。
- 不要在 WebSocket URL 传长期 JWT；使用一次性 ticket。
- 不要让 Agent 工具直接调用 Mapper；工具只是适配器，业务规则仍在 Service。
- 不要信任模型选择的工具名、参数或“用户已经同意”的文本；Registry、Validation、权限和审批门禁必须由代码执行。
- 不要为新的列表、上传、错误解析、下载、图表生命周期重复造轮子，先检查第 7.6 节。

## 11. 当前已知边界

- Agent 尚无正式业务工具；审批型 Agent 工具的用户确认/恢复协议尚未实现。
- Agent 对话历史只由当前前端会话携带，尚未持久化为服务端会话记录。
- Redis Pub/Sub 不做离线事件重放，因此通知依赖 MySQL + REST 补拉保证最终可见。
- 数据库脚本目前是显式 SQL 文件，没有引入 Flyway/Liquibase；部署者必须管理迁移执行顺序。
- 前端主包仍然偏大，生产构建会提示大 chunk；后续可继续拆分 Ant Design、图表、Monaco 等 vendor chunk。
- 完整 Spring 上下文测试依赖当前 local profile 的外部 MySQL；CI 应提供独立 test profile 或 Testcontainers。

## 12. 推荐阅读顺序

第一次接手建议按下面顺序阅读：

1. 本 README 的第 3～5 节；
2. `WebSecurityConfig`、`JwtAuthenticationFilter`、`AuthService`、`SessionService`；
3. `AccessPolicy`、`MenuMapper`、`RoleService`；
4. 前端 `AuthGuard`、`request.ts`、`AppLayout`、`DynamicPage`；
5. 选择一个完整 CRUD：用户或角色模块，从页面一路跟到数据库；
6. `DepartmentTransferApprovalService` + NotificationService；
7. Realtime 全包和前端 RealtimeClient；
8. AgentChatController → AgentRunner → ModelGateway → ToolExecutionService。

读代码时始终沿一条真实链路向下追踪，不要按文件名从头到尾孤立阅读。这个项目的大部分设计意图，都体现在“同一条请求在哪一层做什么、哪一层坚决不做什么”。
