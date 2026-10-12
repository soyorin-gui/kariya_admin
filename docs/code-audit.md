# 全项目代码审计报告（仅分析阶段）

> 状态：**审计进行中 / 只读**。本文件由审计会话生成，**不包含任何代码改动**。
> Git 基线：`main` = `origin/main` = `968e214`，审计开始时工作区已有未提交改动（见 §1.4）。
> 审计原则：不以「代码行数减少」为目标；每一处「疑似无用」都必须给出调用关系或运行影响证据。
> **P0 也不代表可以自动删除**——所有条目均需你逐项审批后才进入修改阶段。

---

## 0. 阅读方式与编号约定

### 0.1 问题编号前缀

| 前缀 | 范围 |
| --- | --- |
| `BE-CMN` | 后端 common / config / security 基础设施 |
| `BE-SYS` | 后端 system 模块（用户、部门、角色、菜单、日志） |
| `BE-AUTH` | 后端登录、会话、外部认证（GitHub/Google/WeChat/UIAS） |
| `BE-SEC` | 后端安全过滤链、JWT、代理解析 |
| `BE-VAL` | 参数校验、幂等、锁、防重复 |
| `BE-APR` | 公共审批（approval 包） |
| `BE-NTF` | 通知与实时通道 |
| `BE-REQ` | 内网需求模块 |
| `BE-DA` | 内网数据资产模块（HBase / ES / 关联） |
| `BE-TX` | 内网交易模块 |
| `BE-AGT` | Agent 模块 |
| `BE-LOG` | 日志采集、分析、风险规则、保留策略 |
| `FE-PAGE` | 前端页面 |
| `FE-CMP` | 前端组件 |
| `FE-INF` | 前端 store / api / utils / hooks / 路由 / 类型 |
| `FE-CSS` | 前端样式与 UI 残留 |
| `FE-DEP` | 前端依赖与工程配置 |
| `CFG` | 后端配置、脚本、依赖 |

### 0.2 风险等级

- **P0**：明确冗余，删除风险极低（仍需你确认）。
- **P1**：明显过度设计，适合简化。
- **P2**：疑似过度设计，需要业务确认。
- **P3**：涉及安全、并发、数据一致性或关键业务，需谨慎评估（**默认建议保留**）。

### 0.3 证据可信度

- **高**：有全仓库引用计数 + 调用链证据，或已在构建/运行中被验证。
- **中**：引用计数明确，但存在动态使用方式（反射、Spring 扫描、配置驱动）无法 100% 排除。
- **低**：仅静态推导，需实际运行或业务确认。

### 0.4 推荐操作

`保留` / `简化` / `合并` / `删除` / `待确认`

### 0.5 审批状态

所有条目默认 **待确认**。你逐项审批后，我再据此进入修改阶段；本阶段我**不会**修改任何代码。

---

## 1. 项目整体架构概览

### 1.1 技术栈（已从代码与配置文件确认）

| 层 | 事实 |
| --- | --- |
| 后端 | Java 17、Spring Boot 3.4.4、Spring Security、MyBatis-Plus 3.5.x（`mybatis-plus-spring-boot3-starter` + `mybatis-plus-jsqlparser`）、MySQL 8、Redis（跨实例实时事件）、WebSocket、POI（Excel 导出）、Jasypt（配置加密）、AspectJ |
| 前端 | React 18、TypeScript 5.8、Vite 6、Ant Design 5、Redux Toolkit、axios、dayjs、ECharts、AntV G6 5、Monaco Editor、react-markdown |
| 构建 | Maven（`backend/pom.xml`，**无** Maven Wrapper）；npm（`frontend/package.json`，`build` = `tsc --noEmit -p tsconfig.app.json && vite build`） |

### 1.2 规模基线（审计开始时实测）

| 项 | 数量 |
| --- | --- |
| 后端 Java 文件（`backend/src`，含测试） | 297 个 / 约 13,032 行 |
| 前端 `frontend/src` 的 `.ts`/`.tsx` | 131 个 / 约 9,028 行 |
| 前端 `.css` | 34 个 / 约 2,187 行 |
| 后端迁移脚本 | `db/approval/V1~V4`、`db/intranet/V1~V15`，另有 `init_schema.sql` / `init_data.sql` |
| 后端测试 | 仅 2 个文件：`JasyptEncryptorTest`、`ApprovalWorkflowServiceTest`（92 行） |

### 1.3 模块职责与边界（对照 `AGENTS.md`、`docs/intranet/ARCHITECTURE.md`）

```text
backend/src/main/java/org/lbl/
  agent/          与业务无关的 Agent 核心（ReAct 循环、工具注册表、模型网关端口）
  approval/       core=公共审批状态机；task=审批中心查询；departmenttransfer=部门调动业务适配
  auth/           登录、注册、开户、验证码、会话、外部认证（external）、UIAS 适配
  common/         统一返回 Result/PageResult、异常与全局处理、请求日志 Filter、Excel 工具
  config/         Security 配置、MyBatis 配置、外部认证 Provider 注册、权限初始化器
  intranet/       requirement（需求）、transaction（交易与报文/字段）、dataasset（HBase/ES/关联）
  notification/   通知落库 + 实时推送
  realtime/       Redis Pub/Sub + WebSocket 网关与心跳
  security/       JWT、认证 Filter、数据权限上下文 AccessPolicy、可信代理解析
  system/         user / dept / role / menu / log（日志、分析、风险规则、保留策略、Agent 审计工具）
frontend/src/
  components/     SmartTable、EllipsisText、G6Graph、BaseChart、CodeViewer、IconPicker、
                  StatusTag、CopyButton、FieldLabel、SystemSettings、ai/**（助手与制品渲染）
  pages/          动态扫描目录：home、system/**、account/**、intranet/**、login、register、auth、error
  layouts/        AppLayout（侧边栏、顶栏、通知、主题入口）
  router/         componentRegistry（import.meta.glob 动态页面注册）、menu、iconRegistry
  services/       请求进度、会话续期、WebSocket 客户端、用户活动管理
  store/          Redux Toolkit（authSlice、notificationSlice）
  theme/          tokens + ThemeProvider（antd token + dayjs locale）
  utils/          request（axios 实例与 401 续期）、apiError、download、passwordPolicy
```

**关键机制（影响后续所有「是否死代码」的判断）**：

1. **前端页面是配置驱动的**：`frontend/src/router/componentRegistry.tsx:19` 用
   `import.meta.glob(['../pages/**/*.tsx', '!../pages/error/**', ...])` 构建期扫描 `pages/`，
   菜单表 `sys_menu.component` 填的值（如 `system/user/index`）在运行时映射到该组件。
   因此 `pages/**/index.tsx` **无人 import 是设计使然，不构成死代码**（见 §7 与 FE-CMP 条目）。
2. **后端 Agent 工具是 Spring 扫描发现的**：`ToolRegistry` 构造函数注入 `List<AgentTool>`，
   工具类即使全仓库零静态引用也是活的。同理所有 `@Component` / `@Service` / `@Mapper`。
3. **数据库脚本不是「未使用文件」**：`db/**/*.sql` 是人工按序执行的迁移脚本，不受编译期引用检查。

### 1.4 审计开始时的 Git 状态（只读核对，未做任何写操作）

```text
## main...origin/main
 M backend/src/main/java/org/lbl/intranet/requirement/controller/IntranetRequirementController.java
 M backend/src/main/java/org/lbl/intranet/requirement/service/IntranetRequirementService.java
 M backend/src/main/java/org/lbl/system/user/mapper/UserMapper.java
 M backend/src/test/java/org/lbl/approval/core/service/ApprovalWorkflowServiceTest.java
 M docs/intranet/{DECISIONS,HANDOFF,MIGRATION,PLAN}.md
 D frontend-deadcode-audit.md
 M frontend/src/layouts/AppLayout.tsx
 M frontend/src/pages/account/approvals/index.tsx
 M frontend/src/pages/account/notifications/index.tsx
 M frontend/src/pages/intranet/requirement/{NewTransactionDetailDialog,RequirementDialog,api,index,types}.tsx|ts
 D 前端处理.xlsx
?? 后端需求明细实体/Mapper 4 个 + 前端需求明细/审批详情组件 4 个
```

> 说明：这些是**你本人的未提交改动**，审计过程中我未覆盖、未整理、未回退。
> 其中 `frontend-deadcode-audit.md` 已从工作区删除、但仍在 Git 历史（HEAD）中；
> 我把它当作**既有的一次前端专项审计记录**，逐条在**当前代码**上复核后引用（见 §9 说明）。

### 1.5 审计范围与进度

| 范围 | 状态 |
| --- | --- |
| 后端 common / config / security / system / intranet 分层 | 进行中 |
| 后端登录鉴权与会话全链路 | 进行中 |
| 参数校验、幂等与防重复机制 | 进行中 |
| 公共审批、通知、实时通道 | 进行中 |
| Agent 模块、日志分析与风险规则 | 进行中 |
| 前端页面、组件、状态与请求 | 进行中 |
| 前端 CSS 与 UI 残留 | 进行中 |
| 工程配置、依赖、脚本、无效配置项 | 已完成（§7） |

---

## 2. 各模块审计结果

### 2.1 公共基础设施（`common` / `config` / `security`）——已完成部分

#### [BE-CMN-01] `AccessPolicy.actor()` 单次调用固定产生 5 次数据库查询，且没有任何请求内缓存；多个调用点在同一请求里重复调用它

- **位置**：
  - 实现：`backend/src/main/java/org/lbl/security/context/AccessPolicy.java:50-75`
  - 重复调用点（同一请求内 ≥2 次）：
    - `org/lbl/intranet/requirement/service/IntranetRequirementService.java:149` → `if (!access.actor().superAdmin() && !access.actor().user().getId().equals(...))`：**同一行调用 2 次**，而该方法被 `update`/`submit`/`cancel`/3 个 `saveXxxDetail` 共 **6 处**调用；
    - `org/lbl/system/menu/service/MenuService.java:39,44,50,76,88` → 5 处 `access.requireSuperAdmin(access.actor())`，每次 1 次；
    - `org/lbl/system/role/service/RoleService.java:135` → `access.requireManageRole(access.actor("system:role:grant"), require(id))`；
    - `org/lbl/system/user/service/UserSessionAdministrationService.java:28,34,40` → 3 处；
    - `org/lbl/approval/departmenttransfer/DepartmentTransferApprovalService.java:57,61` → 同一方法内 2 次。
- **当前实现的作用**：`actor()` 构造当前请求的**授权快照**（用户 + 角色 + 权限码 + 数据范围 + 操作范围）。它内部依次执行：
  `users.selectOne`（按用户名查用户，`:53`）→ `roles.selectAssignedByUserId`（`:55`）→ `depts.selectList`（**全表查部门**，`:58`）→ `permissions()` 内的 `menus.selectByUserId`（`:61,418-423`）→ `prepareRoles()` 内的 `roleMenus.selectPermissionCodesByRoleIds`（`:98`）与 `roleDepts.selectByRoleIds`（`:105`）。
- **判断证据**：
  - 该类的**请求内缓存机构已经存在**（`ScopeContext` record，`:60,466-467`：`departments`/`customDepartments`/`rolePermissions` 三个 Map），`prepareRoles()` 的注释（`:92`）也明确写着"列表和授权检查**共享请求内缓存**，不逐用户查询"。
  - 但缓存**挂在 `Actor` 实例上**，而 `actor()` **每次调用都新建** `Actor` 与 `ScopeContext`（`:60-74`），因此**跨调用不共享**。
  - 全仓库检索 `@Cacheable` / `@Scope("request")` / `RequestContextHolder` / `ThreadLocal`：`org.lbl` 下**只有** `system/log/support/RequestInfo.java:6,93` 使用 `RequestContextHolder`（用于取客户端 IP），**没有任何**对 `actor()` 结果的缓存或请求作用域。
  - 于是 `IntranetRequirementService.requireOwner` 一行触发 **2 × 5 ≈ 10 次查询**；`MenuService` 的 5 个方法各 5 次。
- **依赖关系**：`actor()` 被 15 个类调用（全仓库 51 处 `.actor(` 命中，见检索清单）。**这不是"可以删除的死代码"**，而是"已经很贵的公共方法被重复调用"。
- **补充证据（另一路只读审计独立发现）**：同一个请求内**还额外重复了一次最贵的查询**——`security/filter/JwtAuthenticationFilter.java:60,64` 已经执行过 `users.selectById`（构造 principal）与 `menus.selectByUserId`（`MenuMapper:14-32` 的**递归 CTE**，用于构造 `SimpleGrantedAuthority`），而 `AccessPolicy:53,420` 用**同一条 SQL、同一个 userId 再算一遍**。即每条已认证请求的递归菜单 CTE 执行 **2 次**、用户行查 **2 次**。反证（避免误判）：`UserService.list:79-104`、`RoleService.list:47-62` 等**确实**只取一次 Actor 并复用，说明缓存在"单 Actor"用法下有效——问题只在多次调用点与 filter/actor 的重复。
- **推荐操作**：**简化（不是删除）**，三个方向可组合：
  1. **最小改动**：消除同一方法/同一行内的重复调用（例如 `IntranetRequirementService:149` 先取一次 `var actor = access.actor();`）；`MenuService`/`UserSessionAdministrationService` 同理。
  2. **结构性改动**：把 `actor()` 结果做成**请求作用域缓存**（Spring `@Scope("request")` 或 `RequestContextHolder`），使同一请求内多次调用只查一次库。⚠️ 这触及**基线公共安全组件**，按 `AGENTS.md` §2 必须先申请批准，且必须回归**全部** `@PreAuthorize` + 数据范围相关行为（用户/角色/部门/日志/审批/内网全部列表页）。
  3. **消除 filter 与 actor 的重复查询**：`JwtAuthenticationFilter` 算过的权限集合可由 `AccessPolicy` 复用（或反过来让 filter 只解析 token、权限统一由 `AccessPolicy` 提供）。⚠️ **`actor():52-54` 的 status 复核绝不能动**——那是对"账号被停用后会话仍有效"的必要防护。
- **影响面**：方向 1 影响面小但收益有限；方向 2/3 收益大（每个请求省下 5~10 次查询，其中递归 CTE 成本最高）但**权限行为一旦出错就是越权**，必须做完整回归。**在没有你的明确批准前，我建议本轮不动它**，仅登记。
- **风险等级**：P3（涉及授权与数据范围，属于关键安全路径；同时它也是真实性能问题）　**证据可信度**：高（调用点与查询链都是从代码读出的）　**审批状态**：待确认

#### [BE-CMN-02] `ExternalAuthController` 存在未使用的导入 `LoginSession`

- **位置**：`backend/src/main/java/org/lbl/auth/controller/ExternalAuthController.java:8`（`import org.lbl.auth.session.LoginSession;`）。
- **当前实现的作用**：无——该符号在文件其余部分**一次都没出现**。
- **判断证据**：对全仓库 `backend/src` 的每个 Java 文件做"逐个 import 的简单名在文件内出现次数"统计，**唯一**只出现 1 次（即仅导入行本身）的就是这一条。Java 无 lint 门禁（项目无 Checkstyle/SpotBugs/ESLint 配置），所以 `mvn compile` 不会提示。
- **依赖关系**：无。
- **推荐操作**：删除该行。
- **影响面**：无（编译与行为均不变）。
- **风险等级**：P0　**证据可信度**：高　**审批状态**：待确认

#### [BE-CMN-03] `GlobalExceptionHandler` 的中文标签表 `FIELD_LABELS` 只服务于一条"很少走到"的兜底路径
- **位置**：`backend/src/main/java/org/lbl/common/exception/GlobalExceptionHandler.java:29-60`（27 条 `Map.entry`）与 `:181-256`（反射读注解 message 的逻辑）。
- **当前实现的作用**：校验失败时优先回显**注解上手写的中文 message**；只有当注解**没有**写 message 时，才退回"字段标签 + 约束类型"的机械拼装（`violationMessage`，`:246-256`），此时用 `FIELD_LABELS` 把 `captchaCode` 这类 Java 字段名换成「验证码」。
- **判断证据**：
  - 代码注释（`:243-244`）自己写明：「目前全项目的校验注解里**只有极少数**没有手写 message，这条路很少会走到」。
  - 实测：请求体 record 上的校验注解几乎全部带 `message`（例如 `UserRequest.java:21-43` 每条都有；`RegistrationRequest.java:24-37` 每条都有；内网 `RequirementRequest`/`HbaseTableRequest`/`EsFieldRequest` 等同类）。**没有**手写 message 的实例主要是 `pages` 侧（如 `AssetDialogs.tsx` 的 `rules={[{ required: true }]}`，但那是**前端**校验，不走这条后端路径）。
  - 因此 `FIELD_LABELS` 的 27 条映射中，只有极少数键（`captchaCode`、`rememberMe` 这类在 `RegistrationRequest` 里**有** message 之外的情况）会被用到。
  - 另一部分复杂度来自 `declaredMessage()`（`:202-232`）的**反射**：它为了"不把 `@Pattern` 的正则原样漏给用户"（`:167-179` 有长注释解释威胁模型），改为反射读注解的 `message` 属性。**这个防护本身是合理的**（避免泄露正则、避免 JVM 本地化把英文模板丢给中文界面）。
- **依赖关系**：`describeFirstViolation` → `authoredOrFallback` → `declaredMessage` 是一条完整链路，删掉 `FIELD_LABELS` 不影响主链路，只影响"注解没写 message"时的文案质量。
- **推荐操作**：**待确认 / 建议保留**——这是**权衡**而非明显冗余：
  - 保留的理由：兜底路径确实存在（注释已承认），删掉后用户会看到 `captchaCode 长度不合法` 这种半中半英提示；
  - 简化的可能：把 27 条收缩为"实际会走到兜底的字段"（需先**逐个确认**哪些注解没写 message），收益是少 20 行常量，代价是以后新增 DTO 忘写 message 时提示更差。
- **影响面**：仅影响错误提示文案，不影响任何业务行为。
- **风险等级**：P2（文案质量与可维护性权衡）　**证据可信度**：中（"很少走到"来自代码注释自述 + 我抽查的 DTO 样例，**没有**全量枚举所有注解确认）　**审批状态**：待确认

#### [BE-CMN-04] `AccessPolicy.requiresPlatformTransferReview` 全仓库零调用点

- **位置**：`backend/src/main/java/org/lbl/security/context/AccessPolicy.java:197-204`。
- **当前实现的作用**：判断"调部门会改变动态角色（`DEPT`/`DEPT_AND_CHILDREN`）的数据范围"，用途是把这类迁移**交由平台管理员审批**（方法注释：`:197`「调部门会改变动态角色范围；这类迁移交由平台管理员审批」）。
- **问题证据**：全仓库 grep `requiresPlatformTransferReview` **命中 1 行，即该方法自身声明**；`docs/` 下 0 命中；相邻关键词 `transferReview|平台管理员审批` 也只命中它自己。当前两个"调部门"入口**都不用它**：`UserService.update:175-208`（管理员直接改 `deptId`）、`DepartmentTransferApprovalService.submit/decide:64-160`（固定 `SOURCE→TARGET` 两步审批，**代码中不存在"平台管理员复核"步骤**）。
- **依赖关系**：无调用者；它自身调用 `roles.selectAssignedByUserId`(`:199`)、`prepareRoles`(`:202`)。
- **推荐操作**：⚠️ **待确认，不建议直接删**——必须由你判断这是哪一类：
  1. **"按设计应接入 `UserService.update` 的调部门路径"** → 那它是一条**缺失的安全闸门**（动态角色持有者跨部门迁移可绕过平台审批）；
  2. **"设计已废弃"** → 才可删除（约 8 行 + 无用 import）。
- **影响面**：删错会丢失一条**已声明的安全意图**；若属于情况 1，则这是一个**真实的安全缺口**而非冗余代码。
- **风险等级**：**P2**（零引用是事实；"死代码"还是"未接线的防护"必须由人判断）　**证据可信度**：高（调用关系已用全仓库检索证明）　**审批状态**：待确认

#### [BE-CMN-05] `CurrentUser.deptId` 只写不读（record 组件零消费）

- **位置**：`backend/src/main/java/org/lbl/security/context/CurrentUser.java:14`；唯一写入点 `security/filter/JwtAuthenticationFilter.java:67`。
- **问题证据**：全仓库 grep `\.deptId\(\)` **6 命中，全部是 `request.deptId()`**（属 `UserRequest`），**没有任何一处对 `CurrentUser` 调用 `deptId()`**。对照组：`id()` 被 `MybatisConfig:44`、`OperationLogService:110` 与 7 个 `@AuthenticationPrincipal CurrentUser` 控制器读取；`username()` 被 `OperationLogService:111` 读取。该值不进入任何响应体（`management.endpoints` 只暴露 health/info）。
- **推荐操作**：**待确认**——保留（若打算用它免去数据范围判断时的一次回查）或删除该组件 + 一处实参。若保留，建议在注释里写明"当前无消费者"。
- **风险等级**：P3　**证据可信度**：中高（Java/TS 全仓库检索无读取点；不能 100% 排除未来反射/序列化读取）　**审批状态**：待确认

#### [BE-CMN-06] `MaxUploadSizeExceededException` 处理器与 yml multipart 限制在仓库内没有消费路径

- **位置**：`backend/src/main/java/org/lbl/common/exception/GlobalExceptionHandler.java:69-74`；`backend/src/main/resources/application.yml:8-11`（单文件 10MB / 请求 11MB）。
- **问题证据**：后端 grep `MultipartFile|@RequestPart` = **0 命中**；前端 grep `FormData|multipart|upload` = **0 命中**。仓库内**没有任何方法解析 multipart、也没有任何上传页面**（唯一 Excel 能力是**导出**：`UserExportService`）。提示语"请压缩或拆分后重试"表明该能力曾经存在或计划中存在。
- **依赖关系**：该异常只在解析 multipart 时由 Spring 抛出，当前**无触发入口**。
- **推荐操作**：**待确认**——保留成本极低（属防御性代码），但建议在文档里标注"当前无上传入口"，避免后人误以为存在上传能力。
- **关联事实**：`AGENTS.md`/`HANDOFF.md` 记录的 `a3b8d0b` 已**删除**了 `org/lbl/file` 暂存文件上传模块（用户确认为有意清理）；D-032 又预确认了未来 Excel 导入将**新建 multipart 端点**。因此这段代码很可能是**为 D-032 保留的**——若属实则应保留。
- **风险等级**：P3　**证据可信度**：中（grep 命中 0 是事实，但"框架绝不会抛该异常"是推断）　**审批状态**：待确认

#### [BE-CMN-07] `AccessPolicy.isSuperAdmin` 为 `public` 但仅被同类内部调用

- **位置**：`AccessPolicy.java:425-437`（含长篇 javadoc）；唯一使用点 `:440-442`（`containsSuperAdmin`）。
- **问题证据**：全仓库 grep `isSuperAdmin` 仅命中声明、同类内部使用 1 处、以及 3 处**注释**引用（`UserService:181,327`、`AccessPolicy:430`）；测试目录也未使用。
- **推荐操作**：**简化**（降为 `private`；javadoc 移不影响"判定口径唯一"的意图）。若预期将来由 `UserService` 直接复用则**保留**。
- **风险等级**：P3　**证据可信度**：高　**审批状态**：待确认

#### [BE-CMN-08] `TextValues` 是单方法工具类（**审计结论：建议保留**）

- **位置**：`common/util/TextValues.java:4-11`（1 个 public 方法、3 行实现）。
- **问题证据**：表面上符合"只有一两个方法的 util"这一可疑特征，且功能上可用 `StringUtils.hasText + trim()` 替代。但 grep `TextValues|trimToNull` = **38 行 / 10 个文件**，调用点覆盖 `UserService:144`、`RegistrationService:67-79`、`MenuService:110-136`、`EsAssetService:63`、`HbaseAssetService:53`、`TransactionAssetService:56-59`、`TransactionMessageFieldService:62`、`IntranetRequirementService:50,152`、`AgentRequestMapper:41`。
- **推荐操作**：**保留**——替换成库方法会把这套"空白→null"的口径**重新散回各调用点**，收益为负。记入本报告是为了**避免后续会话把它当过度拆分删除**。
- **风险等级**：P3（仅记录评估过程）　**证据可信度**：高　**审批状态**：建议保留

#### [BE-SYS-01] `ancestors` 字符串解析在 `DeptService` 与 `RoleService` 各写一份，绕开 `DeptPaths` 的"唯一口径"

- **位置**：`system/dept/service/DeptService.java:63-76`（关键 `:67-73`）；重复实现 `system/role/service/RoleService.java:208-223`（关键 `:213-215`）；口径声明 `system/dept/support/DeptPaths.java:6-19`。
- **当前实现的作用**：把"可见部门 + 其全部祖先"组装成前端树所需的完整结构。
- **问题证据**：两处代码**几乎逐行相同**（`getAncestors().split(",")` → `Long.parseLong` → **同一个 `catch (NumberFormatException ignored)` 静默兜底**）。`DeptPaths` 的 javadoc 声明自己是"用字符串前缀表达树关系"的唯一口径，但它**没有**"路径→id 集合"方法，于是两处各自解析。（另 3 处正确使用了 `DeptPaths`：`AccessPolicy:124`、`DeptService:145,157,188`。）
- **依赖关系**：`DeptService.list:43` 与 `RoleService.grantableDepartments:208` 是两条独立接口，都受 `AccessPolicy` 数据范围驱动，**语义必须一致**。
- **推荐操作**：**简化**——在 `DeptPaths` 增加 `ancestorIds(String ancestors)`，两处改为调用。⚠️ `RoleService` 属基线，需按 `AGENTS.md` §2 报批。
- **影响面**：两处若对脏数据的处理出现差异，会产生**"部门列表可见范围 ≠ 角色可授权部门范围"的静默不一致**（`DeptPaths` javadoc 自己就指出这类不一致"不会报错，只会静默算错"）。
- **风险等级**：P3　**证据可信度**：高　**审批状态**：待确认

#### [BE-SYS-02] `MenuController` 直接以 `MenuEntity` 作为响应模型，多暴露 4 个无消费方的持久化字段

- **位置**：`system/menu/controller/MenuController.java:25,31,38,45`；`system/menu/entity/MenuEntity.java:26-36`。
- **当前实现的作用**：菜单 CRUD 的响应体直接复用 MyBatis-Plus 实体。
- **问题证据**：4 个方法返回 `Result<MenuEntity>`；实体含 `deleted`（`:26-27` `@TableLogic`）、`createdBy`/`updatedBy`（`:28-31`）、`deletedTime`（`:36`）。前端 `types/menu.ts:3-19` 的 `SystemMenu` **没有**这 4 个字段（grep `createdBy|updatedBy|deletedTime|deleted` 在 `frontend/src` 仅 3 行且与本模块无关）。同项目的 `DeptController` 走 `DeptVO`（含语义化派生字段），两模块契约风格不一致。
- **推荐操作**：**简化（待确认）**——引入 `MenuVO`，或在 `deleted`/审计字段上加 `@JsonIgnore`。属接口契约收敛，需前端联调确认。
- **影响面**：接口暴露逻辑删除标记与操作者 id（不敏感但无用途）；**后续实体加字段会自动进入 API**，属隐性契约漂移风险。
- **风险等级**：P3　**证据可信度**：高　**审批状态**：待确认

#### [BE-SYS-03] 内置菜单/权限种子在 `init_data.sql` 与 `SystemPermissionInitializer` 双份维护（18 个权限码 + 2 个页面 100% 重叠）

- **位置**：`config/SystemPermissionInitializer.java:51-54,57-76`；`db/init_data.sql:26-61`。
- **当前实现的作用**：初始化器为**老库升级**补建缺失菜单/按钮并授予超管（javadoc `:26-29` 说明这是刻意设计，因为 `init_data.sql` 只在建库时执行）。
- **问题证据**：逐条比对，初始化器声明的**每一条都已存在于** `init_data.sql`，且两处**各自硬编码字面量、没有任何共享来源**：`PAGES(:52-53)` ↔ `init_data.sql:34-35`（id 13/14）；`PERMISSIONS(:58-75)` 的 18 条 ↔ `init_data.sql:38-61`；授权 `grant():164-172` ↔ `init_data.sql:64-65`。
  ⚠️ 该类 javadoc `:262-263` **自述**："以前 `system:user:export` 的补建就**因为没有单一真相来源，在三份种子/补建逻辑之间漂移过**"——而当前**仍是两份来源**（外加 intranet 的 `db/intranet/V*.sql` 走第三套机制）。
- **依赖关系**：`SystemPermissionInitializer` 是 `@Order(100) ApplicationRunner`（`:43-45`），**启动即执行**；⚠️ 且它是 `@SpringBootTest`（`JasyptEncryptorTest`）会**连接远程 MySQL 并可能补写 `sys_role_menu`** 的原因（`HANDOFF.md:166` 已登记）。
- **推荐操作**：**待确认**——保留补建功能，消除重复来源（例如以 `:51-76` 的常量清单为唯一来源，`init_data.sql` 由它生成/校验；或至少在两侧加"必须同步"的显式校验）。**不建议只删其中一侧**。
- **影响面**：新增权限时漏改一侧 → "代码里有页面、菜单树里没入口"或"新库有、老库没有"——正是 javadoc 自述的历史故障类型。
- **风险等级**：P3　**证据可信度**：高　**审批状态**：待确认

#### [BE-DA-01] `EsAssetService` 列表/选项逐行回查 HBase 表（N+1），且 `options()` 无分页上限

- **位置**：`intranet/dataasset/es/service/EsAssetService.java:37-38,40,69-70`。
- **当前实现的作用**：ES 索引分页/下拉列表，返回 VO 时补充所属 HBase 集市名与表名（`EsIndexVO.hbaseMarketName/hbaseTableName`）。
- **问题证据**：`:38` 与 `:40` 都是 `map(this::view)`；`:70` 的 `view()` 内部 `requireHbase(e.getHbaseTableId())` → `:69` 执行 `hbaseTables.selectById(id)`。→ **每页 N 条记录产生 N 次额外 `selectById`**（默认 pageSize=10 → 11 次查询；上限 100 → 101 次）；`options()` 更是"**全量行 × 1 次回查**"且**无任何上限**（`indexes.selectList` 全表），而该接口被两个下拉框使用（`DataResourceDialog:14`、`AssetDialogs:44`）。
  **同项目已有同类修复先例可对照**：`DeptService:44-46` 注释"以前是每条部门各查一次用户表（N+1）…SQL 数量会线性增长"，并改为 `leaderNames()` 批量 `selectByIds`（`:79-84`）。ES 侧未采用同一做法。
- **依赖关系**：`page()`/`options()`/`detail()` 都经 `view()`；`EsIndexVO` 的两个字段被前端 `types.ts:6` 消费，**不能删除**，应改为按 `hbaseTableId` 去重后**批量 `selectByIds` 建 Map**。
- **推荐操作**：**简化**（批量预取 HBase 表名；`options()` 增加条数上限或只返回 `id + 名称` 的轻量 VO）。属 intranet 模块，可在模块内独立修改。
- **影响面**：ES 索引列表与两个下拉框的 SQL 次数、首屏耗时；数据量增长后线性恶化。
- **风险等级**：**P2**　**证据可信度**：高　**审批状态**：待确认

#### [BE-DA-02] DTO/VO 与实体字段完全相同（3 组 VO + 1 组 request/VO），映射不携带任何语义

- **位置与证据**（逐字段比对，**数量与名称/类型完全一致、无裁剪无改名**）：
  - `es/vo/EsFieldVO` ↔ `es/entity/EsFieldEntity`（12/12）；映射 `EsAssetService:71`
  - `hbase/vo/HbaseFieldVO` ↔ `hbase/entity/HbaseFieldEntity`（11/11）；映射 `HbaseAssetService:59`
  - `hbase/vo/HbaseTableVO` ↔ `hbase/entity/HbaseTableEntity`（12/12）；映射 `HbaseAssetService:60`
  - `transaction/vo/TransactionVO` ↔ `transaction/entity/TransactionAssetEntity`（**22/22**）；映射 `TransactionAssetService:62`
  - `relation/request/TransactionDataResourceRequest` ↔ `relation/vo/TransactionDataResourceVO`（字段逐字相同，request 仅多 `@NotNull`）；且 `replace()` 的返回 `new TransactionDataResourceVO(hbaseIds, esIds)`(`:75`) **就是** `normalize(request.*)` 的产物（`:54-55`），转换不携带新语义
  - ⚠️ **对照组（这些不是问题）**：`EsIndexVO`（额外带 `hbaseMarketName/hbaseTableName`）、`TransactionMessageFieldVO`（裁掉 4 个审计字段）、`TransactionListVO`（列表投影）——它们**确有语义差异**，是正确的 VO 用法。
- **问题证据（补充）**：前端 `pages/intranet/transaction/types.ts:2` 的 `Transaction` **只用 14 个字段 + `createdTime`**，**不使用** `createdBy`/`updatedBy` → 说明 `TransactionVO` 是按"实体镜像"而非"消费方契约"设计的。同一 2 字段形状在前端还被**重复声明两次**（`dataasset/types.ts:10` 与 `transaction/types.ts:8` 各一份 `TransactionDataResources`）。
- **推荐操作**：**待确认**——保留 record 作为**不可变 API 契约**本身是合理取舍（比暴露可变实体好，见 BE-SYS-02 的反向案例）；若要减少代码，可让 VO 由实体直接构造/共享映射。**不建议**为消除重复而把可变实体直接暴露给前端。
- **影响面**：约 30 行机械映射 + 4 个同构类型；无功能影响。
- **风险等级**：P3　**证据可信度**：高　**审批状态**：待确认

### 2.1.9 该范围「已评估但不认定为问题」的清单（避免后续会话重复排查）

1. **没有纯转发的 Controller、没有只包装 Mapper 的 Service、没有空壳 Service**：本范围 12 个 Controller 全部承载 `@PreAuthorize` 与（多数）`@OperationLog`；9 个 Service 全部含校验、事务或级联逻辑。
2. **`ExcelExportUtil` 的 `RowProducer`/`RowWriter` 虽各只有 1 个实现，但确有作用**：唯一消费方 `UserExportService:50-61` 确实按 500 条分批循环取数；JDK 函数式接口无法抛 `IOException`，故自定义接口是必要的，**不是过度抽象**。
3. **`AccessPolicy.requireNotPlatformOnly` 与初始化器"给超管授予平台级权限"不矛盾**：`RoleService.grantMenus:187-194` 在重写授权时原样保留超管已有的平台级 id，`menuIds():139-140` 按同口径过滤，不会因界面保存而丢失超管权限。
4. **`SystemPermissionInitializer` 用 `selectList + first` 而非 `selectOne`**：针对"索引建立前的历史库"降级，属**防御性代码**，不认定为重复实现。
5. **`UiasAssertionConsumer` / `EnterpriseDirectoryPort` 在仓库内无实现是刻意的**：`application-local.yml:63` 明确说明"实际实现只在内网部署时提供，本仓库仅保留流程编排与适配端口"；`UiasLoginService:163-170` 有明确失败路径（`ObjectProvider.getIfAvailable()` + 抛错）。
6. **`Result.requestId` 前端只声明不读取是排障契约**：与 `RequestLoggingFilter`、logback `%X{requestId:-}`、`OperationLogEntity.requestId` 同链路。
7. **`EsAssetService.ROWKEY_FIELD_NAME`（public 但仅类内 3 处使用）**：领域常量，不单列为问题。
8. **本范围内没有"单实现的策略/工厂/适配器"**：`interface` 声明仅 14 个 MyBatis Mapper（框架要求）与 `ExcelExportUtil` 的 2 个回调接口。

### 2.2 公共审批 / 通知 / 实时 / 内网需求

> 本节由一个只读子审计会话产出后由我复核并入；其结论均带文件与行号依据。
> 该子审计**未**运行构建、**未**连接数据库、**未**做浏览器验证（见本节末尾"未检查范围"）。

#### 2.2.1 现状链路（已从代码核实）

```text
推送链路（全仓只有一条，无第二套实现）
  业务 Service(@Transactional)
    → NotificationService.create / approvalUpdated
       → mapper.insert(sys_notification) + afterCommit 注册
       → RealtimeGateway.send(userId, type, data)
          → RealtimeEventPublisher.publish → Redis pub/sub（频道 kariya-admin:realtime:events）
             → RealtimeEventSubscriber.onMessage → gateway.sendLocal → 本地 WebSocket
          → 仅当 publish 失败才直接 sendLocal（RealtimeGateway:47-50，防重复投递）
  心跳：RealtimeGateway:83-87 @Scheduled(25s) 只发本地，不广播
  鉴权：RealtimeController → RealtimeTicketService（一次性 ticket，Redis getAndDelete + 60s TTL）→ 握手消费
  前端：AppLayout.tsx:108-126 唯一实时入口（单个 RealtimeClient 实例），非轮询（setInterval 0 命中）

审批（D-034 纯方案 2 已落地）
  approval/core             ApprovalWorkflowService（状态机，全部 @Transactional(MANDATORY)）
  approval/task             ApprovalTaskController(/api/account/approvals) → Service → Mapper
                            （4 条注解 SQL 只 FROM sys_approval_*，JOIN 的 sys_user/sys_user_role/sys_role 是 RBAC 基线表）
  approval/departmenttransfer  写公共表 + 写 sys_department_change + 通知
  intranet/requirement         写 intranet_requirement + 三类明细表 + 写公共表 + 通知
```

#### 2.2.2 D-034 / D-035 决策与代码的一致性核实

| 决策要点 | 核实结果 | 证据 |
| --- | --- | --- |
| 审批中心只查公共表 | **成立** | `ApprovalTaskMapper.java:11-21` 的 `COLUMNS` 仅 `FROM sys_approval_instance a JOIN sys_approval_task t`，4 条 SQL 全部如此 |
| 不 JOIN 任何业务表 | **成立** | 该文件无 `intranet_` 与业务表名；JOIN 的 `sys_user`/`sys_user_role`/`sys_role` 属 RBAC 基线表 |
| 无长期 `UNION ALL` | **成立** | 0 命中 |
| 并发用事务内 `SELECT ... FOR UPDATE`，不引入 Redis/JVM/乐观锁 | **成立** | `ApprovalInstanceMapper.java:15` 含 `FOR UPDATE`；`ApprovalWorkflowService.java:80-86` 的 `lock()` 为 `MANDATORY`；审批包内 `@Version`/`OptimisticLocker`/`RedisLock`/`setIfAbsent`/`tryLock` 均 0 命中 |
| 实例表不设 `version` | **成立** | 实体无 `version`；`init_schema.sql:155-175` 无该列 |
| `resubmit` 保留旧轮次任务 | **代码成立，但查询层把它隐藏了** → 见 BE-APR-01 | `ApprovalWorkflowService.java:58-71` 新增 round；`ApprovalTaskMapper.java:19-20` 用 `t.approval_round=a.approval_round` 限定当前轮 |
| 业务表更名 `sys_department_change` | **成立** | `DepartmentTransferRequestEntity.java:8` `@TableName("sys_department_change")`；`init_schema.sql:142` |
| 删除 `sys_department_change_step` | **运行时代码成立** | Java/前端/`init_schema.sql` 0 命中；仅一次性脚本 `db/approval/V2,V3` 与 docs 仍提及 |
| 业务表不再有 `status`/`current_step`/`version`/`finished_time` | **成立** | 实体仅 id/requesterId/fromDeptId/targetDeptId/reason/createdTime/updatedTime |
| 「新增业务只写公共表、不改审批中心」 | **仅后端 SQL 成立**；前端详情入口仍需按业务加分支 → 见 BE-APR-07 | `approvals/index.tsx:51-61` 与 `notifications/index.tsx:42-48` 都是 `businessType` 的 if/else |
| `approval` 三层包结构保留 | **成立** | `core`/`task`/`departmenttransfer` 未合并、未移动 |
| D-033 过渡设想的代码残留 | **无代码残留** | `sys_department_change_request`/`_step` 全仓 12 命中，全部在 docs(4) + `db/approval/{V2,V3}`(7 行)；Java/前端/`init_schema.sql` 0 命中 |

**检索命中数（供复核）**：`sys_department_change_request|_step` 12；`current_step|finished_time`（snake_case）15（其中 Java 侧仅 `ApprovalTaskMapper` 的 4 处公共表列）；`businessType|business_type` 51；`DEPARTMENT_CHANGE` 19；`INTRANET_REQUIREMENT` 15；`approval.request.updated|notification.created` 9；`intranet:requirement:` 21。
**权限码核对**：Controller 使用的 `intranet:requirement:{list,add,update,submit,assign}` 与 `V14:8-18` 完全一致；transaction/dataasset 权限码与 V2/V9 一致；**无孤立权限码**。

#### 2.2.3 问题清单

##### [BE-APR-01] 「我已处理」列表与其 total 口径不一致（驳回重提后记录被隐藏、总数虚高）

- **位置**：`backend/src/main/java/org/lbl/approval/task/ApprovalTaskMapper.java:11-21`（`COLUMNS`）、`:45-48`（`processed`）、`:50-51`（`countProcessed`）。
- **当前实现的作用**：`processed` 复用 `COLUMNS`，而 `COLUMNS` 的 JOIN 条件包含 `t.approval_round = a.approval_round`。
- **问题证据**：实例重提后 `a.approval_round` 变为 2，该实例**第 1 轮**的 APPROVED/REJECTED 任务被 JOIN 排除；而 `countProcessed` 直接 `SELECT COUNT(*) FROM sys_approval_task WHERE decided_by=…`，**不过滤轮次**。于是列表少行、`total` 多算 → 出现空白尾页。`ApprovalWorkflowService.java:58-71` 明确保留旧轮次，故这是**设计意图与查询口径的冲突**。
- **依赖关系**：只在「驳回 → 重提」路径触发（`IntranetRequirementService.java:86-94`）；单轮次业务不受影响。
- **推荐操作**：简化/修正——列表与计数使用**同一轮次口径**，或让 `processed` 去掉轮次限制（取决于你要不要把历史轮次展示给我已处理）。
- **影响面**：审批中心「我已处理」的分页与总数；属**基线公共审批查询**。
- **风险等级**：P1　**证据可信度**：高　**审批状态**：待确认

##### [BE-APR-02] 「候选审批人」判定在 3 处独立实现，且语义已经分叉

- **位置**：
  1. SQL：`ApprovalTaskMapper.java:25-29,38-42`（`assigned_user_id OR (candidate_dept_id IS NOT NULL AND EXISTS…) OR superAdmin`，角色取 `t.candidate_role_code`）；
  2. Java：`DepartmentTransferApprovalService.java:243-246,273-274`（要求 `candidateDeptId != null`，角色用**硬编码常量**，**忽略** `task.candidateRoleCode`）；
  3. Java：`IntranetRequirementService.java:131`（使用 `task.candidateRoleCode`）。
- **问题证据**：同一条「是否属于我审批」规则有三份实现。(a) 列表 SQL 允许 `assigned_user_id` 命中而 Java 侧不认——当前因 `db/approval/V4:14-21` 已把 `assigned_*` 置空而**不可达**；(b) 部门侧读常量、需求侧读任务列，**角色码一旦变化两侧行为不同**。
- **依赖关系**：决定审批列表可见性与审批动作授权。
- **推荐操作**：合并——抽出单一「候选判定」能力，优先复用 `UserMapper.hasActiveRoleInDepartment` 的口径。⚠️ **不得删除服务端的授权复核**（列表过滤不能替代动作授权）。
- **影响面**：审批中心待办列表 + 两个业务的授权判断。
- **风险等级**：P2　**证据可信度**：高　**审批状态**：待确认

##### [BE-APR-03] 空方法 + 无效参数（历史简化残留）

- **位置**：`DepartmentTransferApprovalService.java:205-207` `notifyCurrentReviewer(...)` **方法体只有注释、无条件语句**，却被 `:103` 与 `:172` 调用两次；`:201-203` `reviewerPhrase(ApprovalTaskEntity task, String deptLabel)` 的 `task` 参数**完全未使用**，`:102`/`:170` 仍传实参。
- **当前实现的作用**：无（零副作用）。方法签名保留了已废弃的「按候选组成员逐个通知」扩展位。
- **问题证据**：方法体与参数使用情况如上述行号可直接核对。
- **依赖关系**：无。
- **推荐操作**：**待确认**——删除，或补齐实现（**需业务确认**：部门审批人是否需要站内通知，当前只有 `approval.request.updated` 刷新事件）。
- **影响面**：部门调动提交、原部门通过后的通知行为（当前无通知）。
- **风险等级**：P3（若删除，真实影响是"确认了这里不发通知"）　**证据可信度**：高　**审批状态**：待确认

##### [BE-APR-04] `approveCurrent` 的「指定下一审批人」参数在生产代码零调用

- **位置**：`ApprovalWorkflowService.java:97-98,106-108`；生产调用点 `DepartmentTransferApprovalService.java:166-167,181`、`IntranetRequirementService.java:133` **全部传 `null, null`**；唯一非 null 传参在 `ApprovalWorkflowServiceTest.java:32`。
- **当前实现的作用**：支持"人工指定下一步审批人"。
- **问题证据**：`grep approveCurrent` 得到 3 个生产调用点，全部传 null；测试覆盖了该分支但**生产无入口**。配套的 `ApprovalTaskRecordMapper.nextWaiting`（`:22-25`）只被此处使用。
- **依赖关系**：公共审批状态机 API 形状。
- **推荐操作**：**待确认**——保留为 D-034「串行多步骤」的候选能力，或简化为「候选组」单一模型（若确定不做人工指定）。
- **影响面**：改动会影响公共审批状态机签名与测试。
- **风险等级**：P3　**证据可信度**：高　**审批状态**：待确认

##### [BE-APR-05] 审批状态双写：`intranet_requirement.status` 与 `sys_approval_instance.status` 并存

- **位置**：`db/intranet/V12:8`（`status TINYINT` 注释即审批生命周期）；`IntranetRequirementService.java:95,106,133-134` 与公共表写入在**同一事务内**并行；对照 D-035 正是为消除该重复而删除了部门表的 `status`。
- **当前实现的作用**：需求列表要按状态筛选/展示，直接读业务表最省事。
- **问题证据**：同一事实有两个来源、两套词汇（`0/1/2/3/4` vs `PENDING/APPROVED/...`）。部门调动已按 D-035 收敛，**需求主表未收敛**。
- **依赖关系**：需求列表筛选（`IntranetRequirementService.java:52`）、前端状态映射（`requirement/index.tsx:20-26`）、审批中心 `requestStatus`（`approvals/index.tsx:13-18`）。
- **推荐操作**：**待确认**——二选一：保留为「同一事务内的冗余投影 + 明确不变量」（需在文档里写清"业务表 status 永远由公共实例派生、不得单独修改"），或按 D-035 思路改由公共实例派生。
- **影响面**：需求列表、审批中心、通知文案；改动属业务模型调整。
- **风险等级**：P2　**证据可信度**：高　**审批状态**：待确认

##### [BE-APR-06] 需求编辑与提交/审批对同一行无锁并发，可能把 `status` 写回旧值（D-037 已明确接受）

- **位置**：`IntranetRequirementService.java:73-79`（`update()` 无行锁即 `requirements.updateById(value)`，MyBatis-Plus 默认 NOT_NULL 策略整行更新**含 status**）；`submit():80-98`、`decide():129-138`、`cancel():101-110`；全仓无 `intranet_requirement ... FOR UPDATE`。
- **问题证据**：`update` 允许 `status ∈ {0,3}`，`submit` 把 `0/3 → 1`；两个请求交错可产生「业务表回到草稿/已拒绝、公共实例仍 PENDING」的不一致。系统内现有并发控制**只作用于公共实例行**。
- **依赖关系**：⚠️ `docs/intranet/DECISIONS.md` D-037:311 与 `HANDOFF.md:13` 明示「本阶段不增加需求行锁/乐观锁……**不再列为当前缺陷**」→ 属**你已接受的边界**，本条仅登记、不建议修复。
- **推荐操作**：待确认（建议至少在文档中记录该已知不一致窗口；若将来要修，用与实例一致的行锁补齐，**不得改动现有公共实例锁**）。
- **影响面**：需求状态与公共审批状态的一致性。
- **风险等级**：P3　**证据可信度**：中（未做并发实测，静态推演 + 文档确认）　**审批状态**：待确认（建议"保留现状 + 记录"）

##### [BE-APR-07] 前端缺通用「审批详情」扩展点：新增业务必须改审批中心前端

- **位置**：`frontend/src/pages/account/approvals/index.tsx:51-61`（`INTRANET_REQUIREMENT` 分支 / 其余提示「尚未接入」）；`frontend/src/pages/account/notifications/index.tsx:42-48`（同类分支，且 `:39` **缺少 `businessType` 判空**，非 `INTRANET_REQUIREMENT` 时无条件调 `getDepartmentChange`，异常被 `.catch(() => undefined)` 吞掉）。
- **问题证据**：D-034 声明「以后新增业务只写公共表、不修改审批中心查询」在后端 SQL 成立，但**前端两处仍需按 businessType 改代码**，且两处判断口径不一致（一个给出"尚未接入"提示，一个静默失败）。
- **依赖关系**：审批中心列表行 → 业务详情接口。
- **推荐操作**：合并——抽一个 `businessType → 详情组件` 的映射表，统一两页行为（同时修掉 `:39` 的判空缺失）。
- **影响面**：审批中心、消息中心；⚠️ 两处都属**基线前端**，按 `AGENTS.md` §2 需批准。
- **风险等级**：P2　**证据可信度**：高　**审批状态**：待确认

##### [BE-APR-08] 零消费字段（前后端各一组）

- **位置**：后端 `ApprovalTaskView.java:17` `currentStep`、`:24` `decisionReason`；`DepartmentTransferModels.java:19` `canCancel`；前端 `api/approvals.ts:14,21` 与 `api/departmentChange.ts:30,32` 的同名字段（含 `finishedTime`）。
- **问题证据**：全前端 grep `decisionReason`（无 `row.decisionReason` 使用）、`canCancel`、`finishedTime` **只出现在类型声明处**；`pages/account/security/index.tsx:195,221` 是用 `activeRequest` 是否存在来决定撤销按钮，**不读 `canCancel`**。
- **依赖关系**：无。
- **推荐操作**：简化——删除零消费字段；或反过来让 UI 真正使用 `canCancel` 替代"凭存在性渲染"（后者语义更准，但属行为调整）。
- **影响面**：审批 API 投影与前端类型。
- **风险等级**：P3　**证据可信度**：高　**审批状态**：待确认

##### [BE-APR-09] 第二套状态词汇（服务端派生）+ 前端联合类型取值域不完备

- **位置**：`DepartmentTransferApprovalService.java:275-277` 派生 `PENDING_SOURCE`/`PENDING_TARGET`；前端 `api/departmentChange.ts:27` 声明该联合，但 `:12` 的 `Step.status` 联合**缺 `CANCELLED`**，而 `ApprovalWorkflowService.java:131,152` 会写入 `CANCELLED`。
- **问题证据**：`Step.status` 的真实取值域是 `{WAITING,PENDING,APPROVED,REJECTED,SKIPPED,CANCELLED}`，前端类型漏了一个，代码只能靠末尾 `else` 兜底（`approvals:223`、`notifications:168`、`security:213`）。
- **依赖关系**：三处 Steps 渲染。
- **推荐操作**：合并——把派生状态与类型定义收敛到单一处并补全取值域。
- **影响面**：部门变更详情 / 消息详情 / 安全页的步骤展示。
- **风险等级**：P3　**证据可信度**：高　**审批状态**：待确认

##### [BE-APR-10] `submit` 重复查询刚创建的实例

- **位置**：`DepartmentTransferApprovalService.java:97` 已持有 `instance`，`:106` 却 `return detail(request, actor)` → `:254-256` 再次 `approvals.get(...)` 并重查全部任务。
- **推荐操作**：简化（改传 `instance`，与 `get():112-114` 的写法一致）。
- **影响面**：单次提交多一次"实例 + 任务"查询；无行为差异。
- **风险等级**：P3　**证据可信度**：高　**审批状态**：待确认

##### [BE-APR-11] 迁移脚本与 `init_schema.sql` 的分工未写明，存在"对新库重复执行"的失败路径

- **位置**：`db/approval/V2:15,38-39,52,59` 依赖**已删除**的 `sys_department_change_request`/`_step`；`V3:4-15` 是一次性破坏性脚本；`V4:2-7` 的 `ALTER` 增加的列**已存在于** `init_schema.sql:188-191`，`V4:14-21` 的 `UPDATE` 依赖 V3 已改名。
- **问题证据**：`application.yml:22-24` 无 `mapper-locations`，全仓 `*.xml` 无 Mapper（已排除 XML 动态引用）。`MIGRATION.md:58-61` 记载 AP1/AP2/AP4 已执行、AP3 待执行。对「用 `init_schema.sql` 新建库」的环境，若再按顺序执行 V1~V4 会在建表/加列阶段**直接失败**；`V2` 在 AP3 之后**永久不可再执行**（`MIGRATION.md:79` 已声明）。
- **推荐操作**：**保留脚本**（有历史与审计价值）+ **待确认**：在 `MIGRATION.md` 明确写「全新库只用 `init_schema.sql`/`init_data.sql`，**不得**执行 `db/approval/V1~V4`」。
- **影响面**：新环境部署与回滚路径。
- **风险等级**：P2　**证据可信度**：高　**审批状态**：待确认

##### [BE-NTF-01] 单次业务动作触发两次完全相同的前端刷新

- **位置**：`DepartmentTransferApprovalService.java:217-224`（逐人 `create` → 同一集合 `approvalUpdated`）；`IntranetRequirementService.java:135-136,140-144`；`NotificationService.java:23-25` 与 `:37-39`；前端唯一消费点 `AppLayout.tsx:112-115` 对**两个事件做完全相同的处理**（`loadNotifications` + `getPendingApprovalCount`）。
- **问题证据**：`notification.created` 与 `approval.request.updated` 在唯一消费者里落入同一分支 → 每次审批动作对每个参与者产生 **2×REST 全量拉取**；`approval.request.updated` 目前**没有独立消费者**（审批中心表格未订阅实时，靠手动 `load()`）。
- **依赖关系**：`RealtimeClient` 单实例；`notificationSlice.loadNotifications` 一次拉 100 条 + 拉未读数。
- **推荐操作**：合并——二选一事件，或让两个事件各自有明确消费端。⚠️ **不得引入第二条推送通道**。
- **影响面**：所有审批动作的实时刷新与请求次数。
- **风险等级**：P2（真实请求浪费，非功能缺陷）　**证据可信度**：高　**审批状态**：待确认

##### [BE-NTF-02] 部门变更审批详情/拒绝弹窗与处理逻辑在前端重复两份（含潜在行为漂移）

- **位置**：`approvals/index.tsx:66-95,183-249` 与 `notifications/index.tsx:56-88,128-194` 近乎逐行相同（含 Steps 描述映射、拒绝 Modal 的 500 字限制、错误文案）。
- **问题证据**：两处各自维护 `approve`/`reject`/`submitting`/`reason` 状态；`notifications:39` 的详情打开兜底分支与 `approvals:57` 的未知类型分支**语义相反**。
- **依赖关系**：都调 `api/departmentChange.ts:44-46`。
- **推荐操作**：合并为一个共享详情/审批组件。⚠️ 若放到 `components/`（基线公共目录），按 `AGENTS.md` §2 需批准。
- **影响面**：审批中心、消息中心。
- **风险等级**：P2　**证据可信度**：高　**审批状态**：待确认

##### [BE-NTF-03] 业务类型/通知类型字符串字面量硬编码，前后端均无统一常量

- **位置（后端）**：`DEPARTMENT_CHANGE` 出现在 `DepartmentTransferApprovalService.java:24`、`DepartmentTransferRequestMapper.java:9`、`DepartmentTransferController.java:21-27`、`V2/V4` SQL、`OperationLog.java:22` 注释；`INTRANET_REQUIREMENT` 在 `IntranetRequirementService.java:38` + `Controller.java:30,31`；通知 type 字面量共 10 个（`DepartmentTransferApprovalService.java:101,169,212,220` 与 `IntranetRequirementService.java:107,135,140,142`）。
- **位置（前端）**：`DEPARTMENT_CHANGE` → `approvals/index.tsx:57,102`、`notifications/index.tsx:47`；`INTRANET_REQUIREMENT` → `approvals:53,132`、`notifications:38,48`；`INTRANET_REQUIREMENT_APPROVAL_REQUIRED` → `notifications:38,48`（该字面量与后端 `IntranetRequirementService.java:142` **手工对齐**）；`AppLayout.tsx:131` 拼 query 参数。
- **问题证据**：`business_type` 是公共实例唯一键的一半（`init_schema.sql:171`），**拼写不一致会静默破坏 `selectByBusiness` 与审批中心**；目前无枚举/常量表，跨模块靠字符串对齐。
- **推荐操作**：合并——建立 `businessType` / `notificationType` 常量（后端一个类，前端一个文件）。属**新增抽象**，但这里是有真实收益的（4 个模块 × 双向引用）。
- **影响面**：审批中心查询、通知跳转、明细授权判断；改动需覆盖前后端。
- **风险等级**：P2　**证据可信度**：高　**审批状态**：待确认

##### [BE-NTF-04] `NotificationService.afterCommit` 的非事务兜底分支当前零调用者

- **位置**：`NotificationService.java:41-46`（`if (!isSynchronizationActive()) { action.run(); return; }`）。
- **问题证据**：全部调用方（`DepartmentTransferApprovalService.java:64,117,122,128`、`IntranetRequirementService.java:65,67,69,70,73,80,99,100,101`）**都是 `@Transactional`**，无非事务调用点。
- **推荐操作**：**保留**（低成本保险，删除收益极低）。仅登记为历史防御代码。
- **风险等级**：P3　**证据可信度**：高　**审批状态**：建议保留

##### [BE-NTF-05] 标记已读的「消息不存在」兜底未按收件人过滤

- **位置**：`NotificationService.java:31-33`（`markRead(id, userId) == 0 && mapper.selectById(id) == null`）。
- **问题证据**：`NotificationMapper.java:16` 的 UPDATE 带 `recipient_id` 且 `read_time IS NULL`；而 `selectById` **不限 recipient**，故"他人的已读消息"与"不存在"被同等对待 → 不报错也不改数据（**无越权写入**，仅错误语义含糊）。
- **推荐操作**：简化（用 `selectById` 结果与 recipient 比较，或直接取消该兜底）。
- **风险等级**：P3　**证据可信度**：高　**审批状态**：待确认

##### [BE-NTF-06] 部门变更「步骤渲染」映射逻辑复制三份

- **位置**：`pages/account/security/index.tsx:203-213`、`pages/account/approvals/index.tsx:212-224`、`pages/account/notifications/index.tsx:157-169`（同一组 `SKIPPED/WAITING/PENDING/APPROVED/REJECTED → 文案 + status` 映射，文案略有差异）。
- **推荐操作**：合并为一个 Steps 渲染组件（或收敛到 BE-APR-09 的同一处状态映射）。
- **风险等级**：P3　**证据可信度**：高　**审批状态**：待确认

##### [BE-REQ-01] 孤儿列 + 同一概念三处表达（「期望上线时间」）

- **位置**：`db/intranet/V12:25` 的 `intranet_requirement_new_transaction_detail.expected_online_date` **无对应实体字段**（`NewTransactionRequirementDetailEntity.java:9-17` 未声明），全仓库 **0 引用**；同一概念另存于主表（`V15:3`、`IntranetRequirementEntity.java:17`）与交易调用明细（`V12:43`、`TransactionCallRequirementDetailEntity.java:15`）。
- **问题证据**：`RequirementApprovalDetailModal.tsx:132` 与 `:193` 在**同一弹窗**里渲染两个「期望上线时间」；主表值由 `RequirementDialog.tsx:83` 编辑，调用明细值由 `TransactionCallDetailDialog.tsx:48` 编辑，而**新增交易明细页没有这个字段**。
- **依赖关系**：需求概要 / 审批详情 / 调用明细三处展示。
- **推荐操作**：**待确认**——删除孤儿列，或明确「哪个是权威来源」。⚠️ **不要**删除正在使用的两处。
- **影响面**：需求表结构（需迁移脚本）、新增交易明细、审批详情展示。
- **风险等级**：P2　**证据可信度**：高　**审批状态**：待确认

##### [BE-REQ-02] 审批/驳回复用 `intranet:requirement:submit` 权限码，且 V14 只给超管授权

- **位置**：`IntranetRequirementController.java:34-35`（approve/reject 使用 submit 权限码）；`V14:8-18`（仅 `list/add/update/submit/assign` 五个码）、`:20-25`（仅 `super_admin` 授权）；`V13:14-19`（`department_approver`/`intranet_developer` 的角色授权是**注释示例**，需人工执行）。
- **问题证据**：审批授权实际落在服务端 `IntranetRequirementService.java:131`；但**接口层缺少独立审批权限码**，`submit` 与"审批"的语义/受众混同。非超管的候选审批人若**没有** `intranet:requirement:list`，在审批中心打开详情会 403（`RequirementApprovalDetailModal.tsx:54-66` 会报错并关闭）。`HANDOFF.md:14` 亦记录"非超管权限仍待人工验证"。
- **依赖关系**：审批中心入口（`ApprovalTaskController.java:15` 不要求 intranet 权限码）。
- **推荐操作**：**待确认**——新增 `intranet:requirement:approve` 并随 V14 补齐授权。这是**授权控制的增强**，**不得削减**现有服务端校验。
- **影响面**：RBAC 种子脚本 + 审批中心可用性。
- **风险等级**：P2（可用性/权限模型问题，非越权）　**证据可信度**：高　**审批状态**：待确认

##### [BE-REQ-03] 前端「需求类型/状态映射」复制 4 份

- **位置**：`requirement/index.tsx:19-26`、`RequirementDetailDrawer.tsx:4-5`、`RequirementApprovalDetailModal.tsx:8-15`、`RequirementDialog.tsx:9-11`。
- **推荐操作**：合并到 `types.ts` 的单一常量。
- **风险等级**：P3　**证据可信度**：高　**审批状态**：待确认

##### [BE-REQ-04] 明细接口直接用 Entity 作为 `@RequestBody`

- **位置**：`IntranetRequirementController.java:25,27,29`；服务端 `IntranetRequirementService.java:65,67,69` 已强制 `detail.setRequirementId(id)`。
- **问题证据**：客户端可传入 `requirementId` 但**被覆盖**，故无越权；属可接受的简化。`DECISIONS.md:311` 明示「不新增明细 Request DTO/额外服务端校验」。
- **推荐操作**：**保留**（你已接受的边界）。**风险等级**：P3　**证据可信度**：高　**审批状态**：建议保留

##### [BE-REQ-05] `decide()` 把「状态异常」与「无权审批」折叠为同一句异常文案

- **位置**：`IntranetRequirementService.java:131`（三个条件 OR 后统一抛「当前步骤不属于你审批」）；对照 `DepartmentTransferApprovalService.java:144-147` 是分两句抛出。
- **推荐操作**：简化（拆分文案便于排查；**不影响授权逻辑**）。
- **风险等级**：P3　**证据可信度**：高　**审批状态**：待确认

##### [BE-REQ-06] 需求概要详情有两套渲染实现（Drawer 与审批 Modal）

- **位置**：`RequirementDetailDrawer.tsx:7`（`column={1}`，含创建/更新时间）与 `RequirementApprovalDetailModal.tsx:126-134`（`column={2}`，无创建/更新时间）。
- **推荐操作**：合并（抽一个只读概要组件两处复用）。
- **风险等级**：P3　**证据可信度**：高　**审批状态**：待确认

##### [BE-APR-12] 文档表述过期（非代码缺陷）

- **位置**：`docs/intranet/ARCHITECTURE.md:116-126` 段落标题已标注「改造前绑定部门调动的地方（历史代码事实，2026-10-12 已处理）」，但 `:121`/`:123` 仍用**现在时**描述"前端字面量类型"与"只有 `DEPARTMENT_CHANGE` 才拉详情"；实际 `api/approvals.ts:9,16` 已是 `string`，`notifications/index.tsx:38,48` 已支持 `INTRANET_REQUIREMENT`。
- **推荐操作**：**待确认**——按 `AGENTS.md` §7「清理已经失效的临时描述」，加注"改造前"或移入历史小节。
- **风险等级**：P3（文档一致性）　**证据可信度**：高　**审批状态**：待确认

#### 2.2.4 「我必须保留的必要设计」（该模块审计明确列出，任何"删重复"都不适用）

1. `ApprovalInstanceMapper.java:15` 的 `SELECT … FOR UPDATE` + `ApprovalWorkflowService.java:80-86` 的 `@Transactional(MANDATORY)`「先锁实例、再复核状态」顺序——D-034 并发正确性的唯一保障。
2. `sys_approval_instance` 的 `uk_approval_business(business_type, business_id)`（`init_schema.sql:171`）+ `create()` 的重复校验（`ApprovalWorkflowService.java:33-35`）——并发最后防线。
3. `DepartmentTransferApprovalService.java:67` 的 `users.selectByIdForUpdate`——同一用户并发提交的串行化。
4. `ApprovalTaskMapper.java:23-42` 的候选组匹配（`dept_id` + `role_code` + status/deleted 过滤）与 `UserMapper.java:37-51` 的 `hasActiveRoleInDepartment` / `selectActiveIdsByRoleInDepartment`——授权控制本体。
5. 服务端授权复核与**禁止自审**：`DepartmentTransferApprovalService.java:145-148`、`IntranetRequirementService.java:131-132`（**不能**只依赖列表过滤）。
6. `NotificationService.java:41-46` 的 `afterCommit` 推送——防「回滚后误推」与「推送早于落库」。
7. `RealtimeGateway.java:47-50`「Redis 优先、仅失败才本地补发」+ `RealtimeSubscriptionStarter.java:31-41` 后台重订阅——防多实例重复投递、防 Redis 抖动导致启动失败。
8. `RealtimeTicketService.java:17-33` 的一次性 ticket（`getAndDelete` + 60s TTL）与 `RealtimeWebSocketConfig.java:33-38` 握手校验——WebSocket 的唯一鉴权入口。
9. `ApprovalWorkflowService.java:49-72` 的 `resubmit` 新轮次模型（修 BE-APR-01 时**不得破坏**它）。
10. `IntranetRequirementService.java:149-150` 的 `requireOwner` / `requireEditableDetail`——草稿/已拒绝才可编辑的业务闸门。
11. `SessionService.java:166-172` 经 `RealtimeGateway.closeSession` 强踢实时连接——会话失效必须能断开 WebSocket。
12. `IntranetRequirementController.java:22-29` 的 `@PreAuthorize`——即使服务端另有校验也不得删除。

#### 2.2.5 该模块的「未检查范围」（子审计明确声明）

1. 未运行任何构建/测试；编译期与运行期行为均为**静态推断**。
2. 未连接 MySQL/Redis、未执行任何 SQL：D-034/D-035 的「AP1/AP2 已执行、AP3 待执行」以及 `init_schema.sql` 与线上实际表结构是否一致，**无法复核**。
3. 未做浏览器端到端验证（审批中心/消息中心的实时刷新、非超管权限、AP3 后的部门调动回归）。
4. 未审阅 `db/intranet/V1,V3,V4,V5,V7,V8,V10` 与交易/数据资产业务代码。
5. 未审阅 `frontend` 的 `home`、`system/*` 页面与 `utils/request.ts`、`Permission` 组件实现细节。
6. 未做并发实测：BE-APR-06 为静态推演。
7. 未检查 git 历史/已删除文件内容，因此"历史重构后未删除的旧实现"只能确认**当前工作区**无残留。

### 2.3 Agent 模块与日志分析模块

> 由只读子审计会话产出后并入。该模块已有两份人工导读（`docs/AGENT-GUIDE.md` 385 行、`docs/LOG-AUDIT-GUIDE.md`），
> 子审计**逐条复核**了 `AGENT-GUIDE.md` §4 的既有结论（见 2.3.4）——**其中 4 条仍然成立、2 条数字已过期、2 条被低估**。

**规模实测**：`org.lbl.agent` 43 文件 / **1,379 行**；`org.lbl.system.log` 63 文件 / **3,057 行**；`frontend/src/components/ai` 17 文件 / **1,000 行**。

#### 2.3.1 问题清单

##### [BE-AGT-01] `AgentRunObserver` 仍是 0 实现的空扩展点

- **位置**：`backend/src/main/java/org/lbl/agent/port/AgentRunObserver.java:11-23`；注入与调用点 `org/lbl/agent/application/AgentRunner.java:49,53,59,63,112,178,182,186,190,193-199`。
- **当前实现的作用**：定义 `onStarted/onToolStarted/onCompleted/onFailed` 四个 default 方法，`AgentRunner` 注入 `List<AgentRunObserver>` 并逐个调用，`safely()` 吞掉观察者异常。
- **问题证据**：全仓库 grep `AgentRunObserver` = 8 命中（文档 3、接口定义 1、`AgentRunner` 4），**没有任何 `implements AgentRunObserver`**；`observers` 恒为空 list，10 个 `notify*`/`safely` 调用点**全部空转**。
- **依赖关系**：Spring 对空集合注入返回空 List，不会启动失败。与 BE-AGT-05 强耦合（`ModelResponse.usage`/`finishReason` 正是它最合适的载荷）。
- **推荐操作**：**待确认**——实现一个最小日志观察者（runId/耗时/工具名/是否失败），或删除接口 + 4 个 `notify*` + `safely`。
- **影响面**：删除约 50 行；实现则补上当前**完全缺失**的可观测性（运行耗时/失败率）。
- **风险等级**：P2　**证据可信度**：高　**审批状态**：待确认

##### [BE-AGT-02] `AgentProfileRegistry` 中 2 个方法零调用，多 profile 分支不可达

- **位置**：`agent/domain/AgentProfileRegistry.java:26-38`；`config/AgentProfileConfig.java:30`；`application/AgentCommand.java:9,14-16`；`application/AgentRunner.java:65`。
- **问题证据**：
  - `find(String)`（`:36-38`）：全仓库**零调用点**；
  - `defaultProfile()`（`:26-28`）：**零外部调用点**，只在同类 `resolve` 内被调 1 次；
  - `api/model/AgentChatRequest.java:14-22` **没有 `profileId` 字段** → `AgentCommand.profileId()` **恒为 null** → `resolve` 的 `:31` 是唯一可达分支，`:32-34`（查表 + 未知 id 抛异常）**不可达**；
  - `profiles` Map 只装 1 个元素。
- **依赖关系**：`AgentProfile.allows()` 是**真正的安全能力（工具白名单），必须保留**。
- **推荐操作**：**简化**——删 `find()`；`resolve()` 收敛为返回唯一 profile（或直接 `@Bean AgentProfile` 注入，省一层 registry + Map + 4 行校验）。
- **影响面**：未来若真要多助手人设需重新加回（约 30 行）。
- **风险等级**：P3　**证据可信度**：高　**审批状态**：待确认

##### [BE-AGT-03] `ToolRisk` 全仓库零读取；`ApprovalPolicy.REQUIRED` 分支不可达，且守卫行为与注释语义冲突

- **位置**：`agent/tool/ToolRisk.java:4-11`、`tool/ToolDescriptor.java:17,25`、`tool/ToolExecutionService.java:29-31`、`application/AgentRunner.java:69-74`。
- **问题证据**：
  - `ToolRisk` 全仓库 10 命中：定义 1、`ToolDescriptor` 2、4 个工具的 `ToolRisk.READ_ONLY` 4、文档 2、**`.risk()` 读取 0** → 纯写入字段。
  - `ApprovalPolicy`：4 个工具全部 `NOT_REQUIRED`，且 `AgentRunner:69-74` 生成 `toolDefinitions` 时已过滤 `REQUIRED` → `ToolExecutionService:29-31` **不可达**。
  - ⚠️ **新发现的行为不一致**：`AgentRunner:66-68` 的 `visibleTools` **包含** `REQUIRED` 工具（只有喂给模型的那份被过滤），于是 `:105` 的 `profile.allows` 与 `:111 tools.find` 仍会命中它，`ToolExecutionService:30` 抛的是 **`BusinessException`**，而 `AgentRunner:119-122` 对 `BusinessException` **只回灌模型、不推给用户**。当前无 `REQUIRED` 工具故无影响；**一旦新增写操作工具**，会出现"审批未就绪 → 模型被静默喂错误 → 用户看不到任何提示"的循环。
- **推荐操作**：**保留两个枚举**（写操作工具确实会用），但把审批拒绝改为**推给用户的错误事件**；`risk` 字段在启用前建议删除或加注释声明"当前仅写入、无消费方"。
- **风险等级**：P3（当前）／**一旦新增写工具即升 P1**　**证据可信度**：高　**审批状态**：待确认

##### [BE-AGT-04] `ToolResult` 的 `output` 通道零消费；`ToolResult.of` 零调用

- **位置**：`agent/tool/ToolResult.java:9-16`。
- **问题证据**：grep `\.output\(\)|ToolResult\.of|ToolResult\.artifact` 仅 6 命中——`artifact()` 4 处工具调用 + 2 处文档；**`of()` 零调用、`output()` 零读取**。`artifact()` 内部把 `artifact.data()` 复用为 `output`（`:15`），故 `output` **永远等于** `artifact.data()`，是冗余通道。`domain/AgentRun.java:8` 的 `toolResults` 同样零读取。
- **推荐操作**：**简化**——`ToolResult` 收敛为 `(String modelSummary, AgentArtifact<?> artifact)`。
- **影响面**：`ToolResult`、4 个工具的调用（签名不变可兼容）、`AgentRun`。⚠️ **不要**误删 `modelSummary` 与 `artifact` 的分离本身（那是必要设计，见 2.3.5）。
- **风险等级**：P3　**证据可信度**：高　**审批状态**：待确认

##### [BE-AGT-05] `ModelResponse.usage` / `finishReason` 被映射但零读取

- **位置**：`agent/port/model/ModelResponse.java:4-9`；`infrastructure/model/openai/OpenAiCompatibleModelGateway.java:74-78`。
- **问题证据**：`AgentRunner` **从不读** `response.usage()` 或 `response.finishReason()`。`finishReason=length`（被截断）与 token 用量都是真实可用信息。
- **依赖关系**：与 BE-AGT-01 **强耦合**——删观察者则这两个字段永久死掉；实现观察者则立刻有消费点。
- **推荐操作**：与 BE-AGT-01 一起决策。
- **风险等级**：P3　**证据可信度**：高　**审批状态**：待确认

##### [BE-AGT-06] `AgentProperties` 11 项中 8 项在任何 yml 中都不存在（"可配置"实为硬编码默认值）

- **位置**：`agent/config/AgentProperties.java:27-38`；`application-local.yml:82-85`；`config/AppConfig.java:10`。
- **问题证据**：yml 中实际配置的只有 3 项（`base-url`/`api-key`/`model`）。**yml 零出现**的 8 项：`chat-path`、`max-steps`、`timeout-seconds`、`run-timeout-seconds`、`strict-tool-schema`、`sse-timeout-millis`、`temperature`、`user-agent`——全部只走 `@DefaultValue`。代码读取点齐备（**无零读取字段**），问题是"从未在仓库内被配置"。
  ⚠️ 同时 `docs/AGENT-GUIDE.md:378-380` 的速查表宣称可通过 `lbl.agent.max-steps` / `timeout-seconds` 调整——**这些 key 在仓库任何 yml 中都不存在**，读者按表操作会以为已生效。
- **推荐操作**：**待确认（文档向）**——补显式配置项，或在 `AgentProperties` Javadoc 注明"仅 3 项需配置，其余走默认值"，并修正导读速查表。
- **风险等级**：P2　**证据可信度**：高　**审批状态**：待确认

##### [BE-AGT-07] `AgentPageContext.menuId` 全链路写入但零读取

- **位置**：`agent/domain/AgentPageContext.java:11`；`api/model/AgentChatRequest.java:45`；`application/AgentRequestMapper.java:24`；`application/AgentRunner.java:162-175`。
- **问题证据**：`withPageContext`（`:162-175`）只拼接 `pageTitle/routePath/breadcrumb` 三项，**未使用 `page.menuId()`**；Agent 链路上 5 处命中**全部是写入**。前端确实每轮都发（`AiAssistant.tsx:66`）。
- **推荐操作**：**待确认**——在 `withPageContext` 里加一行使用它，或从 4 处一起删除，避免"客户端字段被静默丢弃"的隐性契约。
- **风险等级**：P3　**证据可信度**：高　**审批状态**：待确认

##### [BE-AGT-08] `AgentMessage.Role.SYSTEM` 与 `AgentMessage.system()` 零构造，`toModelMessage` 的 SYSTEM 分支不可达

- **位置**：`agent/domain/AgentMessage.java:5,7-9`；`application/AgentRunner.java:150-156`；`api/model/AgentChatRequest.java:57-60`。
- **问题证据**：构造点只有 `AgentRequestMapper:28-29` 的 `user()`/`assistant()`；`AgentChatRequest.HistoryRole` 枚举只有 `USER`/`ASSISTANT`。系统指令实际由 `AgentRunner:77` 的 `ModelMessage.system(profile.instructions())` 直接注入。
- **推荐操作**：**简化**——去掉 `SYSTEM` 值与 `system()` 工厂，`switch` 改两分支；或保留并在 `HistoryRole` 写明"客户端永远不能提交 system"。⚠️ 当前形态会给读者"历史里可能混入 system 消息"的**错觉，而这恰是防注入的关键边界**。
- **风险等级**：P3　**证据可信度**：高　**审批状态**：待确认

##### [BE-AGT-09] `agentExecutor` 参数偏大

- **位置**：`agent/config/AgentAsyncConfig.java:25-30`。
- **问题证据**：`core=2 / max=8 / queue=100` → 在途运行上限 = `max + queue` = **108**（`AgentChatController:92-96` 已正确处理 `TaskRejectedException`，不会静默丢请求）。单次运行最长 110s。108 个并发 Agent 会同时对同一 LLM 网关发起调用；线程池参数**未在 yml 暴露**，过载时是"排队到超时"而非"早失败"。
- **推荐操作**：保留但在 yml 可调，或按实际用户数调小（如 `2/4/20`）。
- **风险等级**：P2　**证据可信度**：高　**审批状态**：待确认

##### [BE-LOG-01] `AuditRiskAnalysisService` 两个 public 方法零调用

- **位置**：`system/log/risk/AuditRiskAnalysisService.java:49-55`（`scanLoginRisks`）、`:58-64`（`scanOperationRisks`）。
- **问题证据**：grep 13 命中——定义 2、文档 2、调用点 2（`ChunkedAuditRiskScanService:40,44` 用的是 `*RiskChunk`）。**这两个方法自身零调用**；`AuditRiskController` 走 `scan()`。
- **推荐操作**：**删除**（约 16 行）。`scanLoginRiskChunk` 已覆盖其能力。
- **风险等级**：P3　**证据可信度**：高　**审批状态**：待确认

##### [BE-LOG-02] `AuditRiskController` + `scan()` 前端零消费者

- **位置**：`system/log/controller/AuditRiskController.java:25-31`；`risk/AuditRiskAnalysisService.java:38-46`。
- **问题证据**：`frontend/src` 全量 grep `risk|audit`（`*.ts`）= **0 命中**；全仓库 `audit-risks` 仅 2 命中（Controller 与 `docs/LOG-AUDIT-GUIDE.md:258`）。Agent 路径**完全不经过** `scan()`。
- **推荐操作**：**待确认**——删除，或保留为手工调试入口（**必须写进文档**，否则"前端零消费者的 REST 壳"会持续误导）。
- **影响面**：删除后可再收敛 `validateRange` 1 参重载与 `evaluate` 的 `truncate` 参数（约 20 行）。
- **风险等级**：P3　**证据可信度**：高（前端 0 消费者）／中（是否有意保留调试口无法从代码判定）　**审批状态**：待确认

##### [BE-LOG-03] `WindowRiskPolicy.enabled` 是 4 处死守卫

- **位置**：`risk/config/WindowRiskPolicy.java:8`；`risk/config/AuditRiskPolicyConfig.java:24-27`（4 条规则**全部 `true`**）；4 条规则各 1 处守卫：`LoginFailureBurstRule.java:38`、`LoginIpAccountSprayRule.java:38`、`PasswordChangeBurstRule.java:39`、`PasswordResetBurstRule.java:39`。
- **问题证据**：`AuditRiskPolicyConfig` 是 `WindowRiskPolicy` 的**唯一构造点**且 4 个 `enabled` 全为 `true` → 4 个 `if (!rule.enabled()) return List.of()` **永不成立**。
- **推荐操作**：保留 `enabled` 字段但删除 4 处死守卫，或彻底删除字段（需同步简化 `maxRuleWindow()`）。
- **风险等级**：P3　**证据可信度**：高　**审批状态**：待确认

##### [BE-LOG-04] `AuditRiskSeverity` 的 `CRITICAL`/`LOW` 永不赋值 → 2 个分支不可达

- **位置**：`risk/model/AuditRiskSeverity.java:5`；`risk/config/AuditRiskPolicyConfig.java:24-27`（只用 `HIGH` 3 处、`MEDIUM` 1 处）；`adapter/agent/AuditAgentReportAssembler.java:231-238`（2 个不可达 case）。
- **推荐操作**：**保留枚举**（未来加规则会用到），但把注释改为"CRITICAL/LOW 为预留级别，当前规则未使用"，避免读者误判"已支持 4 级"。
- **风险等级**：P3　**证据可信度**：高　**审批状态**：待确认

##### [BE-LOG-05] 两个"只写不读"的投影字段/查询列

- **位置与证据**：
  - **(a) `LoginRiskEvent.userAgent`**：`risk/model/LoginRiskEvent.java:14` 定义；`mapper/LoginLogMapper.java:64` 的 SQL **仍然 `SELECT ... user_agent AS userAgent`**；grep `getUserAgent` = **0 命中**（规则只用 `loginIp`/`username`）。→ 每条风险事件多传一列无用数据（`maxEventsPerSource=50_000`，32 天分片扫描时累计可观）。
  - **(b) `AuditRiskReport.returnedFindingCount`**：`risk/model/AuditRiskReport.java:11` 定义；grep = 2 命中（均为定义行），**零读取**。
- **推荐操作**：**简化**——删 `userAgent` 字段并同步删 SQL 里那一列（真实 IO 节省）；删 `returnedFindingCount`（或改为在报告里展示"被裁剪多少条"）。
- **风险等级**：P3　**证据可信度**：高　**审批状态**：待确认

##### [BE-LOG-06] `LogRetentionProperties` 全部 6 个配置项在任何 yml 中都不存在

- **位置**：`system/log/retention/LogRetentionProperties.java:20-26`；`retention/LogRetentionJob.java:45,47`。
- **问题证据**：`lbl.log.retention` 在 `application.yml`/`-local`/`-dev`/`-prod` 中**均不存在**，6 项全走 `@DefaultValue`。`LogRetentionJob` 的 Javadoc 明说"可通过 `lbl.log.retention.cron` 调整"，但仓库无任何 yml 承载该 key。`LogRetentionJob` 是 `@Component` + `@Scheduled`，**无静态引用但确实运行，不可判死**。
- **推荐操作**：**待确认（可运维向）**——在 `application-prod.yml` 补显式保留策略作为运维契约。
- **风险等级**：P2　**证据可信度**：高　**审批状态**：待确认

##### [BE-LOG-07] 时间范围校验 / topN 上限 / 排序逻辑三处重复

- **位置**：`analysis/AuditAnalysisService.java:23-25,53-68`；`risk/AuditRiskAnalysisService.java:103-113`；`adapter/agent/ChunkedAuditRiskScanService.java:121-128`；`adapter/agent/AuditTimeRangeResolver.java:62`。排序重复：`AuditRiskAnalysisService:90-94` 与 `ChunkedAuditRiskScanService:114-119` 是**同一套 4 级排序**。常量重复：`MAX_RANGE`(31天) = `AuditAgentPolicy.maxReportRange()`；`MAX_TOP_N`(50) = `AuditToolSchemas:18` = `AuditReportInput:16` 的 `@Max(50)`。
- **问题证据**：`ChunkedAuditRiskScanService.scan` **只接受**来自 `AuditTimeRangeResolver` 的 `ResolvedAuditRange`，因此 `:121-128` 的空值与先后顺序检查在 Agent 路径上**不可达**；`:125` 的 31 天检查与 `AuditAnalysisService:60` 是同一约束的两份实现。
- **推荐操作**：**简化**——统一由 `AuditAgentPolicy` 提供上限常量；把 `displayOrder()` 提为 `AuditRiskFinding` 的静态比较器供两处共用。⚠️ `AuditAnalysisService` 也服务 2 个 REST 端点（**没有** `AuditTimeRangeResolver` 保护），它的校验是**真的必要**，收敛时不能一并删除。
- **风险等级**：P3　**证据可信度**：高　**审批状态**：待确认

##### [BE-LOG-08] `topBySuccesses` / `topByRejected` / `topByFailures` 只写不读——每次报告白跑 2~3 条分组 SQL

- **位置**：`analysis/model/LoginAuditOverview.java:11-13`；`analysis/AuditAnalysisService.java:39-41`；`mapper/LoginLogMapper.java:52-60`；`OperationAuditOverview.java:12` 同理（`OperationLogMapper:52`）。
- **问题证据**：grep `topBySuccesses|topByRejected` = 10 命中，**全部在定义/赋值处，零读取**。`AuditAgentReportAssembler` 只用 `topByAttempts()`；`frontend/src` 中 `topBy*` **0 命中**（`api/loginLog.ts` 只有分页/删除）。→ 每次 `loginOverview`（Agent 每次登录报告 / 每次 `/statistics`）都白跑 2 条带 `HAVING + ORDER BY + LIMIT` 的分组查询。
- **推荐操作**：**待确认**——删除字段与 mapper 方法（-2~3 条 SQL/次），或**真正使用它**（加到报告与前端统计）。不要保留"算了不用"的中间态。
- **⚠️ 与 `docs/LOG-AUDIT-GUIDE.md:88` 的判断相反**：导读称这是"正确的复用，不是抽象过度"——SQL 片段复用确实正确，但**3 条查询里有 2 条结果从未被消费**，导读漏报了这一点。
- **风险等级**：P2（真实性能浪费）　**证据可信度**：高　**审批状态**：待确认

##### [BE-LOG-09] `ChunkedAuditRiskScanService` 的分片回看使扫描量约为区间本身的 2 倍

- **位置**：`adapter/agent/ChunkedAuditRiskScanService.java:53-66`（尤其 `:55-57`）；`risk/config/AuditRiskPolicy.java:30-37`。
- **问题证据**：31 天按 `maxScanRange()=24h` 切片；非首个分片的查询起点向前回看 `maxRuleWindow()`（= 24h，因 `passwordChangeBurst.window=24h`）→ `queryBegin = segmentStart - 24h`，除首片外每片都查询**前一分片 + 当前分片**，31 天区间实际扫描约 **2 倍**。回看本身必要（跨午夜窗口），但 `maxRuleWindow() - maxScanRange()` = 0，**回看量为 0 即可**（仅需边界包含性）。
- **推荐操作**：**保留但修正**——`queryBegin = max(beginTime, segmentStart.minus(maxRuleWindow().minus(maxScanRange())))`，并补跨午夜窗口的断言测试（⚠️ 导读指出 `SlidingWindowDetectorTest`/`AuditRulesTest` 已被删除，**当前无任何 Agent/日志测试**）。
- **影响面**：31 天报告 DB 读取量约减半；修正需回归"跨午夜不漏判"。
- **风险等级**：P2　**证据可信度**：高　**审批状态**：待确认

##### [BE-LOG-10] 分页端点用闭区间、统计/下钻/Agent 用半开区间（语义不一致，用户可见）

- **位置**：`service/LoginLogService.java:69-70`（`.ge/.le`）；`service/OperationLogService.java:87-88`（同）；`controller/LoginLogController.java:44` 注释写"左闭右开"；`mapper/LoginLogMapper.java:76-89`（`>=` **且** `<`）；`mapper/OperationLogMapper.java:92`（同）。
- **问题证据**：同一次界面查询中，同一对 `beginTime/endTime` 在 `page` 里是闭区间、在 `statistics` 里是半开区间。`LoginLogController:44` 的 Javadoc 明确写「时间采用左闭右开区间」——**与它自己控制器里的 `page` 端点语义不符**。→ **边界那一秒的日志会出现在列表里、却不出现在统计里**。
- **推荐操作**：**待确认（业务语义决策）**——统一为半开区间，或在 `page` 上补注释并在界面说明。
- **风险等级**：P2（用户可见的数据对不上）　**证据可信度**：高（代码事实）／中（是否为有意的产品语义无法从代码判定）　**审批状态**：待确认

##### [BE-LOG-11] `LogResult` 常量在 3 个地方各有一份副本

- **位置**：`support/LogResult.java:10-14`；`mapper/LoginLogMapper.java:21-23,34-36`（SQL 硬编码 3 处）；`mapper/OperationLogMapper.java:22-23,34-35`（2 处）；前端 `loginlog/index.tsx:14-23` 与 `operatelog/index.tsx:14-21`（两份独立映射）。
- **问题证据**：`LogResult` 的 Javadoc 声明它存在的原因是"拼错一个字母就会让筛选条件永远查不到数据"——但 mapper 的 SQL **无法引用 Java 常量**，前端另有 2 份，常量类**只保护了 Java 调用方，没保护真正容易写错字面量的地方**。
- **推荐操作**：**保留**（不为 3 个字面量引入枚举 + TypeHandler），但建议在 mapper 类级 Javadoc 注明一致性要求，并把前端 `RESULT_META` 抽到一处共用（注意 `LOCKED` 仅登录产生，是有意差异）。
- **风险等级**：P3　**证据可信度**：高　**审批状态**：待确认

##### [BE-LOG-12] `AuditAnalysisService` 的 Javadoc 承诺与实现不符

- **位置**：`analysis/AuditAnalysisService.java:15-20`。
- **问题证据**：类注释写"后续规则引擎、REST 接口和 AgentTool 都应复用这里"，但 `risk/**` 的 4 条规则与两个 `findRiskEvents` 明细 SQL **完全绕开本类**；`risk` 层的时间范围校验与 `analysis` 层各写一套。
- **推荐操作**：**保留代码、修正注释**。
- **风险等级**：P3　**证据可信度**：高　**审批状态**：待确认

##### [BE-LOG-13] JSON Schema 与 Bean Validation 双写：实际 6 组（导读只列了 2 组），且零测试断言

- **位置**：`adapter/agent/AuditToolSchemas.java:18,24,25,27,41`；`adapter/agent/model/AuditReportInput.java:11,12,16`；`AuditUserTimelineInput.java:12,13,16,17,18`。
- **逐对核对结果（全部一致，但无任何测试断言）**：

| 约束 | JSON Schema | Bean Validation |
| --- | --- | --- |
| `topN` 1–50 | `AuditToolSchemas:18` | `AuditReportInput:16` `@Min(1) @Max(50)` |
| `recentHours` 1–24 | `AuditToolSchemas:41` | `AuditReportInput:12` / `AuditUserTimelineInput:13` |
| `userId` ≥1 | `:24` `minimum=1` | `AuditUserTimelineInput:16` `@Min(1)` |
| `username` ≤64 | `:25` `maxLength=64` | `AuditUserTimelineInput:17` `@Size(max=64)` |
| `limit` 1–100 | `:27` | `AuditUserTimelineInput:18` `@Min(1) @Max(100)` |
| `range` 枚举 | `:39` enum 4 值 | `AuditReportInput:11`/`AuditUserTimelineInput:12` `@NotNull` + 枚举类型 |
| `anyOf userId/username` | `:29-31` | **无对应注解**，由 `AuditTimelineService:58-63` 运行期抛 `BusinessException` 兜底 |

- **推荐操作**：**保留双写但加护栏**——把上下限抽为共享常量（Schema 与注解都引用它），或补一致性断言测试。
- **影响面**：若漂移，会出现"Schema 允许但服务端拒绝"（`BusinessException` **只回灌模型、用户看不到原因**）或反向（模型永不请求，静默不可用）。
- **风险等级**：P2　**证据可信度**：高　**审批状态**：待确认

##### [BE-LOG-14] 前端制品注册表：注销能力零消费，`chart` 是永久占位，re-export 无用

- **位置**：`frontend/src/components/ai/artifacts/ArtifactRendererRegistry.ts:16-24`；`artifacts/AgentArtifactView.tsx:7,9`；`types/agent.ts:42`；后端 `AgentReportSection.java:8`。
- **问题证据**：
  - `registerArtifactRenderer` 返回注销函数（`:23`），但 `AgentArtifactView.tsx:7` 在**模块顶层**调用并丢弃返回值 → 注销能力**零调用点**；模块顶层副作用也使该能力无法被 HMR/懒加载正确清理。
  - `AgentReportSection.type` 联合类型含 `'chart'`、后端 Javadoc 写"chart 数据预留在 rows 中"，但**5 个静态工厂没有 chart**、前端 `GenericReportRenderer.tsx:12-19` **没有 chart 分支**（落 default 提示）→ 两端同时声明的、无人产出的占位。
  - `AgentArtifactView.tsx:9` 的 re-export **零消费者**。
- **推荐操作**：**简化**——注销函数改为 `void` 或用 `useEffect` 真正清理；删无用 re-export；`chart` 要么实现、要么移除。
- **风险等级**：P3　**证据可信度**：高　**审批状态**：待确认

#### 2.3.2 与 `docs/AGENT-GUIDE.md` §4 的逐条复核结果

| 导读条目 | 导读原结论 | 本次核对 | 判定 |
| --- | --- | --- | --- |
| §4.1 Profile 机制 | registry 只 1 个 profile；`find()` 零调用；`resolve()` 只剩默认分支 | 一致；**新增**：`defaultProfile()` 也零外部调用 | **成立** |
| §4.2 `AgentRunObserver` | 0 实现 | grep 8 命中，无 `implements` | **成立** |
| §4.3 `ToolRisk` 死字段 | 8 个工具全 `READ_ONLY`；`.risk()` 零读取 | `.risk()` = **0 命中**；但 `READ_ONLY` 写入是 **4 处**（4 个 `@Deprecated` 工具删除后只剩 4 个工具） | **成立**，但"8 个工具"**数字已变化** |
| §4.3 `REQUIRED` 不可达 | `AgentRunner:71` 已过滤 | 一致；**新增** BE-AGT-03 的行为不一致 | **成立** |
| §4.4 线程池 | "理论可积压 800" | 原值未变，但在途上限实为 `max + queue` = **108** | **成立**；导读**算术有误** |
| §3.3 domain 层"15 个类" | 15 | 实际 **17 个**（`domain/*` 10 + `report/*` 7） | **已变化**（标题数字过时） |
| §3.3–3.7 各类行数 | 多处 | 实测普遍 **+2~8 行**（注释/Javadoc 扩写） | **已变化** |
| §5 Schema 双写 | 2 处（`topN`/`recentHours`） | 实际 **6 组** + 1 组只有 Schema 侧 | **成立但低估** |
| §7 `docs/AI-AGENT.md` 失效引用 | 1 处 | 仍成立，且 `application-local.yml:81` 也引用同一文件 | **成立**（不止一处） |

#### 2.3.3 该模块的「必须保留的必要设计」（子审计明确列出）

1. **`AgentActor` 权限快照 + `ToolRegistry.visibleTo` 权限过滤 + `ToolExecutionService:26-28` 二次鉴权**——三处独立证据链，**三重校验不是冗余**：`visibleTo` 决定模型看得见什么，`AgentRunner:105` 防模型幻觉工具名，`ToolExecutionService:26` 防绕过 `AgentRunner` 的直接调用。**不要合并。**
2. **`AgentChatRequest` 白名单 DTO**——刻意不复用模型 DTO、不收权限码/角色/任意 Map；所有 `@Size` 上限都必须保留。
3. **`AgentRunner.withPageContext` 把客户端数据拼进 USER 消息**并显式标注"不代表权限，也不是系统指令"——防提示注入的正确设计。
4. **`ToolResult.modelSummary` 与 `artifact` 分离**——应删的是 `output` 通道（BE-AGT-04），**不是**这个分离本身。
5. **`AgentRunner` ReAct 循环的四种防护**：失败工具黑名单、`profile.allows` 二次校验、`BusinessException`/`AccessDeniedException` 分开 catch、`CancellationException` 直接 rethrow——每种都对应真实故障模式。
6. **独立线程池跑慢速 LLM IO + `AbortPolicy` + `TaskRejectedException` 处理**。
7. **`CancellationToken` + `AgentExecutionContext.checkpoint()`**（8 个调用点）。
8. **4 个意图级审计工具 + `mode` 参数**——让模型一次调用定意图，避免 SQL 次数不可预测。
9. **`AuditTimeRangeResolver` 服务端解释时间预设 + Asia/Shanghai 固定**——限制模型只能选预设；可注入 `Clock` 保留测试能力。
10. **`SlidingWindowDetector` 的双指针 + 重叠窗口合并**——"8 次失败只生成 1 条风险"无法用 SQL 表达，是全程**唯一真正的算法**。**建议恢复其被删除的测试。**
11. **`AuditRiskFinding.evidenceLogIds`**——风险结论带可回查证据 ID，审计场景的硬要求。
12. **`AuditRiskContext` 只读快照 + 一次查询 4 规则共享**——删了会让 4 条规则各查一次库。
13. **`OperationLogAspect` 的 `@Order(0)` + `finally` 落库**——权限被拒绝也要记 FAILURE 尝试，是审计价值；`OperationLogService.record` 内部吞异常保证审计失败不影响业务。
14. **两个 `record` 的 `REQUIRES_NEW` + 内部吞异常**——业务回滚不能把审计一起回滚。
15. **`LogRetentionJob` 分批删除 + 上限 + 无 `@Transactional`** 与 `keepDays` 的"至少保留 1 天"兜底。
16. **`maxEventsPerSource + 1` 的溢出探测**——用"取 N+1 行判断是否超限"避免全量拉取。
17. **`RequestInfo` 的可信代理判定与 `TrustedProxyResolver` 的静态注入折中**——审计 IP 与限流 key 必须同源。
18. **`AgentArtifact.type + schemaVersion` 注册表 + 未知类型安全降级**。
19. **`AgentProfileConfig` 的 10 条 system 指令**——尤其第 2（防幻觉）、3（防工具结果注入）、8（mode 选择与禁止按天拆分）、9（事实/推断/建议三分）、10（时间必须用预设）条。
20. **`ToolRegistry` 的启动期校验**（名称正则、description 非空、重名即 fail-fast）。
21. **`OpenAiCompatibleModelGateway` 的三处实现纪律**：异常不写响应体、`RestClient` 懒加载 + 双重检查、`ensureConfigured()` 明确文案。
22. **`AuditToolSummary` 的模型摘要体积上限**与 `AuditAgentReportAssembler.safe()` 的换行清洗。

#### 2.3.4 该模块的「未检查范围」（子审计明确声明）

未启动应用、未执行任何构建/测试/静态分析；所有"不可达/死分支"结论均基于源码控制流与全仓库引用检索，**未做字节码或运行期验证**。未逐列核对 `sys_login_log`/`sys_operation_log` 的 DDL 与 `RequestInfo` 中的长度常量（仅从注释采信）。未查看 commit 历史，因此"某段代码是为哪个需求写的"无法验证。`frontend/public/monaco/**`、`backend/target/**` 已排除。

### 2.5 登录 / 鉴权 / 会话全链路

> 由只读子审计会话产出后并入。⚠️ 检索口径提示：`application-local.yml` 被 `.gitignore` 忽略，
> ripgrep 默认**不检索**它；该子审计已单独 `read` 并纳入结论。所有命中数均指**非忽略文件**。

#### 2.5.1 全链路现状（用于判断"哪一层重复"）

```text
会话建立（4 条入口）
  A1 口令登录   POST /api/auth/login
     AuthController:53-64 → AuthService.login:44-73
       （限流预检 :47-52 → 查用户 :53 → 查凭据 :54 → BCrypt :55-56 → 失败计数+登录日志 :57-61
        → 成功清计数 :65 → 改密策略 :66 → 建会话 :69 → 签发 JWT :71）
     → AuthController:58 extractSid（jwt.parse:41-43 再解析刚签发的令牌）→ :61 删旧会话 → :62 下发刷新 Cookie
  A2 自助注册   POST /api/auth/register
     AuthController:66-71 → RegistrationService.register:60-71（captcha.verify:65 → PasswordRules:66 → createUser:67 → savePassword:69 → issue:70）
  A3 外部登录   GET /api/auth/external/{p}/start → beginLogin:116-123 → begin:133-171 → 第三方 → /callback
     → complete:184-227（consume:188 → 校验:189-196 → verifyIdentity:254-274 → ExternalLoginPersistence.settle:58-103）
  A4 开户确认   settle:79-89 建开户态会话 → /account/setup → POST /api/auth/onboarding/account/{create|bind}
     → RegistrationService.createFromOnboarding:74-91 / bindFromOnboarding:94-128 → 换发会员会话 → 删开户态会话

每请求鉴权链（5 层）
  1. springSecurityFilterChain（order -100）
  2. WebSecurityConfig:35-75（CSRF 关、CORS、STATELESS、授权规则 :41-66、401/403 出口、addFilterBefore(jwt))
  3. JwtAuthenticationFilter:43-81（Bearer → jwt.parse:46 → sessions.find:48 → 开户态:49-59 / 会员:60-71）
  4. Controller 层 @PreAuthorize
  5. Service 层 AccessPolicy.actor():50-75（按用户名再查用户:53 → 角色:55 → 部门全表:58 → 菜单权限:420 → roleMenus:98 → roleDepts:105）

续期与失效
  401 → services/authSession.ts:14-27（并发合并）→ POST /api/auth/refresh → AuthService.refresh:98-117
  空闲续期 UserActivityManager.ts（5 分钟节流）→ POST /api/auth/touch → AuthService.touch:119-131 → SessionService.touch:92-109
  退出 / 设备管理 / 改密重置停用 → authVersion+1 + sessions.removeAll + realtime.closeSession
```

**各层职责重复速查表（子审计给出，是本节多数结论的判据）**：

| 关注点 | Filter | Controller | Service | 结论 |
| --- | --- | --- | --- | --- |
| token 解析 | `:46` | `AuthController:154`（登录回程） | — | 重复 → BE-SEC-03 |
| 会话解析 | `:48` | `OnboardingAccountController:56` | `AuthService:99/113/115` 内共 3 次 | 重复 → BE-AUTH-07/08 |
| 用户查询 | `:60`（by id） | `AuthController:115`（by id） | `AccessPolicy:53`（**by username**） | 重复 → BE-SEC-01 |
| 权限/菜单查询 | `:64` | `AuthController:120/124/127` | `AccessPolicy:420/241` + roleMenus/roleDepts | 重复 → BE-SEC-01 |
| 会话有效性 | `:61-62` | `AuthController:116` | `AuthService:106/127` | **4 处** → BE-AUTH-03 |
| 凭据可用性 | — | `AuthController:130` | 5 处 | **7 处** → BE-AUTH-02 |
| 密码校验+锁定+日志 | — | — | `AuthService:47-65` / `RegistrationService:100-118` | **2 份** → BE-AUTH-01 |

#### 2.5.2 问题清单

##### [BE-SEC-01] filter 已完成身份/权限加载，`AccessPolicy.actor()` 又按用户名重查一遍

- **位置**：`security/filter/JwtAuthenticationFilter.java:46-70`；`security/context/AccessPolicy.java:51-61,418-423`。
- **问题证据**：filter `:60 users.selectById(s.userId())`、`:64 menus.selectByUserId(...)`；`AccessPolicy:53 users.selectOne(eq(username, authentication.getName()))`、`:420 menus.selectByUserId(userId)`。`CurrentUser:14-18` 已把 `id/username/deptId` 放进 principal 且 `getName()` 返回 username，**因此这次按用户名查询与 filter 的按 id 查询是同一个用户**。`menus.selectByUserId` 全仓 6 个调用点（filter:64、AccessPolicy:241/420、AuthController:124、UserService:92、RoleService:161）；`access.actor()` 全仓 40+ 调用点，且存在**单方法多次调用**（`DepartmentTransferApprovalService:57,61,66,110,130,140`、`IntranetRequirementService:149`、`UserService:137,143`）。
- **依赖关系**：⚠️ ONBOARDING 的 principal 是 `OnboardingPrincipal`（无 userId），`actor()` 现在正是靠"按用户名查不到"来拒绝开户态（`UserController.java:150` **明确记录了这个副作用**）——改动时必须保留该行为。
- **推荐操作**：**简化**（`CurrentUser` 分支用 principal 的 id/deptId，保留 ONBOARDING 现有"解析不到 → 401"；或给 `actor()` 加请求级缓存）。**与我的 BE-CMN-01 是同一处问题的两个视角**，结论一致。
- **影响面**：鉴权热路径，20+ 调用方；每请求可省 1 次用户查询 + 1 次菜单权限查询（多调用点场景成倍）。
- **风险等级**：**P1**　**证据可信度**：高　**审批状态**：待确认

##### [BE-SEC-02] `JwtService` 两个 `issue` 重载，唯一差异是一个**只写不读**的 claim

- **位置**：`security/jwt/JwtService.java:26-39`。
- **问题证据**：3 参重载调用点 2 处（`AuthService:71,116`）；2 参重载 5 处（`AuthService:103`、`RegistrationService:202`、`ExternalLoginPersistence:87,147,174`）。两者**仅差 `"principalType"`**（`:35`），而该 claim 全仓**只有写入点、无读取点**（读取方 `JwtAuthenticationFilter:46-49` 只取 `sid`/`subject`/`authVersion`；`principalType` 的其它 5 个命中都在 `LoginSession` 与 `AuthController` 的**响应体**里，与 JWT 无关）。
- **依赖关系**：无外部消费方；claims 是令牌格式契约，**删 claim 不影响旧令牌解析**（多余 claim 会被忽略）。
- **推荐操作**：**合并**为一个 `issue(sid, LoginSession)`。
- **风险等级**：**P1**　**证据可信度**：高　**审批状态**：待确认

##### [BE-SEC-03] 登录回程把刚签发的令牌再解析一次以取回**已知的** sid

- **位置**：`auth/controller/AuthController.java:57-62,153-155`。
- **问题证据**：`AuthService.login:69-71` 已经持有 `sid`，但 `LoginResult:3` 只带 `accessToken`；于是 Controller `jwt.parse(result.accessToken()).get("sid")`，对**同一请求内自己刚签发的令牌**再做一次 Base64 解码 + HMAC 验签。
- **依赖关系**：⚠️ `LoginResult` 是**出网 DTO**，**不能**把 sid 塞进去——那等于把刷新凭据暴露给 JS（属安全边界，见 2.5.4）。
- **推荐操作**：**简化**（让 `AuthService` 返回内部对象携带 sid，或把签发与 Cookie 下发收进同一处）。
- **影响面**：登录/注册/开户三条路径的 Controller 与 Service 签名。
- **风险等级**：**P2**　**证据可信度**：高　**审批状态**：待确认

##### [BE-SEC-04] 同一 SpEL 鉴权表达式 **8 份**、同一组开户态权限码 **2 份**

- **位置**：`ApprovalTaskController:15`、`DepartmentTransferController:13`、`RealtimeController:17`、`NotificationController:13`、`AccountProfileController:14`、`AccountSessionController:19`、`AccountIdentityController:30`、`UserController:157`；权限码在 `JwtAuthenticationFilter:52-54` 与 `AuthController:111` 各写一遍。
- **问题证据**：`grep "isAuthenticated() and !hasAuthority('onboarding:access')"` = **8 命中，逐字相同**；权限码列表全仓 7 命中。⚠️ `UserController:144-154` 的注释记录了**真实漂移史**：「全项目其余成员接口都是这个口径……**只有这里漏了**」。
- **依赖关系**：⚠️ 表达式中的 `!hasAuthority` 是**排除式，不能改成 `hasAuthority('xxx')`**（`UserController:140-142` 记录了原因：**待改密用户必须能调用改密接口**）。
- **推荐操作**：**简化**（抽元注解 `@MemberOnly` 或共享常量；权限码列表抽为常量供 filter 与 `/auth/me` 共用）。⚠️ 8 个 Controller + 1 filter + `/auth/me`，**错一处即产生"前端有入口、后端 403"或反之**。
- **风险等级**：**P1**　**证据可信度**：高　**审批状态**：待确认

##### [BE-AUTH-01] 「密码校验 + 失败计数 + 登录日志」编排在两个 Service 各写一遍

- **位置**：`auth/service/AuthService.java:44-72` 与 `auth/service/RegistrationService.java:93-128`；辅助方法 `AuthService:92-96` 与 `RegistrationService:130-142`。
- **问题证据**：两段**逐行同构**（`isLocked → 查用户 → 查凭据 → enabled → passwords.matches → recordFailure → 写登录日志 → locked ? 429 : 400 → recordSuccess`）；差异仅 3 处：日志文案、失败原因方法（注册侧多一类"未设置登录密码"）、注册侧无来源配额。`grep "passwords\.matches"` 全仓 3 命中。
- **依赖关系**：两处**共享 `LoginAttemptGuard`**（这是正确的既有抽象）；重复的是**编排**而非计数。⚠️ 合并时**必须**保证两条入口都继续计数（`RegistrationService:96-98` 的注释正是因此写下）。
- **推荐操作**：**合并**（抽 `PasswordAuthenticator.verifyOrFail(...)`）。需回归**四条行为**：锁定阈值 / 429 / 日志文案 / **账号枚举防护**。
- **风险等级**：**P1**　**证据可信度**：高　**审批状态**：待确认

##### [BE-AUTH-02] 「凭据是否可用」判定在 **7 处**重复，且有**两种写法**

- **位置**：`AuthService:55`、`RegistrationService:109,140`、`PasswordChangePolicy:73`、`UserPasswordService:70`、`AuthController:130`、`AccountIdentityController:89`。
- **问题证据**：`grep "getEnabled\(\)"` = **7 命中**，同时存在 `credential.getEnabled() != 1`（4 处）与 `!Integer.valueOf(1).equals(credential.getEnabled())`（3 处）**两种写法**；`AuthController:130` 与 `AccountIdentityController:89` 是**逐字相同**的表达式。前端 `types/auth.ts:68` 的 `hasPassword` 与 `AppLayout:293` 的菜单项依赖该值。
- **推荐操作**：**合并**（在 `LocalCredentialEntity` 加 `usable()`，或抽 `CredentialService.hasPassword(userId)`）。
- **影响面**：7 个调用点，含"解绑最后一个登录方式"的判定（`AccountIdentityController:88-95`）。
- **风险等级**：**P1**　**证据可信度**：高　**审批状态**：待确认

##### [BE-AUTH-03] 「会话是否仍有效」判定在 **4 处**重复

- **位置**：`JwtAuthenticationFilter:60-62`、`AuthService:106`、`AuthService:127`、`AuthController:116`。
- **问题证据**：四处都判 `user.getStatus()==1 && user.getAuthVersion().equals(session.authVersion())`；filter 另加 `subject` 与 `authVersion` 两项**令牌级**校验。
- **依赖关系**：`auth_version` 自增是"改密/停用即踢下线"的实现手段（`UserPasswordService:58,80`、`UserService:204`），**四处必须同一口径**，否则出现"某条路径允许旧会话继续用"。
- **推荐操作**：**合并**（把"用户/快照一致性"收进 `SessionService` 或 `LoginSession.matches(UserEntity)`）。⚠️ **保留** filter 里额外的令牌 claim 校验——那是**防重放边界**。
- **风险等级**：**P1**　**证据可信度**：高　**审批状态**：待确认

##### [BE-AUTH-04] 失败锁定存在两套独立实现（窗口/上限相同、key 与维度不同）

- **位置**：`auth/service/LoginAttemptGuard.java:38-47,86-135` 与 `system/user/service/UserPasswordService.java:26-27,67-68,86-95`。
- **问题证据**：两处各自声明 `5` 与 `Duration.ofMinutes(15)`，各自实现 `increment + 首次 expire + 读取计数`，key 前缀不同（`auth:login:*` vs `auth:password-change:fail:{userId}`）。**与 BE-VAL-13 是同一处问题的两个视角。**
- **依赖关系**：`LoginAttemptGuard.requireSourceQuota:122-129` 是按**来源**配额，无法直接表达"按 userId"，因此合并需要给它增加一个"按身份标识"的计数方法。
- **推荐操作**：**合并**（共用窗口/上限常量与计数实现；两条路径都保留限流语义）。⚠️ 阈值必须一致，否则出现两套"5 次/15 分钟"。
- **风险等级**：**P1**　**证据可信度**：高　**审批状态**：待确认

##### [BE-AUTH-05] 外部身份绑定前置校验两份，且**语义不一致**（DB 唯一索引才是真边界）

- **位置**：`RegistrationService:178-194` 与 `auth/external/ExternalLoginPersistence.java:122-141`；DB 约束 `db/init_schema.sql:70-71`。
- **问题证据**：两份都做同样两项检查，但 **Registration 版对"已有同 provider"一律拒绝**（`:181-183`）；**Persistence 版在"同 provider 且同 subject"时提前 return 实现幂等**（`:127-130`），文案也不同。真正的最终边界是 DB 唯一键 `uk_external_subject(provider_key,issuer,subject)` 与 `uk_user_provider(user_id,provider_key)`。
- **推荐操作**：**合并**（抽 `ExternalIdentityBinder`）。⚠️ **保留 DB 唯一索引**。"重复绑定"到底是幂等还是拒绝，**属行为差异，需业务确认后统一**。
- **风险等级**：**P1**　**证据可信度**：高　**审批状态**：待确认

##### [BE-AUTH-06] `normalizeIssuer` 两份**逐字相同**的私有实现

- **位置**：`RegistrationService:210`、`ExternalLoginService:473`。
- **问题证据**：函数体完全相同（`value == null ? "" : value`），全仓 5 命中（2 定义 + 3 调用）。该归一化**直接决定唯一键是否命中**，两处漂移会导致"同一身份被当成两个"或误判重复。
- **推荐操作**：**合并**（提到 `VerifiedIdentity` 或共享工具）。
- **风险等级**：**P0**（逐字重复 + 影响唯一键匹配）　**证据可信度**：高　**审批状态**：待确认

##### [BE-AUTH-07] 同一请求内会话被反复从 Redis 读取

- **位置**：`AuthService:99,103,113,115`；`session/SessionService.java:75-89,92-109,111-118`；`RegistrationService:201`；`AuthController:62,69`；`OnboardingAccountController:75`；`ExternalAuthController:89,122`。
- **问题证据**：`AuthService.refresh` **单次调用内 `sessions.find` ≥3 次**（`:99` 直接；`:113` 内部 `SessionService:112`；`:115 touch` → `SessionService:93`；开户态分支 `:103` 再多 1 次）。每次签发后还会为拿 `LoginSession` 再读一次。`grep "sessions\.find\("` 全仓 **19 命中**。另：`SessionService.touch:97-102` 的 `seconds == null || seconds <= 0` 分支在 `find:83` 已确认 `absoluteKey` 存在之后**几乎不可达**（仅覆盖 -1/竞态），属"合理但重复"。
- **推荐操作**：**简化**（让 `touch()`/`updatePasswordChangeRequired()` 接收已解析的 `LoginSession`，或做请求级缓存；绝对期限检查保留一处即可）。
- **影响面**：登录/注册/开户/刷新/外部回调**全部路径**；每次可省 1~3 次 Redis 往返 + JSON 反序列化。
- **风险等级**：**P1**　**证据可信度**：高　**审批状态**：待确认

##### [BE-AUTH-08] 开户确认路径上 filter 与 Controller **各解析一次会话**（重复鉴权入口）

- **位置**：`JwtAuthenticationFilter:47-58`；`auth/controller/OnboardingAccountController.java:35-38,55-58`。
- **问题证据**：filter 已完成 Bearer 解析 + 会话存在性 + `"onboarding:"+s.onboardingId()` 与 JWT subject 的一致性校验，并把 `OnboardingPrincipal` 放进 SecurityContext；Controller 却**完全不用 principal**，改用 `@CookieValue(RefreshCookieFactory.NAME)` 再 `sessions.find(sid)`，且**只判 `session.onboarding()`**（比 filter **少校验 subject/onboardingId**）。`RefreshCookieFactory:38` 的 `path("/api/auth")` 恰好覆盖 `/api/auth/onboarding/**`，因此两条凭据同时存在 → 同一请求出现**两种"当前身份"来源**。
- **依赖关系**：`WebSecurityConfig:60` 对 `/api/auth/onboarding/**` 要求 authenticated，正是为了这条路径同时拥有 Bearer 与 Cookie。
- **推荐操作**：**合并**（改用 `@AuthenticationPrincipal OnboardingPrincipal` + 其 `onboardingId` 取会话，或明确二者分工并加注释）。⚠️ **不得因此降低任一层校验强度**。
- **风险等级**：**P1**　**证据可信度**：高　**审批状态**：待确认

##### [BE-SEC-05] `shouldNotFilter` 仍放行**已删除的** springdoc 路径，与 `WebSecurityConfig` 口径不一致

- **位置**：`JwtAuthenticationFilter.java:84-88`；对照 `config/WebSecurityConfig.java:61-65`。
- **问题证据**：`WebSecurityConfig:61-64` **明确写出**"springdoc/swagger 依赖已移除，原先的 `/swagger-ui/**` 与 `/v3/api-docs/**` 放行规则一并删掉"，而 filter 仍 `uri.startsWith("/swagger") || uri.startsWith("/v3/")`。全仓 `swagger`/`v3/api-docs` 仅这两处命中。行为上仍安全（跳过鉴权 → 无 token → 401），但两条口径已不一致。
- **关联**：与 §5 的 FE-DEP-04（`.env` 入库）同属"配置/代码不再反映真实对外面"这一类。
- **推荐操作**：**简化**（删除这两个前缀，或补注释说明为何保留）。
- **风险等级**：P3　**证据可信度**：高　**审批状态**：待确认

##### [BE-SEC-06] `TrustedProxyResolver.hasTrustedProxies()` 描述的外部调用方不存在

- **位置**：`security/proxy/TrustedProxyResolver.java:63-66,78`。
- **问题证据**：全仓该类名命中**全部在本文件内**（定义 `:64` + 内部使用 `:78`）；javadoc 声称"调用方可直接走直连分支，省掉无意义的头部解析"，但**没有任何类持有/调用它**。`RequestInfo.useResolver:40-42` 走的是 `resolve()`。
- **推荐操作**：**简化**（改为 `private`，或修正 javadoc；**不要删方法本身**——`:78` 在用）。
- **风险等级**：P3　**证据可信度**：高　**审批状态**：待确认

##### [BE-SEC-07] `JwtAuthenticationFilter` 既是 `@Component` 又被 `addFilterBefore`，二次执行只靠隐式守卫

- **位置**：`JwtAuthenticationFilter.java:27-28`；`config/WebSecurityConfig.java:74`。
- **问题证据**：全仓 `FilterRegistrationBean` **0 命中**（无显式排除）；`@Component` 会让 Spring Boot 把同一个 bean **再注册为容器级 Filter**（order 为 `LOWEST_PRECEDENCE`，排在 `springSecurityFilterChain` 之后）。当前**不产生双跑**，唯一依赖是 `OncePerRequestFilter` 的 request attribute 守卫。
- **依赖关系**：该守卫是 **Spring 内部约定**，不是本项目显式声明；改动过滤器顺序/自定义 `SecurityContextHolderFilter` 行为时需重新确认。
- **推荐操作**：**待确认**（建议加 `FilterRegistrationBean.setEnabled(false)` 或加注释把这条隐性约束固化）。⚠️ **不是缺陷，也不要因此删掉 `@Component`**——`WebSecurityConfig:35` 依赖注入它。
- **风险等级**：P3　**证据可信度**：中（由 Spring 语义推导，未运行验证）　**审批状态**：待确认

##### [BE-AUTH-09] `SessionGrant.onboarding` 字段零读取

- **位置**：`auth/model/SessionGrant.java:3`。
- **问题证据**：4 处构造全部传字面量；`grep "grant\."` 全仓 12 命中，只出现 `sid()/accessToken()/user()/passwordChangeRequired()`，**无 `onboarding()`**。⚠️ 同名方法 `LoginSession.onboarding():42` 另有 25 个命中，**容易误判**——删除时必须限定类型。
- **推荐操作**：**删除**该字段（如需表达，用现成的 `principalType`）。
- **风险等级**：**P0**（零读取，删除风险极低）　**证据可信度**：高　**审批状态**：待确认

##### [BE-AUTH-10] `VerifiedIdentity.attributes` 零读取（却承载第三方返回的**完整 profile**）

- **位置**：`auth/identity/VerifiedIdentity.java:6`；写入 `ExternalLoginService:271-273`、`UiasLoginService:117-118`。
- **问题证据**：全仓 `VerifiedIdentity` 12 命中，**无任何 `.attributes()`**；`ExternalLoginPersistence` 只消费具名分量。该字段参与 record 的 `equals/hashCode/toString`，且内容是**含 PII 的第三方原始响应**。
- **推荐操作**：**删除**（若将来要落库额外 claim，再显式加回并写明用途）。
- **风险等级**：**P1**（同时是**敏感数据面**：零消费却进入 record 的 toString）　**证据可信度**：高　**审批状态**：待确认

##### [BE-AUTH-11] 验证码开关已被移除，**四处残留**（含前端契约失效）

- **位置**：`auth/service/CaptchaService.java:54,91,100-102,113-118,267`；`auth/model/RegistrationRequest.java:12-15`；`frontend/src/types/auth.ts:22-31`；`frontend/src/pages/register/index.tsx:71,170`。
- **问题证据**：**与 §4 的 BE-VAL-12/BE-VAL-15 是同一处问题**（三路审计独立发现，结论一致），并**新增两条**：
  1. `CaptchaService:54` 的 `log` 字段在文件内 **`log.` 0 命中** → **死字段**，连带 `Logger`/`LoggerFactory` 导入；
  2. 前端 `register/index.tsx:170` 的条件渲染是**死分支**（`challenge?.enabled` 恒 `undefined`）。
- **推荐操作**：**简化**——删构造参数 / 死日志字段 / 过期 javadoc；前端删 `enabled` 字段与恒真条件。⚠️ **服务端出题 + 一次性消费 + 来源配额必须保留。**
- **风险等级**：**P1**（三处审计口径略有差异：本节判 P1，§4 判 P2——按更严的 P1 处理）　**证据可信度**：高　**审批状态**：待确认

##### [BE-AUTH-12] 用户名规则正则字面量 **6 处**重复（密码规则已集中，用户名没有）

- **位置**：后端 `RegistrationRequest:26`、`OnboardingAccountRequest:13`、`UserRequest:23`、`UserCreateRequest:21`；前端 `register/index.tsx:139`、`account/setup/index.tsx:152`。
- **问题证据**：同一 `^[A-Za-z0-9_.-]+$` 全仓 **6 命中**；对照密码规则已集中为唯一来源（`PasswordRules:35-43`，被 4 个 DTO 引用）。用户名规则**没有任何常量承载，也没有自动化一致性护栏**。
- **推荐操作**：**合并**（新建 `UsernameRules` 常量，比照 `PasswordRules`）。⚠️ **边界说明**：`OnboardingBindRequest.username:7-9` **刻意不套**该正则（它是历史用户名查询），**不要一起统一**。
- **风险等级**：**P1**　**证据可信度**：高　**审批状态**：待确认

##### [BE-AUTH-13] `ExternalLoginService` 与 `UiasLoginService` 整套 state / 事务 / 兜底重复（**7 组同构**）

- **位置**：`ExternalLoginService:200-220,351-361,469-482`；`UiasLoginService:80-86,138-145,148-157,173-181`。
- **问题证据**：`consume`、`safeReturnTo`、`transactionKey`、`token`、`encode`、`appendQuery`、`beginBinding` 的会话校验 **7 组同构**；失败兜底双 catch 也各写一份。两者 Redis key 前缀不同：`auth:external:transaction:` 与 `auth:uias:transaction:`。
- **依赖关系**：⚠️ **key 前缀是存储契约**（`LoginTransaction:11-12` 有同类说明：改字段名会让在途事务反序列化失败）。**合并时不得改前缀。**
- **推荐操作**：**合并**（共用登录事务存储/编排组件；`LoginTransaction` 已可复用）。
- **风险等级**：**P2**　**证据可信度**：高　**审批状态**：待确认

##### [BE-AUTH-14] `complete()` 两个 catch 重复，且审计主体把"已绑定身份的失败登录"误标为**开户**

- **位置**：`ExternalLoginService.java:200-220`；`auditSubject:287-289`；判定点 `ExternalLoginPersistence.java:79-80`。
- **问题证据**：两个 catch 结构相同（`loginLogs.record(auditSubject(...), FAILURE, ...)` + `if (isBinding) throw`），仅日志细节与文案不同。`auditSubject` 只能给出 `bind:{provider}` 或 `onboarding:{provider}`，而"非 bind"里**同时包含**开户确认与"已绑定会员的正常登录"——后者要看 `settle:79` 的 `linked == null` 才知道，**catch 时拿不到**。于是**已绑定用户**的外部登录失败会以 `onboarding:github` 写进 `sys_login_log`。
- **依赖关系**：登录日志是风险规则的数据源（`system/log/risk/rule/LoginFailureBurstRule` 等在同包内），错误的 username 会影响**按账号聚合的排障与统计**。
- **推荐操作**：**简化 + 待确认**（合并两个 catch；主体改为 `login:{provider}`，或把留痕移到能判定 linked 的位置）。
- **风险等级**：**P2**　**证据可信度**：中（"误标"由代码路径推导，**未在运行环境观测日志**）　**审批状态**：待确认

##### [BE-AUTH-15] UIAS 两个适配端口在仓库内**无任何实现** → 整条 UIAS 链路不可达

- **位置**：`auth/uias/UiasAssertionConsumer.java:12`、`EnterpriseDirectoryPort.java:6`；判定 `UiasLoginService:69-73`、`ExternalLoginService:101-105`。
- **问题证据**：两个类型名全仓 **13 命中，全部是定义/注入/使用点，0 个 `implements`**；`UiasLoginService.isUsable:71-72` 要求两个 `ObjectProvider` 都拿到实例 → **恒 false** → `providers()` 返回的 uias **恒 `enabled=false`** → 前端按钮恒为"暂未启用"，登录页的统一认证入口**已被注释掉**（`login/index.tsx:119-134`，见 §5 的 FE-PAGE-01）。
- **依赖关系**：`ExternalAuthProviderConfig:82-87` 仍注册 uias 定义；`isSaml:112-114` + `begin:139-141` **显式拒绝 SAML**，属**安全网**（不是死代码）。
- **推荐操作**：**待确认**（这是**有意的内网规划端口**，**不建议现在删**；建议明确 Owner/接入时间，并考虑在缺适配器时给出**可诊断**的日志或文档指针）。⚠️ **与 §5 的 FE-PAGE-01（登录页死代码）联动**：若确定不做 UIAS，则前后端一起清。
- **风险等级**：**P2**　**证据可信度**：高（"无实现"是事实）／"是否保留"是业务问题　**审批状态**：待确认

##### [BE-AUTH-16] 两个密码入口未使用既有的来源级配额；失败路径每请求写一条登录日志（**决策项**）

- **位置**：`LoginAttemptGuard.requireSourceQuota:122-129`（调用点仅 `CaptchaService:106`、`ExternalLoginService:121`、`UiasLoginService:76`）；`AuthService:44-63`、`RegistrationService:93-128` **未调用**；`LoginLogService:43-44,46-61`。
- **问题证据**：`/api/auth/login` 目前**只有"失败计数 + 锁定"，没有请求级上限**。失败路径每次请求产生 2 次 Redis `INCR` + 1 次 `REQUIRES_NEW` 的 DB 插入；**锁定期内每次请求仍各写一条 LOCKED 日志**。`LoginLogService:43-44` 的 javadoc **自己承认**"攻击者持续用错误密码刷接口会推高这张表的增长速度"。
- **依赖关系**：⚠️ `requireSourceQuota` 用的 `currentSource()` 来自 `RequestInfo.rateLimitIp`，**只在可信代理下采信转发头**，共享出口 NAT 会误伤。
- **推荐操作**：**待确认（这是加强而非削弱）**——是否给两个密码入口加"每来源每分钟 N 次"上限、阈值多少、是否接受办公网共享出口的误伤。**需要你决定。**
- **风险等级**：P3　**证据可信度**：高（事实）／决策待定　**审批状态**：待确认

##### [BE-AUTH-17] 数据范围枚举：`@Pattern` 与 Java `Set` 两套字面量

- **位置**：`system/role/request/RoleRequest.java:22-24` 与 `security/context/AccessPolicy.java:232-237`；调用点 `RoleService:76,97`。
- **问题证据**：同一 5 值集合在两个文件里各写一遍字面量；两个调用点传入的都是 `@Valid` 校验过的请求体，因此 `AccessPolicy:233` 的"配置无效"分支在现有调用链上**不可达**（`requireScope` 全仓仅 2 个调用点）。⚠️ **第二段 `:235-236`（不能设置超过自身的数据范围）是真实授权边界，必须保留。**
- **推荐操作**：**简化**（把 5 值集合抽为常量，让注解与 Java 判定共用单一来源）。**与 §4 的 BE-VAL-02 是同一处问题。**
- **补充结论（重要，针对"自定义校验器是否必要"）**：**全后端没有任何 `@Constraint` / `ConstraintValidator` / 自定义校验注解**（grep = **0 命中**；唯一 `@interface` 是 `OperationLog`，属审计切面）。因此**不存在**"自定义校验器与 Bean Validation 重复"的问题；唯一的重复形式就是本条这种"注解字面量 + Java 常量集合"的双份枚举。
- **风险等级**：**P1**　**证据可信度**：高　**审批状态**：待确认

##### [BE-AUTH-18] `SessionService.deserialize` 的旧竖线格式兼容分支在仓库内**无写入方**

- **位置**：`auth/session/SessionService.java:187-199`（`split("\\|", -1)` 且要求 `parts.length == 4`）。
- **问题证据**：唯一写入点是 `serialize:179-185`（Jackson JSON），`deserialize:189` 也以 `startsWith("{")` 优先走 JSON；**竖线分支在仓库内没有任何写入方**，其字段顺序与当前 `LoginSession` 结构**已不同**。是否还有存活 key 取决于 Redis 历史数据：`rememberedAbsoluteDays` 默认 14 天、开户态绝对 2 小时。
- **推荐操作**：**待确认**（若确认所有实例升级超过最长绝对期限（14 天）且 Redis 无历史 key，可删；否则保留一轮）。
- **影响面**：升级期会话兼容；删除后旧格式 key 会被当成"会话不存在"（用户重新登录）。
- **风险等级**：**P2**　**证据可信度**：中（"无写入方"是事实；"是否还有旧 key"**无法静态判定**）　**审批状态**：待确认

##### [BE-AUTH-19] javadoc 引用了**不存在的测试与文档**

- **位置**：`auth/service/PasswordRules.java:20`、`auth/service/PasswordChangePolicy.java:70`。
- **问题证据**：`grep "PasswordRulesTest|ARCHIVE-removed-code|appliesToPasswordLogin"` 全仓 **3 命中，全部是这两处引用本身**；`backend/src/test` 只有 2 个测试文件；`docs/` 下无 `ARCHIVE-removed-code.md`（`docs/**/*.md` 共 7 个文件）。因此"密码规则由常量拼出"这条一致性**目前没有任何自动化护栏**，而前端 `utils/passwordPolicy.ts:4-19` 明确要求**手工逐字同步**（见 §4 的 BE-VAL-19）。
- **推荐操作**：**简化**（补一个 `PasswordRules` 边界测试 + 前后端正则一致性测试，或把引用改为准确描述）。
- **风险等级**：P3　**证据可信度**：高　**审批状态**：待确认

#### 2.5.3 该模块的「必须保留的安全边界」（不得以"重复/内网"为由删除）

1. **JWT 签名与令牌级一致性校验**：`JwtService.parse:41-43`（HMAC 验签、过期）+ `JwtAuthenticationFilter:61-62` 的 `subject`/`authVersion` 与 Redis 快照的双向一致性——防伪造、防旧令牌重放的核心，**不因**"会话已校验"而可删。
2. **会话双轨有效期**：`SessionService:51,70-71,83,96-103`（空闲 TTL + `absoluteKey` 绝对上限）与 `SessionLifetimePolicy:28-35`（`touch` 只续空闲、**绝不续绝对期限**）。
3. **刷新 Cookie 属性**：`RefreshCookieFactory:33-41`（`HttpOnly` + `Secure` + `SameSite=Lax` + `path=/api/auth`）。⚠️ **`path` 不能放宽到 `/`**——那会把刷新凭据发给每个业务请求（`AccountSessionController:52-56` 的注释正是为守住这条）。
4. **401 / 403 分流契约**：`WebSecurityConfig:60,65,67-73` 与 `GlobalExceptionHandler:76-81,104-109`。特别是 `/api/auth/onboarding/**` 那条 `authenticated()`（`WebSecurityConfig:51-59` 记录了删掉后**"页面卡死"的真实故障**）与"待改密用户必须能调改密接口"的**排除式**表达式。
5. **双层失败锁定 + 来源哈希化**：`LoginAttemptGuard:38-47,76-112,137-145`（账号级严、来源级宽以对抗密码喷洒；Redis key **只存 SHA-256 而非明文 IP**）。
6. **可信代理判定**：`TrustedProxyResolver:76-91,129-148,214-228`（只信可信代理的转发头、从右往左取第一个不可信地址、IPv4-mapped 还原、CIDR 字节前缀匹配）——审计 IP 与限流 key 的**信任根**。
7. **密码规则只用于"设置新密码"**：`PasswordRules:22-27` 与 `PasswordChangeRequest:8-20`、`OnboardingBindRequest:7-12`（**历史密码/原密码不套新规则**，否则老用户立刻登不进去）。
8. **服务端验证码与一次性消费**：`CaptchaService:103-132`（服务端出题、`getAndDelete` **无论对错即失效**、2 分钟 TTL）+ `:231-259` 的字体探测（无字体时**拒绝启动**，避免"永远猜不中且不报错"）——其 `catch (Throwable)` 是**必要**的（`AWTError` 属 `Error`）。
9. **登录日志的 `REQUIRES_NEW` + 吞异常**：`LoginLogService:34-60`——"失败记录不被业务事务回滚"与"审计写入失败不能导致用户登不进来"两条取舍。
10. **`PasswordChangePolicy` 不含 `authMethod` 判断**：`PasswordChangePolicy:50-70`——文档已明确"**不要把这里的 authMethod 判断当成 bug 去掉**"。
11. **改密/重置/停用即失效所有会话**：`UserPasswordService:58-60,80-82`、`UserService:204-206`（`auth_version+1` + `sessions.removeAll` + `realtime.closeSession`）。
12. **解绑前的"必须保留一种可用登录方式"**：`AccountIdentityController:88-99`（含 `selectByIdForUpdate:83` 的并发保护与 `deleteBinding` 影响行数判定）。
13. **DB 唯一索引作为最终边界**：`init_schema.sql:28,45,70-71` + `GlobalExceptionHandler:119-124` 的 `DuplicateKeyException` → 400。⚠️ Java 侧前置校验**不得**以"有 DB 约束"为由删除（否则用户看到 500/400 通用文案）。
14. **`RequestInfo.clientIp` 的 resolver==null 兜底**：`RequestInfo:34,54-55`——静态工具类折中，未初始化时**安全退化**为只看 TCP 源地址（对单测/非 Web 上下文必要）。
15. **`ExternalLoginPersistence` 刻意不持有 HttpClient + 成功日志在事务外写**：`ExternalLoginPersistence:21-35`、`ExternalLoginService:221-225`（避免事务中网络 IO 与"一个请求占两条连接"）。
16. **`ExternalLoginService.begin:139-141` 的 SAML 拒绝**：即使有人把 provider 改成 SAML 并 enable，也不会走进未实现的路径——**是安全网，不是死代码**。

#### 2.5.4 该模块「已核查但判定不是问题」（避免后续会话误判）

| 项 | 结论 |
| --- | --- |
| `SecurityProperties` 9 个配置项 | **全部有使用点**（逐个已核），无未使用配置项 |
| `ExternalAuthProperties`（含 26 个 Provider 字段） | 逐个核对 getter **均有使用点**，无未使用配置项 |
| 前端 `api/auth.ts`（10 个函数）与 `api/account.ts`（8 个函数） | **全部有调用方** |
| 后端 auth 链路 19 个 HTTP 端点 | **全部有前端调用方**（逐条对照），**无孤立端点** |
| 自定义校验注解/校验器 | **0 命中**——不存在与 Bean Validation 重复的自定义校验器 |
| `LoginLogService` 的 catch-all | 刻意设计（审计写入失败不能让用户登不进来）→ 保留 |
| `ExternalLoginPersistence.bind` 与 `RegistrationService.bind` | 重复（BE-AUTH-05），但 DB 唯一索引是最终边界 → **合并而非删除** |
| `ExternalIdentityEntity.lastLoginTime` | 只写不读，但属**运维数据** → 保留 |
| `LoginSession.passwordChangeRequired` 走会话快照 | 刻意（避免用户操作途中突然掉权限 + 避免每请求读库）→ 保留 |
| `AuthController.refresh/touch/me` 未加 `@PreAuthorize` | 依赖刷新 Cookie 而非 Bearer，**是设计而非缺口** |
| `WebSecurityConfig` 的 CORS 空来源默认值 | 空 = 不放行任何跨域，同源部署下正确 → 保留 |

#### 2.5.5 该模块的「未检查范围」

`system/log/risk/rule/*`（4 个风险规则如何消费登录日志——仅据包名推断数据来源）、`system/log/retention/*`；`realtime` 包完整实现；`agent`/`approval`/`intranet`/`notification` 全部业务实现（仅在统计 `access.actor()` 调用点、`@PreAuthorize`、`menus.selectByUserId` 时被命中）；`db/approval/V1~V4`、`db/intranet/V1~V15`；前端其余页面/组件/样式、`SystemSettings`、`Permission` 组件实现。
**运行时验证全部未执行**：未启动应用、未跑测试、未连 Redis/MySQL、未发请求。所有"运行时影响"均为**静态推导**；BE-SEC-07、BE-AUTH-14、BE-AUTH-18 的可信度已标注为"中"。

### 2.6 其他模块

> **无剩余未并入的模块。**
> 本报告覆盖了 `backend/src/main/java/org/lbl/` 全部 9 个顶层包（agent / approval / auth / common / config /
> intranet / notification / realtime / security / system）与 `frontend/src/` 全部 131 个 `.ts/.tsx` + 34 个 `.css`。
> 各模块的「已检查范围」与「未检查范围」分别记录在 §2.1.9、§2.2.5、§2.3.4、§2.5.5、§5.2.6、§6.6、§7.3。

---

## 3. 过度设计清单

> 本节是**跨模块汇总视图**：只列「抽象层数 > 实现数」「为假设的未来预留」「只写不读的死结构」三类，
> 每条都指向 §2/§4/§5/§7 中的详细条目，**不重复**已有证据。

### 3.1 抽象层数 > 实现数（只有 0~1 个实现的接口/注册表）

| 编号 | 结构 | 实现数 / 调用点 | 是否存在明确的近期用例 | 详见 |
| --- | --- | --- | --- | --- |
| BE-AGT-01 | `AgentRunObserver` 接口 + `AgentRunner` 的 4 个 `notify*` + `safely()` | **0 实现**，10 个调用点**全部空转** | ❌ 无 | §2.3 |
| BE-AGT-02 | `AgentProfileRegistry`（Map + 注册 + 校验） | 只注册 1 个 profile；`find()` **0 调用**；多 profile 分支不可达 | ❌ 无（`AgentChatRequest` 无 `profileId`） | §2.3 |
| FE-CMP-05 / BE-LOG-14 | `ArtifactRendererRegistry` 插件注册表 | **只注册 1 个渲染器**；注销闭包 0 调用；re-export 0 消费者 | ❌ 无 | §2.3 / §5 |
| — | `ModelGateway` 端口 | **1 实现** | ✅ **有**（为换内网网关预留，`AgentProperties.baseUrl` 就是为此） | **保留** |
| — | `AgentTool` 扩展点 | **4 实现** | ✅ **有** | **保留** |
| — | `ExcelExportUtil.RowProducer/RowWriter` | 各 1 实现 | ✅ **有**（分批取数需要；JDK 函数式接口**无法抛 `IOException`**） | **保留** |
| — | `UiasAssertionConsumer` / `EnterpriseDirectoryPort` | **0 实现** | ⚠️ **需业务确认**（`application-local.yml:63` 明确"实现只在内网部署时提供"） | BE-AUTH-15 |
| — | 本范围内 14 个 MyBatis Mapper `interface` | 各 1 实现 | ✅ 框架要求 | **保留** |

### 3.2 只写不读的死结构（本报告**最集中**的一类，共 20+ 处）

| 类别 | 条目 | 位置要点 |
| --- | --- | --- |
| 前端类型死字段 | **10 个**：`AgentMetric.format/status`、`AgentFinding.code`、`AgentReport.metadata`、`AgentStreamEvent.runId`、`AgentReportSection.type` 的 `'chart'`、`CurrentUser.superAdmin`、`DepartmentChangeDetail.finishedTime/canCancel`、`MenuRoute.routeName` | `types/agent.ts`、`types/auth.ts`、`api/departmentChange.ts`（FE-INF-06） |
| 后端投影死字段 | `LoginRiskEvent.userAgent`（**SQL 仍在 `SELECT` 它**）、`AuditRiskReport.returnedFindingCount` | BE-LOG-05 |
| 前端类型死导出 | **19 个**符号"导出但仅本文件内使用"，其中 `SmartTableSelection` 全仓 **1 命中** | FE-INF-04 |
| 后端死字段 | `ToolRisk`（`.risk()` **0 读取**）、`ToolResult.of()/output()` 0 调用、`ModelResponse.usage/finishReason` 0 读取、`AgentPageContext.menuId` 0 读取、`AgentMessage.Role.SYSTEM` 0 构造、`CurrentUser.deptId` 0 读取、`SessionGrant.onboarding` 0 读取、`VerifiedIdentity.attributes` 0 读取 | §2.1 / §2.3 / §2.5 |
| 后端死方法 | `AuditRiskAnalysisService.scanLoginRisks/scanOperationRisks`、`AccessPolicy.requiresPlatformTransferReview`（⚠️ **可能是缺失的安全闸门，需业务确认**）、`DepartmentTransferApprovalService.notifyCurrentReviewer`（**方法体只有注释**）、`AccessPolicy.isSuperAdmin`（public 但仅类内）、`TrustedProxyResolver.hasTrustedProxies()`（描述的外部调用方不存在） | §2.1 / §2.2 / §2.3 / §2.5 |
| 死配置项 | `logging.level.org.kariya`（**包不存在**）、`AgentProperties` 11 项中 **8 项** yml 零出现、`LogRetentionProperties` **全部 6 项** yml 零出现、`lbl.log.retention` 全仓不存在 | CFG-05、BE-AGT-06、BE-LOG-06 |
| 死 API / 死规则 | `assignRequirementTransaction`（前端 **0 调用**）、4 个 `WindowRiskPolicy.enabled` **死守卫**、`AuditRiskSeverity.CRITICAL/LOW` 永不赋值、`AccessPolicy:233` 枚举分支不可达 | FE-INF-02、BE-LOG-03/04、BE-VAL-02 |
| 死样式 | `.base-chart`（**当前**死规则）、`.login-unified-button` 4 条、`loadingScreen.css` 的容器级规则（**选择器不匹配，全部失效**）、两个日志 `index.css` 的 5 个类 | §6 |
| 死日志字段 | `CaptchaService:54` 的 `log` 字段（文件内 `log.` **0 命中**，连带 2 个 import） | BE-AUTH-11 |

### 3.3 为「假设的未来需求」预留 / 不可达分支

| 编号 | 内容 | 判定 |
| --- | --- | --- |
| BE-AGT-03 | `ApprovalPolicy.REQUIRED` 分支**不可达**（`AgentRunner:69-74` 已过滤），但 `visibleTools` **未过滤**它 → 一旦新增写工具会出现"审批未就绪 → 模型被静默喂错 → **用户看不到任何提示**"的循环 | **保留枚举，但要修守卫行为**（P3；新增写工具即升 P1） |
| FE-CMP-03 | `BaseChart` 6 个 + `G6Graph` 1 个 + `IconPicker` 2 个 = **9 个从未被传入的 prop** 及其不可达分支 | 与演示页去留绑定 |
| BE-LOG-03/04 | 4 处 `enabled` **死守卫** + 2 个永不赋值的枚举值 | 保留枚举、删死守卫 |
| BE-NTF-04 | `NotificationService.afterCommit` 的非事务兜底分支**当前零调用者**（全部调用方都是 `@Transactional`） | **建议保留**（低成本保险，删除收益极低） |
| BE-CMN-06 | `MaxUploadSizeExceededException` + yml multipart 限制在仓库内**无消费路径**（后端 `MultipartFile` 0 命中、前端 `FormData` 0 命中） | ⚠️ **很可能是为 D-032 的 Excel 导入保留的** → 保留 |
| BE-AUTH-18 | `SessionService.deserialize` 的旧竖线格式分支**无写入方** | 需确认 Redis 无历史 key（绝对期限 14 天） |
| BE-CMN-08 | `TextValues` 单方法工具类 | **审计结论：保留**（替换会把口径散回 10 个调用点） |
| BE-AGT-10 | `AgentCommand.profileId` 恒为 `null`；`AgentProperties.strictToolSchema/chatPath` 从未配置 | 见 BE-AGT-02 / BE-AGT-06 |
| BE-CMN-03 | `FIELD_LABELS` 27 条映射只在"注解未写 message"时兜底 | **建议保留**（权衡项，非冗余） |
| BE-AUTH-15 / FE-PAGE-03 | UIAS 全链路不可达（0 实现）+ 登录页入口已注释 | **需业务确认**（有意规划 vs 废弃） |

### 3.4 重复实现汇总（同一规则被独立实现 N 次）

| 重复内容 | 次数 | 条目 |
| --- | --- | --- |
| `message.error(getApiErrorMessage(...))` 错误提示样板 | **82 次 / 30 文件** | FE-PAGE-12 |
| SpEL `isAuthenticated() and !hasAuthority('onboarding:access')` | **8 份**（逐字相同） | BE-SEC-04 |
| 「凭据是否可用」判定（且有**两种写法**） | **7 处** | BE-AUTH-02 |
| 分页参数守卫（文案与边界逐字相同） | **7 处** | BE-VAL-03 / BE-SYS-01 |
| 前端需求类型/状态映射 | **4~8 份** | BE-REQ-03、§5.2 状态字典矩阵 |
| 「会话是否仍有效」判定 | **4 处** | BE-AUTH-03 |
| 部门变更 Steps 状态映射 | **3 份** | BE-APR-09 / BE-NTF-06 / FE-PAGE-05 |
| 「删除后回退页码」（**语义已分叉**） | **3 份** | FE-PAGE-10 |
| `toTree` 树构建 + 按 keyword 剪枝 | **5 个实现** + 3 份剪枝 | §5.2 矩阵 |
| 分页契约适配 `{page,pageSize}` → `{pageNum,pageSize}` | **5 份** | FE-PAGE-11 |
| `?.replace('T',' ')` 时间格式化（项目已装 dayjs 却基本没用） | **16 处 / 13 文件** | FE-PAGE-13 |
| 分页 margin CSS `margin:16px 0 0 !important` | **6 个文件**（产物中同在字节偏移 2541） | FE-CSS-03 |
| 用户名正则 `^[A-Za-z0-9_.-]+$` | **6 处**（4 后端 + 2 前端） | BE-AUTH-12 / BE-VAL-19 |
| JSON Schema 与 Bean Validation 双写 | **6 组** + 1 组只有 Schema 侧，**零测试断言** | BE-LOG-13 |
| `business_type` / 通知 type 字符串字面量 | 前端 + 后端共 **20+ 处**，无统一常量 | BE-NTF-03 |
| 内置菜单/权限种子 | **2 份**（`init_data.sql` ↔ `SystemPermissionInitializer`），18 权限码 **100% 重叠** | BE-SYS-03 |
| 「密码校验 + 计数 + 日志」编排 | **2 份**（逐行同构） | BE-AUTH-01 |
| 「外部身份绑定前置校验」（**语义不一致**） | **2 份** | BE-AUTH-05 |
| 「登录失败计数」实现 | **2 套** | BE-AUTH-04 / BE-VAL-13 |
| 外部登录 state/事务/兜底编排 | **7 组同构** | BE-AUTH-13 |
| 弹窗骨架 + 提交锁 | **5 套骨架 / 9 个锁**（**项目里已有 `useDialogLoad` 却没人用**） | FE-PAGE-06 |
| `pages/**` 下无 `export default` 的组件型 tsx | **16 个**（会进入菜单候选） | CFG-09 / FE-CMP-01 |
| 前后端重复校验（F + B + S + D 四层） | 抽样 8 类业务，详见 §4.2 矩阵 | BE-VAL-01~09 |
| `normalizeIssuer` / 两个日志页 CSS / 两个字段页 CSS | 各 2 份（**逐字相同**） | BE-AUTH-06、FE-CSS-04/05 |

---

## 4. 安全与校验复杂度清单

> 本节由只读子审计会话产出后并入。**这是本报告中最需要谨慎对待的一节**：
> 它同时包含"可以删的重复防护"和"必须保留的安全边界"，二者必须严格区分。
> 子审计**未运行构建、未连接数据库、未在浏览器验证**，所有"分支恒为 false"的结论均为静态推断。

### 4.1 检索基线（方法可复现）

| 检索式（`backend/src/main/java`） | 命中数 |
| --- | --- |
| Bean Validation 注解（`@NotBlank`/`@NotNull`/`@Size`/`@Pattern`/`@Min`/`@Max`/`@Valid`/`@Email`/`@Positive` 等） | **251** |
| `throw new BusinessException` / `IllegalArgumentException` / `IllegalStateException` / `catch (` | **344**（其中 `BusinessException` 128 处） |
| `@RequestBody` | **39**（**36** 带 `@Valid`，3 处不带 → BE-VAL-17/18） |
| `@Constraint` / `ConstraintValidator` / `implements ConstraintValidator` | **0** |
| 自定义注解 `@interface` | **1**（`OperationLog`，与校验无关） |
| `synchronized` / `ReentrantLock` / `RedisLock` / `@Version` / `FOR UPDATE` / `Idempot` / `Redisson` 等 | **13**（逐个见 4.3） |
| `FOREIGN KEY` / `REFERENCES` / `CHECK (`（`db/**/*.sql`） | **0** |
| `captcha-enabled` / `captchaEnabled`（含 yml/ts/tsx） | **1**（**仅 Javadoc 注释** → BE-VAL-12） |
| `分页参数无效，每页最多 100 条` | **7** |

**结论一**：本仓库**不存在**自定义枚举校验器/自定义约束注解/自定义 `ConstraintValidator`（检索 0 命中）。因此"自定义校验器重复造轮子"这一类问题在本项目**不成立**。

**结论二（重要）**：**数据库层是校验最薄的一层**——全仓库 `CHECK`/`FOREIGN KEY` 命中 0；`sys_menu.menu_type`、`sys_role.data_scope`、`intranet_es_field.data_type` 的枚举合法性**只由 Java 层承担**。D-027 之后，ES `data_type` 的枚举约束**仅剩前端下拉**一处。这与"内网无恶意写入"的前提一致；但 **D-032 已预确认要实施 Excel 导入**，届时该前提失效——D-027 的"遗留"条款已对此设条件。

### 4.2 字段校验层次矩阵（抽样 8 类业务，列出**重复层数 ≥ 4** 的项）

图例：**F** 前端、**B** Bean Validation、**S** Service 手写预检、**D** 数据库约束。

| 字段 / 规则 | F | B | S | D | 层数 | 判断 |
| --- | --- | --- | --- | --- | --- | --- |
| `username` 必填 | ✅ | ✅ | ✅ `UserService:238` | ✅ `NOT NULL` | **4** | S 冗余（BE-VAL-04） |
| `username` 唯一（含软删） | ✅ 实时提示 | ➖ | ✅ `UserService:239-244` | ✅ `UNIQUE` | **3** | **必须保留**（BE-VAL-14） |
| `status` 0/1（用户/部门/角色） | ✅ | ✅ | ✅ `UserService:278`、`DeptService:194`、`RoleService:98,245` | ✅ | **4~5** | **S 完全冗余**（BE-VAL-01） |
| `dataScope` 枚举 | ✅ | ✅ `@Pattern` | ✅ `AccessPolicy:233` | ➖ | **3** | 枚举分支冗余；**授权判断必须保留**（BE-VAL-02） |
| `menuType` 枚举 | ✅ | ✅ `@Pattern` | ✅ `MenuService:102` | ➖ | **3** | **S 完全冗余**（BE-VAL-05） |
| `roleCode` / `deptCode` / `permissionCode` / `routePath` 唯一 | ✅ | ✅ | ✅ 含软删口径 | ✅ `UNIQUE` | **4** | **必须保留** |
| `transactionCode` 唯一 | ✅ | ✅ | ✅ `TransactionAssetService:54` | ✅ `UNIQUE` | **4** | **必须保留** |
| `marketName + tableName` 唯一 | ✅ | ✅ | ✅ `HbaseAssetService:55` | ✅ `UNIQUE` | **5** | **必须保留** |
| `reason`（驳回原因）非空 | ✅ | ✅ `@NotBlank` | ✅ `IntranetRequirementService:100` | ✅ | **4** | S 不可达（BE-VAL-04） |
| 数据资源 ID 合法性 | ✅ 多选 | ✅ `@NotNull` | ✅ `TransactionDataResourceService:83-85` | ✅ `UNIQUE` | **4** | 去重属 D-022 已决策；`id<=0` 冗余（BE-VAL-06） |
| 需求明细三表**全部字段** | ⚠️ 部分无约束 | ❌ **无 DTO、无 `@Valid`** | ✅ 仅状态守卫 | ✅ 列宽 | **1~2** | **校验强度缺失**（BE-VAL-18） |
| HBase/ES 字段 `sortOrder` | ⚠️ 无 `min` | ✅ 仅 `@NotNull` | ➖ | `INT NOT NULL` | **1** | **缺 `@Min(0)`**（BE-VAL-08） |

**矩阵小结**：绝大多数字段是「F + B + D」三层（正常纵深，不建议动）；真正多余的第四层集中在
**Service 对"DTO 已同规则同文案覆盖"的重复判断**，以及**同一横切规则的多次实现**（分页守卫 ×7）。

### 4.3 防重复 / 幂等 / 锁机制全清单

| # | 机制 | 位置 | 真实触发场景 | 判定 |
| --- | --- | --- | --- | --- |
| 1 | 账号+来源失败计数与锁定（5 次/15 分钟） | `LoginAttemptGuard.java:39,43,45,86-100` | `/api/auth/login` **匿名可达**（`WebSecurityConfig:65`） | **必须保留** |
| 2 | 来源级失败锁定（20 次/15 分钟） | `LoginAttemptGuard.java:41,88,95-98` | 防密码喷洒 | **必须保留** |
| 3 | 来源级固定窗口配额 | `LoginAttemptGuard.java:122-129`，仅 3 个调用点（验证码出题、UIAS 启动、外部登录启动） | 未认证且每次产生副作用的入口 | **必须保留** |
| 4 | 验证码（一次性 `getAndDelete` + 2 分钟 TTL） | `CaptchaService.java:56,83-84,103-132` | `/api/auth/register` **匿名可达** | **必须保留** |
| 5 | 改密"原密码"失败计数 | `UserPasswordService.java:26-27,67-68,92-95` | 与 #1 各写一套 | 必要，实现重复（BE-VAL-13） |
| 6 | 服务端会话（Redis 滑动 TTL + 绝对上限 + `auth_version`） | `SessionService.java:44-55,75-89,92-109` | 改密后全端下线 | **必须保留** |
| 7 | 会话标识 = 48 字节 `SecureRandom` + URL-safe Base64 | `SessionService.java:206-210` | 抗猜测 | **必须保留** |
| 8 | Refresh Cookie | `RefreshCookieFactory.java:25` | — | **必须保留** |
| 9 | 审批实例 `SELECT ... FOR UPDATE` | `ApprovalInstanceMapper:15-16`；`ApprovalWorkflowService:82-86` | 并发审批/重提/撤销 | **必须保留**（符合 D-034） |
| 10 | 用户行 `SELECT ... FOR UPDATE` | `UserMapper.java:24-26`（**未提交改动**） | 防并发解绑同时通过"仍有另一种登录方式"检查 | **必须保留**（D-034 允许的 `FOR UPDATE` 家族） |
| 11 | 前端提交闸门 `useRef` + `confirmLoading` | 6 处（`UserDialog:43`、`RoleDialog:31`、`DeptDialog:40`、`MenuDialog:37`、`TransactionDialog:22`、`RequirementDialog:18`） | 防双击重复提交 | **必须保留**；但**另有 4 处缺失**（BE-VAL-22） |
| 12 | 幂等 Token / 请求去重键 | **全仓库 0 命中** | — | **不存在**（也无需新增） |
| 13 | Redis 分布式锁 / Redisson | **0 命中** | — | **不存在**，符合 D-034 |
| 14 | `synchronized` | **1 处**：`OpenAiCompatibleModelGateway.java:100` | 懒加载单例 | 与本主题无关 |
| 15 | `ReentrantLock` | **0 命中** | — | 不存在 |
| 16 | 乐观锁 `@Version` | **0 命中**（`sys_approval_instance` 明确不设 `version`） | — | **不存在，符合 D-034** |
| 17 | `AtomicBoolean` | 3 处（SSE 完成标志、订阅标志、取消令牌） | 生命周期 | 与本主题无关 |
| 18 | `ConcurrentHashMap` | `RealtimeGateway:20,32` | WebSocket 会话注册表 | 与本主题无关 |
| 19 | 数据库唯一约束作为并发兜底 | 多处（`V1:25`、`V7`、`V8`、`init_schema.sql` 等） | 并发/绕过应用写库 | **必须保留**（`GlobalExceptionHandler:119-124` 把 `DuplicateKeyException` 转 400） |

**D-034 违反性检查结论：未发现任何违反。** 全仓库没有 Redis 分布式锁、没有 JVM 锁保护业务写路径、没有新增乐观锁字段；锁一律为事务内 `SELECT ... FOR UPDATE`，仅用于审批实例与用户行。

**明确"未发现"（以免后续会话误以为遗漏）**：幂等 Token、防重放 nonce、请求签名、乐观锁重试、`synchronized` 保护的业务写入、`FOR UPDATE` 保护的需求明细写入（D-037 已接受）。

### 4.4 ① 可以删除的重复防护（5 项，全部 P3，合计约 10 行）

| 编号 | 内容 | 位置 |
| --- | --- | --- |
| BE-VAL-01 | Service 重复实现 DTO 已保证的 `status` 0/1 取值校验（4 处） | `UserService:278`、`DeptService:194`、`RoleService:98,245` |
| BE-VAL-02 | `dataScope` 枚举合法性分支（`AccessPolicy:233-234`），DTO 已用同一清单同义覆盖 | `AccessPolicy.java:232-234` |
| BE-VAL-04 | "非空/非空串"与 `@NotBlank` 重复（驳回原因、用户名） | `IntranetRequirementService:100`、`UserService:238` |
| BE-VAL-05 | `menuType` 枚举在 `@Pattern` 与 Service `TYPES` 集合两处 | `MenuService.java:102` |
| BE-VAL-06 | 数据资源 `id == null \|\| id <= 0` 抛错（DTO/DB 已覆盖；`LinkedHashSet` 去重属 D-022 已决策，**保留**） | `TransactionDataResourceService.java:82-85` |

> ⚠️ 这 5 项的共同特征：**DTO 用同一规则、同一文案已覆盖，Service 分支恒为 false**。
> **没有一项涉及鉴权、授权、唯一性口径、事务或数据完整性。**

### 4.5 ② 可用更简单方式实现的必要防护（7 项）

| 编号 | 内容 | 建议 | 风险 |
| --- | --- | --- | --- |
| BE-VAL-03 | 分页守卫在 **7 个 Service** 中逐字重复（阈值与文案完全一致） | 合并为共享工具 | P3；若动基线 Service 需按 `AGENTS.md` §2 报批 |
| BE-VAL-07 | 需求明细"可编辑状态"规则：1 处内联 + 6 个调用点 + 1 个助手，其中 `:65` 与 `:150` **逐字相同** | 把 `:65` 内联改为调用 `requireEditableDetail` | P3；行为不变 |
| BE-VAL-09 | 开发者身份校验两段，第二段被第一段蕴含（`hasActiveRoleInDepartment` 的 SQL 已含 `u.status=1`） | 保留 `selectById`（取 `realName` 需要），删其 `null/status` 抛错分支 | P3 |
| BE-VAL-13 | 改密失败计数与登录失败计数是两套并行实现（数值与逻辑等价，维度不同） | 可合并为 `LoginAttemptGuard` 的一个"仅账号维度"入口；不合并也可接受 | P3 |
| BE-VAL-16 | `GlobalExceptionHandler#declaredMessage` 的反射取文案逻辑（~52 行）有更简单的等价实现（自定义 `MessageInterpolator` / `ValidationMessages.properties`） | **不建议为"简化"而改**——会改变"逐注解手写文案优先"的现有契约，当前实现有明确论证且失败方向安全 | P3，可信度中 |
| BE-VAL-19 | 前后端校验规则以**手抄字面量副本**方式重复（密码正则、用户名/手机号/部门编码/角色标识/权限标识正则），且 `PasswordRulesTest` **已不存在** → **无任何机制保证一致** | 保留前端校验；如需降低漂移，可加**构建期断言脚本**比对两份正则，不动运行时 | P2 |
| BE-VAL-22 | 4 个写弹窗缺少另外 6 处都有的同步提交闸门（`DataResourceDialog`、`NewTransactionDetailDialog`、`TransactionCallDetailDialog`、`TransactionChangeDetailDialog`） | 统一补齐 | P3；服务端有事务 + 唯一键兜底，不构成数据损坏 |

### 4.6 ③ 必须保留的安全边界（明确反对删除或削弱）

| 编号 | 内容 | 为什么不能动 |
| --- | --- | --- |
| BE-VAL-10 | **反账号枚举**：对外统一「用户名或密码错误」，内部差异化原因只进 `sys_login_log` | 差异化原因若对外暴露即可枚举账号 |
| BE-VAL-11 | 登录限流 + 来源口径依赖 `trusted-proxies` | ⚠️ **部署风险**：`application-local.yml` 默认为空 ⇒ 反向代理后所有用户共享网关地址，"来源级"锁定会**互相误伤**。代码注释已写明"部署在 Nginx 后面时必须填写"→ 本条为**部署项**，代码零改动 |
| BE-VAL-14 | 唯一性校验的"含软删"口径（`countIncludingDeleted*`） | 逻辑删除只置 `deleted=1`，**物理唯一索引项仍在**，因此不能用"DTO/前端已校验"替代 |
| BE-VAL-20 | **日志脱敏（加强方向）**：`GlobalExceptionHandler:139` 把**整个绑定结果**交给日志框架，而请求体含 `password`/`oldPassword`/`newPassword`；`:122` 的 `DuplicateKeyException` message **包含 SQL 与列名** | 方向与"精简"相反——要求**加强**而非简化 |
| BE-VAL-21 | **操作日志脱敏（加强方向）**：`OperationLogAspect:46-48` 把 `ex.getMessage()` 存进 `sys_operation_log.error_message`（`VARCHAR(1000)`），可能含表名/列名/SQL 片段；仅截断不脱敏 | 同上；`ARCHITECTURE.md` §8 已把"内网字段脱敏与审计要求"标为**待确认** |
| BE-VAL-23 | 端点 `@PreAuthorize` + Service 业务授权**两层**（`IntranetRequirementController:32-37` vs `IntranetRequirementService:131-132,149`） | 两者职责不同：权限码无法表达"候选组+角色+部门""不能自审""仅本人可改"。去掉任一层都会越权 |
| BE-VAL-24 | 需求类型创建后不可改（前端 `disabled` + 后端 `:77` 强制） | 前端 `disabled` 只防 UI，直接调接口仍可提交 → 后端判断是**唯一强制点**（D-037 已确认） |
| BE-VAL-25 | 交易列表两个查询框不接后端（**D-026 已确认**） | 用户已决策，**不再报为问题** |

### 4.7 ④ 需要业务确认的问题（校验与安全侧）

| 编号 | 问题 | 需要你回答什么 | 风险 |
| --- | --- | --- | --- |
| BE-VAL-12 | **"可关闭验证码"开关在代码与配置中都不存在**：`RegistrationRequest`/`CaptchaService` 的 Javadoc 声明 `lbl.security.captcha-enabled=false`，但 `SecurityProperties` **无该字段**；`CaptchaService.verify` **无条件校验**，`Challenge` record **不含 `enabled`**。`CaptchaService:91` 注入了 `SecurityProperties` 却**从未读取** | **是否要保留"验证码可关闭"这一产品能力？** 要 → 实现真实开关；不要 → 修正 2 处 Javadoc 与前端类型 | P2 |
| BE-VAL-15 | 前端 `CaptchaChallenge.enabled` 与 `captchaRequired` 是**死代码**（后端不返回该字段 → 恒 `true`） | **必须与 BE-VAL-12 一起处理**：单独"修好前端"会制造"前端隐藏、后端仍强制"的真实故障 | P3 |
| BE-VAL-17 | `RoleController.grant` 的 `List<@NotNull Long>` 因**缺 `@Valid`** 而失效（39 处 `@RequestBody` 中的 3 处之一） | 是否补 `@Valid`？（属"加强"方向） | P3 |
| BE-VAL-18 | 需求明细三个 PUT 端点**无 DTO、无 `@Valid`**，超长值只能撞 DB 列宽 → 落到 `Exception` 兜底返回 **500「系统繁忙」**。前端也无长度约束（`TransactionCallDetailDialog` 14 个 `<Input />` 全部无 `maxLength`；`NewTransactionDetailDialog` 固定 2000 而 `service_window` 仅 16） | **D-037 已决策"不新增明细 DTO/校验"**，因此这不是待修缺陷；但需要你确认：**是否接受"超长 → 500"这一表现**？可选的低代价缓解是在前端补 `maxLength` 与列宽对齐（不动后端契约） | P2 |
| BE-VAL-08 | HBase/ES 字段 `sortOrder` 只有 `@NotNull`，**无 `@Min(0)`**（同类 `MenuRequest`/`DeptRequest` 都是 `@Min(0) @Max(9999)`） | 是否补齐以统一强度？ | P3 |
| BE-VAL-11 | `TRUSTED_PROXIES` 在反向代理部署下**必填** | 确认部署形态，并把该项写入部署清单 | P2 |

### 4.8 ⚠️ 一份需要注意的内部张力（供你判断，不构成缺陷判定）

D-027 的论证是"本系统是内网自用项目，**不存在外部恶意写入场景**"。但同一仓库中存在：

- `/api/auth/**` **完全匿名放行**（`WebSecurityConfig.java:65`）；
- 专门的密码爆破/喷洒防护（`LoginAttemptGuard.java:15-34`）；
- 专门防"脚本无限刷注册接口"的验证码（`CaptchaService.java:25-51`）。

这说明代码库**实际**假设了"有人可以匿名发请求"这一威胁模型。**本报告不以该张力要求修改 D-027**（它是你已确认的决策）；
仅提示：若将来以"内网无攻击者"为由**削减鉴权/限流**，将与本仓库既有实现的自证前提相矛盾。

### 4.9 该模块的「用户已决策、不建议再改」（登记为边界，避免后续会话重复报为问题）

D-014（同父字段英文名唯一，Service 预检 + V7 唯一索引双层是决策要求）、D-022（资源集合整体替换前去重是决策要求）、
D-024（引用保护由服务层显式实现、不用外键）、D-026、D-027（ES `data_type` 不做服务端枚举校验，**确认无违反**）、
D-030（不补领域测试）、D-034（`FOR UPDATE` 而非分布式锁/乐观锁，**确认无违反**）、
D-037（不加需求行锁、不加 `transaction_id` 唯一约束、不新增明细 DTO/服务端校验）。

### 4.10 该模块的「未检查范围」

未运行 `mvn`/`npm`/`tsc`（约束禁止构建），因此"分支恒为 false""500 的具体异常类型""日志框架是否真的写出密码值"均为**静态推断**；
未连接数据库（未验证 V7 唯一索引是否已执行、列的实际 DDL 是否与脚本一致）；未在浏览器验证前端行为；
未审计 `org/lbl/agent/**` 的参数绑定层、`system/log/{analysis,risk,adapter,retention}`、`realtime`、`notification`、`security/{jwt,filter,proxy}`、前端基线组件；
**未验证 Bean Validation 的生效链**（未确认 `spring-boot-starter-validation` 是否在 `pom.xml`——我已独立确认它在，见 §7.3）。

---

## 5. 前端冗余清单

> 本节的条目均由我在**当前代码**上逐条复核，与仓库历史中的前端专项审计（`frontend-deadcode-audit.md`）做了对照：
> 其中已修复的项**不再列出**（例如 `theme/index.ts`、`theme/types.ts` 已不存在；`classnames` 依赖已移除；
> `StatusTag` 的 `label` prop 现已在 `pages/system/user/index.tsx:60` 被使用），只保留仍然成立的问题。

### 5.1 已验证的问题

#### [FE-INF-01] `assignRequirementTransaction` 前端已定义但**零调用**：后端"分配交易码"能力没有 UI 入口

- **位置**：定义 `frontend/src/pages/intranet/requirement/api.ts:8`；后端端点 `backend/src/main/java/org/lbl/intranet/requirement/controller/IntranetRequirementController.java:37`（`PUT /api/intranet/requirements/{id}/transaction`）。
- **当前实现的作用**：把某个已通过审批的需求与一个"评估中"交易关联起来（`IntranetRequirementService.assignTransaction`，`:120-128`）。
- **判断证据**：对 `frontend/src` 全量检索 `assignRequirementTransaction`，**调用点 0 个**（唯一命中是定义行）。同时后端另一个分配端点 `PUT /{id}/developer`（`:36`，分配开发负责人）在**前端连 api 函数都没有定义**——`Requirement` 类型里有 `developerUserId`/`developerName`（`types.ts:3`），列表页也渲染了「开发负责人」列（`index.tsx:47`）和「交易资产」列，但**没有任何界面可以执行这两个分配动作**。
- **依赖关系**：不依赖其他前端代码。⚠️ 这是**待完成功能**，不是"可以删的死代码"——`api.ts:8` 是现成可复用的封装。
- **推荐操作**：**待确认（需要你给出产品判断）**——两种可能：
  1. 该功能**计划内尚未实现** → 保留 `api.ts:8` 并补 UI；
  2. 该功能**已改由别处承担** → 才考虑删除。
  **不要**在没有你的判断前直接删掉它。
- **影响面**：若删除，将来补 UI 需重写这一行；若保留，只是 1 行未被调用的导出。
- **风险等级**：P2（功能缺口，非过度设计）　**证据可信度**：高（引用计数为 0 是事实）　**审批状态**：待确认

#### [FE-PAGE-01] `frontend/src/pages/login/index.tsx` 中 `unifiedProvider` 变量是死变量（唯一使用点在大段注释里）

- **位置**：定义 `frontend/src/pages/login/index.tsx:32`；唯一使用点在同一文件 `:119-131` 的 JSX **注释块** `{/* {unifiedProvider && ( ... )} */}`。
- **当前实现的作用**：原计划渲染"统一身份认证"长按钮（UIAS）。
- **判断证据**：全仓库检索 `unifiedProvider` 只有 2 处命中：`:32` 的定义行与 `:119-131` 注释块内的引用。注释块外的 JSX 不再使用它。附带：`onThirdParty` 仍被其它登录方式使用（未受影响），所以**不能**连它一起删。
- **依赖关系**：`providers` state 由 `getExternalProviders()` 填充（`:33-37`），仍被其它按钮消费。
- **推荐操作**：**待确认**——需你回答「UIAS 统一认证入口是否还要恢复？」：要恢复则保留；不恢复则删除 `:32` 这一行（`uias` provider 的配置与后端适配**不要**随之删除，后端有独立价值）。
- **影响面**：删除 1 行；`tsc` 与运行行为均不变（因为该变量本来就没被真正读取）。
- **风险等级**：P0（死变量，删除风险极低）　**证据可信度**：高　**审批状态**：待确认

#### [FE-INF-02] `frontend/src/types/table.ts:36` 的 `SmartTableSelection<T>` 全仓库零引用

- **位置**：`frontend/src/types/table.ts:36-39`。
- **当前实现的作用**：一个表格行选择相关类型。
- **判断证据**：对整个 `frontend/src` 做逐符号引用计数，`SmartTableSelection` **总引用数 = 1**（只有声明行本身）——即零消费者。对照：同一文件的 `SmartTableRef`、`TableRequestParams` 等都有消费者（例如 `pages/intranet/transaction/index.tsx`、`components/SmartTable`）。
- **依赖关系**：无。`components/SmartTable/index.ts:3` 的 barrel 转发了 `types/table` 的若干类型，但**没有**转发这一个。
- **推荐操作**：删除（或保留为将来行选择功能的类型占位——请你在审批时二选一）。
- **影响面**：无。类型删除不影响构建产物。
- **风险等级**：P0　**证据可信度**：高　**审批状态**：待确认

#### [FE-INF-03] 登录页「统一认证长按钮」的 4 组 CSS 规则已无渲染点

- **位置**：`frontend/src/pages/login/login.css:236`、`:244`、`:247`、`:253`（`.login-panel .ant-btn.login-unified-button` 及其 `:hover`/`.is-disabled`/`.anticon` 变体）。
- **当前实现的作用**：为 UIAS 统一认证长按钮提供样式。
- **判断证据**：
  - 类名 `login-unified-button` 在全仓库只出现在 `login.css`（4 处）与 `login/index.tsx` 的**注释块**内（`:127`，已注释）。JSX 侧没有活的渲染点，因此这 4 组规则**不可能生效**。
  - CSS 内部注释自述「当前启用的统一认证长按钮；上面的圆形按钮样式保留，方便按环境切回原方案」，说明这是**有意保留的备用方案**。
- **依赖关系**：与 FE-PAGE-01 同源（同一个被注释的功能）。**必须先决定 UIAS 入口是否恢复**：恢复则两者都保留，不恢复则两者都删。
- **推荐操作**：**待确认**（与 FE-PAGE-01 合并决策）。
- **影响面**：⚠️ 删除属**样式删除，需浏览器目视回归登录页**（媒体查询 `@media (max-width: 800px)` 内没有引用这些类，故影响面仅限于 >800px 的登录页右侧面板）。
- **风险等级**：P1（无渲染点的样式，但可能是有意留的备选方案）　**证据可信度**：高（类名的 JSX 使用点确实全被注释）　**审批状态**：待确认
- **备注**：仓库历史中的前端专项审计记录过同一处（其 #7/#8/#9 条目），结论一致；本条为在当前代码上的复核版本。

#### [FE-PAGE-02] `log-page` / `log-toolbar` / `log-strong` / `log-mono` / `log-time` 五个类在 TSX 中零使用

- **位置**：定义在 `frontend/src/pages/system/loginlog/index.css:2` 与 `frontend/src/pages/system/operatelog/index.css:4-19`（两文件都 `@import '../shared.css'`）。
- **当前实现的作用**：登录日志/操作日志列表的表格分页间距、工具栏对齐、强调文字、等宽文本、时间列不换行。
- **判断证据**：对 `frontend/src` 全量检索这 5 个类名，**除两个 CSS 文件自身外 0 命中**（已排除模板字符串拼接：它们都是静态类名形态，不是 `` `log-${x}` ``）。
  再对照 `pages/system/loginlog/index.tsx`（92 行）与 `operatelog/index.tsx`（100 行）：两个页面用的是 antd 原生 `Table` + `Typography`，**没有** `className='log-page'` 之类。因此这 5 个类当前都不生效。
- **依赖关系**：⚠️ **`@import '../shared.css'` 这一行不能删**——`shared.css` 提供了 `.table-toolbar`、`.danger`、`.system-dialog` 等**正在被使用**的类（例如 `.system-dialog` 被 `MenuDialog`/`UserDialog` 等多个页面使用）。本条目只针对那 5 个自身无引用的类。
- **推荐操作**：**待确认**——若日志页当前有意使用 antd 默认样式，这 5 个类是**历史样式残留**，可删；若你打算恢复自定义日志样式，则保留。
- **影响面**：⚠️ 样式删除需**浏览器目视回归登录日志页与操作日志页**（分页间距、时间列换行是最容易看出差异的两处）。
- **风险等级**：P1　**证据可信度**：高　**审批状态**：待确认

#### [FE-CSS-01] `pages/system/loginlog/index.css` 与 `pages/system/operatelog/index.css` 是同一份样式的两份副本

- **位置**：`frontend/src/pages/system/loginlog/index.css` 与 `frontend/src/pages/system/operatelog/index.css`。
- **当前实现的作用**：两个日志页各自的样式入口。
- **判断证据**：两文件**内容等价、格式不同**（`Get-FileHash` 不同：`F68606...BC8D` vs `A5513F...2F90`，因为一个是单行压缩写法、一个是多行展开写法），逐条规则完全一致：`@import '../shared.css'` + `.log-page .ant-table-wrapper .ant-pagination` + `.log-toolbar` + `.log-strong` + `.log-mono` + `.log-time`。这属于**同一份规则两个副本**，改一处必漏一处（叠加 FE-PAGE-02：这些类当前还都没被使用）。
- **依赖关系**：分别被 `pages/system/loginlog/index.tsx:?` 与 `pages/system/operatelog/index.tsx` 通过 `import './index.css'` 引入（Vite 侧副作用导入）。
- **推荐操作**：**合并**——与 FE-PAGE-02 一起决策：若这 5 个类要保留，则合并进 `pages/system/shared.css`（两个页面已经 `@import` 它）并删除两个 `index.css`；若不要，则两个文件一起删（但需保留 `import '../shared.css'` 的替代写法）。**待确认**。
- **影响面**：⚠️ 两个日志页的样式来源发生变化，必须浏览器目视回归；另注意删除 `index.css` 会同时移除它们对 `shared.css` 的引入，需确认 `shared.css` 仍被其他页面引入（`pages/system/user/index.css:1` 也 `@import '../shared.css'`，安全）。
- **风险等级**：P1　**证据可信度**：高　**审批状态**：待确认
- **备注**：仓库历史中的前端专项审计记录为 #32（当时两文件**字节完全相同**）；现在已分叉为"压缩 vs 展开"两种写法，**问题性质不变、可读性更差**。

### 5.2 页面与组件层（前端专项审计结果）

> 本节由只读前端子审计会话产出后并入。它**通读了全部 131 个 `.ts/tsx` 与 34 个 `.css`**，
> 并对仓库历史中的 `frontend-deadcode-audit.md`（HEAD 中的 56 条）逐条复核，标注了 **7 条已失效的旧结论**（见 5.5）。

#### 5.2.1 动态扫描机制核实（决定「无用」的判定边界）

- `router/componentRegistry.tsx:19` 的 glob 是 `**/*.tsx`（**不是** `**/index.tsx`），实测命中 **28 个 tsx**：**12 个是页面**（`home/index` + 6 个 system + 4 个 intranet + 2 个隐藏 fields 页）、**16 个是对话框/共享组件**。
- **结论**：`pages/**` 下的 `index.tsx` 即使零静态 import **也不是死代码**（12 个页面组件的默认导出在静态引用图里都是 0 命中，属设计使然）。后端菜单种子（`init_data.sql:28-35` + `db/intranet/V2/V6/V9/V11/V14`）登记的全部是活页面。
- 用「零入站引用」脚本扫全部 131 个 ts/tsx：**没有任何整文件死代码**（`api/*`、`store/*`、`services/*`、`utils/*`、`theme/tokens`、`types/log` 全部被 import；唯一例外是 `vite-env.d.ts` 与动态路由页面本身）。

#### 5.2.2 问题清单

##### [FE-PAGE-03] `/home` 生产首页实际是一张 554 行的「组件示例页」

- **位置**：`frontend/src/pages/home/index.tsx:1-554`（`:220` 注释自述「组件展示页**不读取业务接口**；所有内容均为本地 mock」；47 条 mock 数据在 `:166-174`）。
- **判断证据**：它同时是**种子数据里的生产首页**——`backend/src/main/resources/db/init_data.sql:28` = `(1,0,'首页','MENU','home','/home','home/index',…)`；`layouts/AppLayout.tsx:28` 的 `HOME_ROUTE='/home'`、`:190` 的 `findMenuByPath(menus,'/home')` 也把它当首页。
- **依赖关系**：`BaseChart` / `CodeViewer` / `CopyButton` / `G6Graph` / `StatusTag` / `SmartTable` 的**唯一或主要消费者**就是这一页（见 FE-CMP-02）。
- **推荐操作**：**待确认（业务决策，不是技术风险）**——需要你决定 `/home` 是否就是这张演示页。**若要下线，必须同一个变更里改 `init_data.sql:28` 的 `component` 或补一个真实首页**；直接删文件会让 `/home` 变成 `PageUnavailable` 诊断页。
- **影响面**：高（牵连 3 个组件、4 个依赖与 13.35 MB 静态资源的去留）。
- **风险等级**：**P1**　**证据可信度**：高　**审批状态**：待确认

##### [FE-CMP-02] `CodeViewer` / `BaseChart` / `G6Graph` 都是单点消费，且唯一消费者就是上面那张演示页

- **位置**：`pages/home/index.tsx:399`（`BaseChart`）、`:414`（`G6Graph`）、`:434,458`（`CodeViewer`）。
- **判断证据**：全仓库命中数——`CodeViewer` 10（含 barrel 与自身）、`BaseChart` 12、`G6Graph` 7，**除 barrel 与自身外只有 `home` 一个消费者**。连带把 `monaco-editor` + `@monaco-editor/react` + `echarts` + `@antv/g6` 四个运行时依赖与 `public/monaco/vs`（103 文件 / 13.35 MB）拉进产物。
- **推荐操作**：**待确认**（与 FE-PAGE-03 **同一决策**）。⚠️ **`G6Graph` 必须单独确认**：`AGENTS.md` §4 与 `ARCHITECTURE.md` 明确「血缘图优先复用现有 `G6Graph`」，若该规划仍在执行，**它必须保留**。
- **风险等级**：**P1**　**证据可信度**：高　**审批状态**：待确认

##### [FE-PAGE-04] 「部门变更审批详情 Modal + 拒绝原因 Modal」在前端重复两份（含行为漂移）

- **位置**：`pages/account/approvals/index.tsx:183-229`（Steps `:207-226`、`approve() :66-79`、`reject() :80-95`、拒绝 Modal `:236-249`）对 `pages/account/notifications/index.tsx:128-174`（Steps `:152-171`、`approve() :56-69`、`reject() :70-88`、拒绝 Modal `:181-194`）。
- **判断证据**：Steps 的 `items` 映射**逐字相同**；两处各自维护 `approve/reject/submitting/reason` 状态。**行为漂移已实际发生**：`notifications:39` 的详情打开兜底分支与 `approvals:57` 的未知类型分支**语义相反**（一个静默失败、一个给提示）。→ 与 BE-NTF-02 是同一处问题的前后端两个视角。
- **推荐操作**：**合并**为共享的 `DepartmentChangeApprovalModal`（与 BE-NTF-02 一起做）。
- **风险等级**：**P2**　**证据可信度**：高　**审批状态**：待确认

##### [FE-PAGE-05] 同一套 Steps 状态映射还有第三份

- **位置**：`pages/account/security/index.tsx:197-215`（与上条同构的 `SKIPPED/APPROVED/REJECTED/WAITING/PENDING → 文案 + status` 映射）。
- **推荐操作**：**合并**（与 BE-APR-09 的类型收敛一起做）。
- **风险等级**：P3　**证据可信度**：高　**审批状态**：待确认

##### [FE-PAGE-06] 弹窗骨架 + 提交锁模式重复；且项目里**已有**可复用实现却没人用

- **位置**：**9 处**提交锁 ref（`DeptDialog:40`、`MenuDialog:37`、`RoleDialog:31`、`UserDialog:43` 的 `submittingRef`；`TransactionDialog:22`、`RequirementDialog:18` 的 `savingRef`；`login/index:25`、`AppLayout:75`、`AiAssistant:41`）。
  另有 **Modal 骨架 props 完全相同地抄了 5 遍**：`className='system-dialog'` + `okText/cancelText/onCancel/onOk/confirmLoading/okButtonProps={{disabled:loading}}/cancelButtonProps={{disabled:submitting||loading}}/closable={!submitting&&!loading}/maskClosable={!submitting&&!loading}/keyboard={!submitting&&!loading}/destroyOnHidden/forceRender`（`UserDialog:152-169`、`RoleDialog:95-112`、`DeptDialog:93-110`、`MenuDialog:98-115`）。`useLayoutEffect` 2 处/文件 × 5 文件 = 10；`form.resetFields()` 12 文件 12 处；`let active = true` 12 文件 12 处。
- **⚠️ 关键反证（这条让"重复"从"可接受"变成"应合并"）**：`pages/intranet/dataasset/AssetDialogs.tsx:9-23` **已经写好**可复用的 `useDialogLoad(open,id,load,empty,set,close)`，但 system 的 5 个弹窗**没有使用它**。即"可抽取却未抽取"。
- **推荐操作**：**合并**——抽 `useDialogLoad`（复用已有实现）+ `SystemDialog` 外壳 + `useSubmitLock()`。⚠️ 抽取时注意提交锁的**加锁时机不一致**（`MenuDialog:79` 在 `validateFields` **之前**加锁，其余多数在之后）。
- **风险等级**：**P2**　**证据可信度**：高　**审批状态**：待确认

##### [FE-PAGE-07] 登录日志页与操作日志页整体同构（组件 + 样式 + 逻辑）

- **位置**：`pages/system/loginlog/index.tsx`（98 行）vs `pages/system/operatelog/index.tsx`（106 行）。
- **判断证据**：`RESULT_META`、`RESULT_OPTIONS`、`TIME_FORMAT` 各 2 份；`remove()`（`loginlog:39-49` vs `operatelog:37-47`，均含 `currentRecordCount` ref）、分页配置、`scroll={{x:…}}`、toolbar `Popconfirm` 文案**几乎逐字相同**。样式侧见 FE-CSS（两条同类问题已并入 §6）。
- **推荐操作**：**合并**（抽 `LogTablePage` 或至少抽 `useLogDelete()`）。
- **风险等级**：**P2**　**证据可信度**：高　**审批状态**：待确认

##### [FE-PAGE-08] 内网需求「三个明细弹窗」同构 + 局部 `Text` 组件重复定义 3 份

- **位置**：`TransactionChangeDetailDialog.tsx:7`、`TransactionCallDetailDialog.tsx:10`、`NewTransactionDetailDialog.tsx:7` 各自写了一遍
  `const Text = ({name,label}) => <Form.Item name={name} label={label}><Input.TextArea rows={2|3} maxLength={2000}/></Form.Item>`
- **判断证据**：三者的 `useEffect` 加载 + `save()` + Modal 骨架（`className='system-dialog transaction-dialog' width={820} open={Boolean(id)} confirmLoading={saving} destroyOnHidden`）**同构**。
- **推荐操作**：**合并**——抽 `keyof T` 驱动的 `TextItem` 与统一明细弹窗外壳。⚠️ 关联 BE-VAL-18（这三个弹窗正是缺 `maxLength` 与列宽对齐的地方）。
- **风险等级**：**P2**　**证据可信度**：高　**审批状态**：待确认

##### [FE-PAGE-12] `try/catch + message.error(getApiErrorMessage(...))` 在 30 个文件里重复 82 次

- **位置**：检索 `message\.error\(getApiErrorMessage` = **82 命中 / 30 文件**；`getApiErrorMessage` 全仓 **116 命中 / 32 文件**；`App.useApp()` 37/35；`message.success` 51/33。
- **⚠️ 精确表述（避免误判）**：`utils/request.ts:35-63` 的响应拦截器**只处理 401 静默续期与进度条，不做统一错误提示**，所以这 82 处**不是"重复拦截"**，而是**"同一段错误提示样板抄了 82 次"**。把统一提示挪进拦截器属于**基线行为变更**（会改变所有页面的提示时机与文案）。
- **推荐操作**：**待确认**——抽 `useApiAction()`（不触碰 `utils/request.ts` 的拦截器语义，风险最低），或经批准后改拦截器。
- **影响面**：30 个页面文件的错误提示行为；⚠️ 抽 hook 会改变"提示由谁触发"的时序，需逐页回归。
- **风险等级**：**P2**　**证据可信度**：高（命中数已核实）　**审批状态**：待确认

##### [FE-PAGE-09] 登录会话列表项渲染两份

- **位置**：`pages/system/user/index.tsx:239-248` vs `pages/account/security/index.tsx:324-332`。`authMethod==='PASSWORD'?'账号密码':providerKey` + `rememberMe` Tag + `[loginIp, lastActiveTime?.replace('T',' '), userAgent].join(' · ')` **完全一致**。
- **推荐操作**：**合并**（抽 `SessionListItem`）。
- **风险等级**：P3　**证据可信度**：高　**审批状态**：待确认

##### [FE-PAGE-10] 「删除后若当前页被删空则回退一页」三份，且语义已分叉

- **位置**：`pages/system/user/index.tsx:121-122`、`pages/system/loginlog/index.tsx:43-45`、`pages/system/operatelog/index.tsx:41-43`。⚠️ **`user` 页的条件是 `=== 1`，另两页是 `=== 选中条数`** → 统一前**必须确认哪个是对的**。
- **推荐操作**：**待确认**（先定语义，再抽 `reloadAfterDelete`）。
- **风险等级**：P3　**证据可信度**：高　**审批状态**：待确认

##### [FE-PAGE-11] 分页契约适配手写 5 份

- **位置**：`{page,pageSize,search}` → `{pageNum,pageSize,keyword}` → `{records,total}` → `{list,total}` 这段适配在 `pages/home/index.tsx:487-492`、`pages/intranet/dataasset/index.tsx:39`、`pages/intranet/transaction/index.tsx:110-123`、`pages/system/loginlog/index.tsx:74-77`、`pages/system/operatelog/index.tsx:81-84` 各写一遍。
- **推荐操作**：**待确认**（在 `types/table.ts` 或 `SmartTable` 侧统一提供一次适配。⚠️ 各页 key 名略有差异（`keyword`/`esfKeyword`），需保留搜索字段映射）。
- **风险等级**：P3　**证据可信度**：高　**审批状态**：待确认

##### [FE-PAGE-13] 重复的时间格式化与文件名生成（项目已引入 dayjs 却基本没用）

- **位置**：`?.replace('T',' ')` = **16 命中 / 13 文件**（`AppLayout:158`、`system/{user:63,247, role:44, dept:65, loginlog:57, operatelog:64}`、`account/{approvals:127, notifications:120, security:331}`、`intranet/{transaction/index:55, dataasset/index:29,35, requirement/index:48, RequirementDetailDrawer:7}`）；`dayjs(` 仅 **2 命中 / 2 文件**（`RequirementDialog:2`、`TransactionCallDetailDialog:4`）。
  另 `pages/system/user/index.tsx:48` 用 `new Date().toISOString().slice(0,10)` 造导出文件名，与 `loginlog`/`operatelog` 各自的 `TIME_FORMAT` 常量各写各的。
- **推荐操作**：**合并**——抽 `utils/datetime.ts`（`formatDateTime` / `formatDate`）。
- **风险等级**：P3　**证据可信度**：高　**审批状态**：待确认

##### [FE-PAGE-02] `LoginBrand` 与 `SiderBrand` 是两个近乎逐字相同的品牌区封装

- **位置**：`pages/login/LoginBrand.tsx:1-14` vs `layouts/SiderBrand.tsx:1-14`；**差异仅外层 class**（`login-brand` vs `sider-brand`）。引用方：`pages/login/index.tsx:9,86`、`pages/register/index.tsx:6,129`、`layouts/AppLayout.tsx:6,234`。
- **推荐操作**：**合并**（抽 `<Brand variant>`）。
- **风险等级**：P3　**证据可信度**：高　**审批状态**：待确认

##### [FE-CMP-03] 三个组件共 9 个从未被传过的 prop / 不可达分支

- **位置**：`BaseChart.tsx:8-17` 的 `loading/empty/emptyText/className/style/renderer` 六项（唯一调用点 `home:399` 只传 `option`/`height`）→ `:26,35,38-41,45,47-56` 分支不可达；`G6Graph.tsx:9` 的 `className`；`IconPicker.tsx:18-19` 的 `disabled`/`className`（唯一调用点 `MenuDialog.tsx:164` 只传 `options`/`placeholder`）→ `:90 if(disabled) return` 与 `:96-97` 不可达。
- **推荐操作**：**简化**（⚠️ 若保留演示页，这些 prop 是"为将来复用预留"；与 FE-PAGE-03 一起决策）。
- **风险等级**：P3　**证据可信度**：高　**审批状态**：待确认

##### [FE-CMP-04] `StatusTag` 的 `label` 与 `children` 双入口——**旧审计结论已失效**

- **位置**：`components/StatusTag/StatusTag.tsx:8`（`label?`）、`:23`（`label ?? children`）。
- **判断证据**：**两个入口当前都有消费者**——`children` 4 处（`pages/home/index.tsx:244,355,356,357`）、`label` **1 处**（`pages/system/user/index.tsx:60`）。⚠️ 仓库历史中的前端审计称"3 个调用点全用 children、label 0 使用"——**该结论已过时**。
- **推荐操作**：**简化**（保留 `children`，把 user 页那一处改成 children）。
- **风险等级**：P3　**证据可信度**：高　**审批状态**：待确认

##### [FE-CMP-05] `ArtifactRendererRegistry` 是只有 1 个条目的插件注册表（含未使用的注销闭包 + 死转出口）

- **位置**：`components/ai/artifacts/ArtifactRendererRegistry.ts:16-24`（返回 `() => renderers.delete(key)`，**无任何调用点**）；全项目只注册 1 个渲染器 `'ui.report'@1`；`resolveArtifactRenderer` 唯一调用点也是 `AgentArtifactView.tsx:14`；`AgentArtifactView.tsx:9-10` 的 `export { registerArtifactRenderer }` / `export type { AgentArtifactRendererProps }` **零消费者**（真实消费者 `GenericReportRenderer.tsx:3` 直接引 `../ArtifactRendererRegistry`）。
- **说明**：与本报告 §2.3 的 BE-LOG-14 是**同一处问题**（前后端两路审计独立发现，结论一致）。
- **推荐操作**：**简化**。
- **风险等级**：P3　**证据可信度**：高　**审批状态**：待确认

##### [FE-CMP-06] `useClipboard` 的 `error` 返回值 0 消费者 + `execCommand` 降级路径

- **位置**：`hooks/useClipboard.ts:48` 返回 `{copy, copied, copying, error}`，唯一使用点 `components/CopyButton/CopyButton.tsx:15` 只解构 `{copy,copied,copying}`；`:3-17` 保留 `document.execCommand('copy')`（**已废弃 API**）降级路径。
- **推荐操作**：**简化**（去掉 `error`；`execCommand` 降级**待确认**——取决于内网浏览器基线，若仍有旧国产内核在用则必须保留）。
- **风险等级**：P3　**证据可信度**：error 高／execCommand 是否仍需 **中**　**审批状态**：待确认

##### [FE-CMP-07] 3 个 hook 都是单消费者

- **位置**：`useClipboard`（4 命中：定义 + `CopyButton.tsx:3`）、`useFullscreen`（4：`CodeViewer.tsx:3`）、`useResizeObserver`（4：`BaseChart.tsx:3`）。
- **判断证据**：三者实现质量良好，**不是"无意义封装"**；但"一个 hook 服务一个组件"时抽 hook 只增加了间接层。
- **推荐操作**：**保留**（`useResizeObserver` 有真实生命周期价值）／另两个**待确认**。
- **风险等级**：P3　**证据可信度**：高　**审批状态**：待确认

##### [FE-INF-04] **19 个「导出但仅在本文件内被使用」的符号**（多余 export）

- **位置与命中数**（口径：全仓 `*.ts/tsx/css/json/html/java/sql`，排除 node_modules/dist）：
  `usePermission`=2（全在 `permission/Permission.tsx`，页面一律用 `<Permission>`）｜`MENU_ICONS`=4（全在 `router/iconRegistry.tsx`）｜`NOTIFICATION_FETCH_LIMIT`=3（全在 `store/notificationSlice.ts`）｜`SmartTableSelection`=**1（仅定义行）**｜`SmartTableReloadOptions`=2｜`TableRequestResult`=3（含 `SmartTable/index.ts:3` 转出口，**无消费者**）｜`AgentChatHandle`=2｜`RefreshResult`=2｜`AuditFields`=5｜`SelectOption`=3｜`MenuType`=3｜`LogQuery`=3｜`MessageNodeType`=3｜`RequirementType`=3｜`RequirementStatus`=2｜`InterfaceDensity`=3｜`ThemePreference`=5｜`RealtimeEvent`=3｜`InlineEditableTableProps`=2 —— **文件外全部 0 命中**。
- **推荐操作**：**简化**（去掉多余 `export`，或删除 `SmartTableSelection` 这类完全无引用的符号）。
- **风险等级**：P3　**证据可信度**：高（逐符号计数）　**审批状态**：待确认

##### [FE-INF-05] `MenuRoute` 与 `SystemMenu` 是两份高度重叠的菜单类型，且联合类型被内联复制

- **位置**：`types/auth.ts:76-91` 的 `MenuRoute`（含 `keepAlive`）vs `types/menu.ts:3-19` 的 `SystemMenu`（含 `builtin`/`createdTime`），**12 项字段重合**；`types/auth.ts:80` 把 `'DIR'|'MENU'|'BUTTON'` **内联又写了一遍**，而 `types/menu.ts:1` 已有 `export type MenuType`（`MenuType` 因此只剩 3 处文件内命中，成了"导出了却没人用"的类型）。
- **推荐操作**：**简化**（`MenuRoute` 改为 `Pick<SystemMenu,…> & {keepAlive?}` 并复用 `MenuType`）。
- **风险等级**：P3　**证据可信度**：高　**审批状态**：待确认

##### [FE-INF-06] `types/table.ts` 死类型 + `SmartTable` barrel 做类型转发

- **位置**：`types/table.ts:36-39` 的 `SmartTableSelection<T>` 全仓 **1 命中（定义行）**；`components/SmartTable/index.ts:3` 把 `SmartTableRef/TableRequest/TableRequestParams/TableRequestResult/TableSorter` 从 `types/table` **再转一次**，消费者（如 `pages/intranet/dataasset/index.tsx:6`）为此必须**从组件 barrel 引类型**，而 `SmartTable.tsx:6` 自己就是从 `types/table` 引的。
- **推荐操作**：**简化**（消费方直接从 `types/table` 引类型，barrel 只留组件）。
- **风险等级**：P3　**证据可信度**：高　**审批状态**：待确认

##### [FE-INF-07] `types/agent.ts` / `auth.ts` / `api/departmentChange.ts` 的 10 个死字段

- **位置与实测命中数**：`AgentMetric.format`=`metric\.format` **0**｜`AgentMetric.status`=`metric\.status` **0**｜`AgentFinding.code`=`\.code\b` **0**｜`AgentReport.metadata`=`\.metadata` **0**｜`AgentStreamEvent.runId`=`\.runId` **0**｜`AgentReportSection.type` 含 `'chart'` 但 `GenericReportRenderer.tsx:12-19` **无 chart 分支**（落 default 显示"暂不支持区块"）｜`CurrentUser.superAdmin`=**1**（定义行）｜`DepartmentChangeDetail.finishedTime`=**1**、`canCancel`=**1**｜`MenuRoute.routeName` **0 读取**（`routeName` 的真实使用点在 `MenuDialog.tsx:4`/`types/menu.ts:2`，走 `SystemMenu`）。
- **推荐操作**：**简化**（删除零消费字段；`chart` 要么补渲染分支、要么移除）。
- **风险等级**：P3　**证据可信度**：高　**审批状态**：待确认

##### [FE-INF-08] 通知详情用 URL query 传瞬态状态，与全局 store 并存两套状态通道

- **位置**：`layouts/AppLayout.tsx:131` 把 `businessId/businessType/notificationType` 拼进 `/account/notifications?...`；`pages/account/notifications/index.tsx:33-41` 再解析回组件 state（`setRequirementApproval`/`setDetail`）；同一份通知数据又在 `store/notificationSlice.ts` 全局持有。
- **判断证据**：该 slice 的注释**明确记录过**「两处 `useState` 不一致 → 红点不消失」的历史 bug。URL 传参本身可接受（可分享/可刷新），但与 store 并存使"弹窗由谁打开"出现**三个入口**（`open(item)`、query、`RequirementApprovalDetailModal`）。
- **推荐操作**：**保留 URL 通道**但收敛为单一 `useNotificationTarget()`。
- **风险等级**：P3　**证据可信度**：高　**审批状态**：待确认

##### [FE-INF-09] `navigateTarget` 是一行恒等包装

- **位置**：`router/menu.ts:34-36` `export function navigateTarget(path){return {pathname:path}}`；命中 5 处（`menu.ts:1`、`DynamicPage.tsx:1`、`AppLayout.tsx:1`、`Forbidden.tsx`、`NotFound.tsx`）。
- **推荐操作**：**简化**（内联为 `{pathname}`）。
- **风险等级**：P3　**证据可信度**：高　**审批状态**：待确认

##### [FE-INF-10] `RealtimeClient` 指数退避上叠了随机抖动

- **位置**：`services/RealtimeClient.ts:50`：`Math.min(30_000, 1_000*2**Math.min(this.retry++,5)) + Math.floor(Math.random()*500)`。
- **判断证据**：抖动的作用是打散大量客户端同时重连；本系统在线连接数量级为**个位到十位**。
- **推荐操作**：**简化/待确认**（保留退避与上限，去掉抖动）。
- **风险等级**：P3（无实际收益，也无害）　**证据可信度**：高　**审批状态**：待确认

##### [FE-INF-11] 存在两套并行的 401 续期实现（**说明项，非重复实现**）

- **位置**：`utils/request.ts:47-61`（axios 拦截器 + `_retry` + `AUTH_BOOTSTRAP` 白名单正则 `:18`）与 `services/authSession.ts:40-57`（`authenticatedFetch`，供 SSE/fetch 用）。
- **判断证据**：二者**共享** `refreshAccessTokenOnce()`（`authSession.ts:14-27`，带 `refreshPromise` 并发合并）与 `expireSession()`。**不构成重复实现**（一套给 axios、一套给原生 fetch，无法合并），但**必须成对维护**：任何对 `AUTH_BOOTSTRAP` 的改动都要同步评估 SSE 路径。
- **推荐操作**：**保留**（记入本报告以免后续会话误判为冗余）。
- **风险等级**：P3　**证据可信度**：高　**审批状态**：建议保留

#### 5.2.3 依赖与配置侧（与 §7.2 的 CFG-* 合并）

| 前端专项编号 | 结论 | 与 §7 的关系 |
| --- | --- | --- |
| FE-DEP-01 | `html2canvas` 从未被使用（`src/**` 与 `vite.config.ts` **0 命中**；但**陈旧构建产物** `frontend/dist/assets/index-kBuWb80_.js` 里仍有 4 处命中 → 说明历史上确实打包过） | **即 CFG-01**（新增了"构建产物里曾存在"这一证据） |
| FE-DEP-02 | `monaco-editor` 在 src 内**只以类型方式**存在（`CodeViewer.tsx:5` 的 `import type`），运行时来自手工落盘的 `public/monaco/vs` | **新增**，P3，可信度中（移到 devDependencies 需先确认 `public/monaco/vs` 的生成方式） |
| FE-DEP-03 | `@types/node` 无显式使用点（两个 tsconfig 都**没有** `types`/`typeRoots` 声明，src 内 0 命中） | **新增**，P3，可信度中（`tsconfig.node.json` 可能隐式需要它） |
| FE-DEP-04 | `.env.development` / `.env.production` **已入库**（`git ls-files` 确认）；根 `.gitignore` 只忽略 `.env`/`.env.local`/`.env.*.local` | **新增**，**P2**：当前值本身不敏感（localhost 与相对路径），但形成"环境配置可入库"的先例。⚠️ 改用 `.env.example` 需同步处理，否则新克隆仓库 `baseURL` 变空（`utils/request.ts:5`） |
| FE-DEP-05 | `.prettierrc.json` 孤儿配置 | **即 CFG-03** |
| FE-DEP-06 | 3 处 `eslint-disable` 指向不存在的 ESLint | **即 CFG-04** |
| FE-DEP-07 | `tsconfig.app.json` 未开 `noUnusedLocals`/`noUnusedParameters`；全仓**唯一**实锤死导入是 `MenuOutlined` | **即 CFG-10**（前端侧独立复核，结论一致） |
| FE-DEP-08 | `public/monaco/vs` 冗余（103 文件/13.35 MB/81 个语言目录/9 个 nls 含 zh-cn） | **即 FE-DEP-01（§7.2）** |
| FE-CMP-01 | glob 收集非页面对话框（16 个文件），选中即白屏 | **即 CFG-09**（前端侧独立复核，文件数一致） |

#### 5.2.4 该模块的「必须保留的必要设计」

1. **`componentRegistry.tsx` 的 `import.meta.glob` + `resolved` Map 缓存**（`:32`；注释记录过"每次渲染重新 lazy → React 卸载重建 → 接口重复请求"）——「新增页面不改 map」的唯一支点。
2. **`components/common/AuthGuard.tsx`** 的会话恢复 + `onboardingElsewhere`（`:56-57`）额外拦一帧。
3. **`utils/request.ts` 的 401 拦截 + `AUTH_BOOTSTRAP` 精确白名单**（`:18` 刻意不用 `/\/auth\//`，否则会误伤 `/auth/onboarding/account/*`）+ `refreshAccessTokenOnce` 并发合并 + `expireSession` 单次收口。
4. **`services/progress.ts` 三层保护**（300ms 延迟 + `active` 并发计数 + `resetProgress` 收口），分别对应三个**已发生过**的真实 bug。
5. **`SmartTable.tsx:111,117-138` 的请求竞态保护**（`requestSequence` 丢弃过期响应）与 `:163-165` 的 `tableStateVersion`。
6. **`permission/Permission.tsx` + `DynamicPage.tsx:29-81` 的三级判定**（我的菜单 → 403 → 404）与 `keepAlive` 缓存——菜单树即授权结果，**不要**在前端再维护"路径→权限码"映射。
7. **`theme/ThemeProvider.tsx:97-118` 的「AntD token + CSS 变量两套必须同投」** 与 `theme/tokens.ts` 的唯一定义处。
8. **`SmartTable` 作为列表页共享抽象**（11 个消费点）以及 `EllipsisText`（11 文件/40 命中）、`FieldLabel`（4 文件/30 命中）——这些是**真正的有效复用**，与 FE-CMP-02 的三个单点组件性质不同。
9. **`index.html:21-102` 与 `loadingScreen.css` 的两份首屏样式必须保留两份**（React 挂载前无 JS 样式可用）；只是选择器已分叉（见 §6 的 FE-CSS-02）。
10. **`utils/passwordPolicy.ts` 的前端副本**（必须与后端 `PasswordRules` 逐字一致，文件头已写明同步义务）——对应 BE-VAL-19。
11. **`services/RealtimeClient` + `store/notificationSlice` 的通知单源设计**。

#### 5.2.5 ⚠️ 旧审计结论中**已失效、请勿再引用**的条目

| 旧条目 | 现状 |
| --- | --- |
| 旧 #1/#2（`theme/index.ts`、`theme/types.ts` 整文件死代码） | **文件已不存在** |
| 旧 #6（`ThemeProvider.tsx` 的兼容再出口） | **已移除** |
| 旧 #10（`user/index.css` 的 `.password-note`） | **已移除**（现文件仅 14 行） |
| 旧 #11（`shared.css` 的 `.form-grid`） | **已移除**（全仓 `form-grid` 0 命中） |
| 旧 #21（`StatusTag.label` 0 使用） | **已失效**，`label` 现有 1 个消费者（见 FE-CMP-04） |
| 旧 #22（`classnames` 依赖冗余） | **已移除**（`package.json` 中已无 `classnames`） |
| 旧 #32（两个日志 `index.css` 字节相同） | **hash 已分叉**，但规则集仍相同（见 §6） |
| 旧 #36（提交锁 8 处） | 现为 **9 处** |

#### 5.2.6 前端侧「已检查范围」

**逐个读取全文**：`main.tsx`、`vite-env.d.ts`；`router/` 全部 5；`store/` 全部 4；`services/` 全部 4；`utils/` 全部 4；`hooks/` 全部 3；`theme/` 2；`permission/` 1；`layouts/` 2；`api/` 全部 11；`types/` 全部 9；`components/` 全部 39；`pages/` 全部 45。
另通读 `frontend/{package.json,vite.config.ts,tsconfig*.json,index.html,.prettierrc.json}`、`npm-offline/{pending,state}.json`、`docs/code-audit.md`、`git show HEAD:frontend-deadcode-audit.md`、后端全部 `db/**/*.sql` 的菜单种子。

**未检查**：`node_modules/**` 完整依赖树、`frontend/dist/**` 内部（仅用它证明 `html2canvas` 曾被打包）、`public/**` 除 monaco/favicon 外的资源、后端 Java 源码（仅按需检索菜单 component 与 `assignRequirementTransaction`）、`docs/intranet/*.md` 全文。
**未执行任何构建/类型检查/运行时验证**——所有"不可达/白屏"结论都是**静态控制流推导**。

---

## 6. UI / CSS 历史残留清单

> 由只读子审计会话产出后并入。**方法学前提（三项决定性事实，全部实测）**：
>
> 1. **代码分割改变了层叠分析**：`frontend/dist/index.html` 只 `<link>` 一个 `assets/index-DGxaXTeF.css`；其余 13 个页面 CSS 在懒加载 chunk 里按需注入。
>    **每个页面 chunk 内都内联了一份 `shared.css` 副本并被提升到文件最前面**（实测 `index-qX2_iY6o.css`、`index-WjyWEUDb.css`、`index-BN_EpCkD.css` 等都以 `.table-toolbar{...}` 开头，字节偏移 idx 0）。因此**同特异性冲突一律「页面文件胜出」**——本节的覆盖判定是从**构建产物的真实字节偏移**取证的，不是从 import 顺序推理。
> 2. **全仓库没有** `clsx`/`classnames`/CSS Modules/styled-components/emotion/Tailwind/Less/Sass，**也没有运行时样式注入**（`insertRule`、`document.createElement('style')`、`dangerouslySetInnerHTML` 全部 0 命中）。动态类名**只**来自模板字符串拼接，已穷举 13 处。
> 3. `styles/theme.css`、`styles/reset.css` **没有直接 import**，靠 `global.css:1-2` 的 `@import` 生效（正常做法，非缺陷）。

### 6.1 真·零引用类名清单（已逐一排除动态引用）

**方法**：① 从 34 个 CSS 抽出全部类名（**240 个**）；② 从全部 `*.tsx/*.ts/*.html` 的 `className="..."`/`class="..."` **字面量**抽静态集合（173 个）；③ 从 `className={`…`}` 模板字面量抽动态 token；④ 对差集**逐个人工读源码确认**。

| 类名 | 定义位置 | 结论 |
| --- | --- | --- |
| `.log-toolbar` | `loginlog/index.css:2`、`operatelog/index.css:5-7` | ⚠️ **在用**——由 `loginlog/index.tsx:80`/`operatelog/index.tsx:87` 的 `toolbarClassName='table-toolbar log-toolbar'` 注入。**文本检索 0 命中是假阳性** |
| `.smart-table-demo-toolbar/-search/-actions` | `home/home.css:80-92` | **在用**（`home/index.tsx:495-497` 经 SmartTable 的三个 className prop 注入） |
| `.bubble.assistant` / `.bubble.user` | `AiAssistant.css:178-187` | **在用**（`AiMessageList.tsx:25` 的 `` `bubble ${message.role}` ``） |
| `.identity-wechat/github/google` | `security/security.css:65-76` | **在用**（`security/index.tsx:278` 的 `` `identity-icon identity-${provider.icon}` ``） |
| `.social-wechat/github/google` | `login.css:218-229` | **在用**（`login/index.tsx:147` 的 `` `social-icon-button social-${provider.key}` ``） |
| `.icon-picker-popover` | `iconPicker.css:1-8` | **在用**（`IconPicker.tsx:84` 的 antd 5 `classNames={{root:...}}`） |
| `.base-chart` | `baseChart.css:1-5` | **当前为死规则**——`BaseChart.tsx:45` 支持 `className` prop，但**全仓库唯一调用点** `home/index.tsx:399` 未传 |
| `.code-viewer` | `codeViewer.css:1-6` | **部分生效**——`CodeViewer.tsx:86` 支持 `className` 但 `home/index.tsx:434,458` **未传**；该文件内的 `.code-viewer.is-fullscreen` 仍以它为父选择器 |
| `.g6-graph` | `g6Graph.css:1-4` | **必然生效**（`G6Graph.tsx:43` **硬编码** `` `g6-graph${className ? ...}` ``）——唯一一个"零字面量但必然命中"的组件根类 |
| `#nprogress .bar` / `.peg` | `global.css:8-13` | **必须覆盖**（由 nprogress 运行时生成，`main.tsx:11` 引入其样式）——非缺陷 |
| 全部 `.ant-*` / `.anticon`（**49 个**） | 24 个 CSS 文件 | **绝不能按"0 命中"判定无用**——由 antd 运行时渲染。逐条评估见 6.6 |

### 6.2 选择器覆盖矩阵（从产物字节偏移取证）

**真实冲突（同特异性 → 源码序后者胜）**：

| 选择器 | 定义位置 | 实际生效者 | 被覆盖规则是否仍可能生效 |
| --- | --- | --- | --- |
| `.ant-form-item`（弹窗内） | `.system-dialog`（`shared.css:100-102`，18px）vs `.menu-dialog`（`menu/index.css:3-5`，14px） | **14px**（menu chunk：shared idx 1067 < menu idx 2541） | **是**。`menu/index.css` 只靠 `.menu-dialog` 这个**并列类**取胜；任何未加 `menu-dialog` 的弹窗仍是 18px。属**脆弱写法** |
| `.ant-form-item-label-wrap`（交易弹窗内） | `shared.css:79-82`（`overflow:visible;white-space:normal`）vs `transaction/index.css:5-10`（`overflow:hidden;white-space:nowrap;text-overflow:ellipsis`） | **ellipsis 版**（transaction chunk：shared idx 787/864 < transaction idx 2620） | **是**。两者特异性相同（0,2,0），交易弹窗确实同时挂两个类。⚠️ **含义反转**：D-031/`shared.css` 的"长 label 折行兜底"在交易弹窗内被**主动回退成单行省略**——**是有意为之**（交易字段多），但两处意图相反且只靠源码序区分 |
| `.page-fill > .table-card > *` vs `.page-fill > .table-card > .card-scroll` | `global.css:78-80`（`flex:0 0 auto`）vs `:86-90`（`flex:1 1 auto`） | **`.card-scroll` 版**（同特异性，后者源序更后） | **是，且依赖源序**。这是 D-031 约定内部的**有意排序**，逻辑正确但**很脆**：任何人在两条规则之间插入第三条就会让滚动区失去 `flex:1` |
| `.table-card` | `global.css:28-34`（padding 24px）vs `:36-38`（`:root[data-density='compact']` → 18px） | 紧凑模式 18px | 是（条件性） |
| `.table-toolbar` | `shared.css:5-10` vs `:214-217`（`@media max-width:600px` → `flex-direction:column`） | 窄屏版 | 是（媒体查询条件性生效，**非覆盖**） |

**高特异性覆盖共享规则**：`.dataasset-dialog .ant-form`（`dataasset/index.css:3`）覆盖 `.system-dialog .ant-form`（`shared.css:134-137`）的 520px 限宽——**同特异性靠源序取胜**。
`.role-grant-dialog .ant-modal-body`（`role/index.css:2`，`padding-bottom:18px!important`）与 `shared.css:52-63`（`padding:20px 24px!important`）**两者都生效**（简写先设四边、长写后覆盖下边）——非冗余，但依赖 `!important` 之间的源序。

**确认无冲突（易被误判为覆盖）**：`.quick-search .ant-select-selector` 与 `.transaction-field-page .ant-table .ant-select-selector`（作用域不重叠且后者特异性更高）；`.login-panel`/`.quick-search`/`.register-page`/`.transaction-field-page`/`.dataasset-field-page`/`.table-toolbar` 六处都改 `.ant-input-affix-wrapper` 但**作用域互不重叠**。

### 6.3 问题清单

#### [FE-CSS-01] `--fs-md` 变量在主题阶梯中**不存在**，AI 助手 Markdown 标题字号静默失效

- **位置**：`frontend/src/components/ai/AiAssistant.css:212`（`.ai-markdown h1,h2,h3,h4{font-size:var(--fs-md)}`）。
- **当前实现的作用**：给 AI 回答里的 Markdown 标题统一 16px 视觉级别。
- **问题证据**：① `theme.css:33-45` 定义的阶梯是 `--fs-base/-xs/-sm/-lg/-xl/-2xl/-3xl/-display/-hero/-huge/-mega`，**没有 `--fs-md`**；② 自动化核对（把 34 个 CSS + 全部 ts/tsx 的 `--x:` 定义集合与 `var(--x)` 使用集合**求差**）结果为**唯一差项就是 `--fs-md`**，命中 1 处即本行；③ `tokens.ts` 与 `ThemeProvider.tsx:109-117` 也只注入 `--brand/--font-sans/--fs-base/--success/--warning/--error`。
  → 该 `font-size` 声明在计算值阶段为 **invalid at computed-value time**，属性**回落为 `inherit`**，而同一规则里的 `margin`/`line-height` 正常生效。**即"标题字号没跟上主题字号设置"**。这与 `security.css:28-30` 里**已修复**的 `--text-strong` 是同一类历史残留。
- **推荐操作**：**修改**（二选一）：(a) 改成既有的 `var(--fs-lg)`（= base+2 = 16px，**零视觉变化**）；(b) 在 `theme.css` 补 `--fs-md`。**建议 (a)**——(b) 会给阶梯引入一个与 `--fs-lg` 重复的档位。
- **影响面（视觉回归）**：**低但必须目视**——只有"AI 助手回答中出现 Markdown `#`~`####` 标题"时才可见；且**当前既然失效，改 (a) 会让标题变大**（这才是原本意图）。
- **风险等级**：**P1**（静默失效，与"字号全局生效"这一已确认设计目标不一致）　**证据可信度**：**高**（自动化全仓库变量集合求差，唯一命中）　**审批状态**：待确认

#### [FE-CSS-02] 首屏加载页是**两份会漂移的实现**，且**选择器不匹配**

- **位置**：`components/common/loadingScreen.css:4,21,32,38,50-89,103`（`.app-loading*` **类**选择器）vs `frontend/index.html:21,46,53,63,69,80-87,94`（`#app-loading` **ID** + `.app-loading-*` 混用）。
- **问题证据**：
  1. **选择器不匹配**：`index.html:106` 的 `<div id="app-loading">` **没有 `class` 属性** → `loadingScreen.css` 的**全部容器级规则**（`position:fixed/inset:0/z-index:9999/display:flex/…`）在产物里对首屏元素**完全不生效**；首屏实际靠 `index.html:21-51` 的内联样式。
  2. **两份实现已经漂移**：内联用兜底写法 `var(--brand,#1677ff)`（合理的预挂载兜底），CSS 版用裸 `var(--brand)`；`.app-loading-mark` 的 `box-shadow` 内联是**硬编码** `rgba(24,119,241,.22)`、CSS 版是 `color-mix(in srgb,var(--brand) 22%,transparent)`。
  3. `index.html:45-51` 有一条 CSS 侧**没有对应实现**的 `@media (prefers-color-scheme: dark)` 深色兜底。
  4. 文件顶部与 `index.html:12` **互相声明"必须同步改"**，属已登记的**有意重复**，但**已实际漂移**。
- **依赖关系**：`LoadingScreen.tsx:10` 渲染 `.app-loading`（类），`index.html` 渲染 `#app-loading`（ID）——**两者永不互相命中**，这就是"两份实现"的物理成因。
- **推荐操作**：**待确认 → 建议统一**（最小改动、低风险）：给 `index.html:106` 的 div **加上 `class="app-loading"`**，让 CSS 副本的首屏部分真正生效并与组件共用；或在 `loadingScreen.css` 补 `#app-loading{…}` 别名。⚠️ **不建议删除任何一份**（`index.html` 那份必须在无 JS 时存在）。同时把两份的 `box-shadow` 统一为 `color-mix(var(--brand)…)`，否则**换品牌色时首屏光晕仍是蓝的**。
- **影响面（视觉回归）**：**中**。加 `class` 后同特异性下 `.app-loading`（样式表）**低于** `#app-loading`（ID + 内联），**首屏视觉不变**；深色兜底那条特异性 (1,1,0) 仍高于 `.app-loading`。**必须刷新页面目视确认首屏**（含深色系统 + 手动选浅色两种组合）。
- **风险等级**：**P1**（选择器不匹配 → 一半实现是死代码；且换品牌色/深色主题时首屏光晕颜色不跟随）　**证据可信度**：高　**审批状态**：待确认

#### [FE-CSS-03] 分页 margin 规则在 **6 个 CSS 文件**里逐字重复

- **位置**：`user/index.css:4-6`、`loginlog/index.css:2`、`operatelog/index.css:2-4`、`transaction/index.css:2-4`、`dataasset/index.css:2`、`home/home.css:104-106`（最后一条形态不同）。
- **问题证据**：① **5 处规则文本完全一致**：`margin: 16px 0 0 !important`（前缀类名不同）；② 每个页面 chunk 各自打包一份——实测 `index-8ABVz46g.css:2541`、`index-CN2JjV-A.css:2541`、`index-T3Ll6fME.css`、`index-D7m8TGBm.css` 在**同一字节偏移 2541** 出现该规则，**证明是同一段源码被复制进 5 个 chunk**；③ 该规则针对的 `.ant-pagination` 由**同一个基线组件 SmartTable** 渲染（`SmartTable.tsx:254`），5 个页面全部通过它渲染分页——**作用域本不需要按页面切分**。
- **推荐操作**：**合并**。⚠️ 若收敛到 `SmartTable.css` 并给 `SmartTable.tsx:254` 加固定类，**属基线修改需批准**；**替代方案（不碰基线 JS、代价更低）**：把 5 条合并进 `shared.css`（已被每页 `@import`），用 `.table-card .ant-table-wrapper .ant-pagination` 一条覆盖全部。
- **影响面（视觉回归）**：**中**。`.table-card` 作用域会额外命中"卡片内嵌套第二个 Table"的页面（当前 `searchRender` 自定义查询区没有分页，风险低，但**必须逐页确认**）。**必须目视回归 5 个列表页**。
- **风险等级**：**P2**　**证据可信度**：高（源码逐字比对 + 产物字节偏移一致）　**审批状态**：待确认

#### [FE-CSS-04] `loginlog` 与 `operatelog` 的页面 CSS 整份逐字重复（仅格式不同）

- **位置**：`pages/system/loginlog/index.css:2`（**压缩为 1 行**）vs `pages/system/operatelog/index.css:2-20`（展开 19 行）。
- **问题证据**：① 两文件**全部 5 条规则文本逐字相同**（含 `#536581` 这个硬编码色），差别**只有空白/换行**；② `loginlog/index.tsx:52,55,57` 与 `operatelog/index.tsx:50,56,61-64` 都使用 `.log-strong/.log-mono/.log-time`；③ `role/index.css` 也是同样的"全文件压缩成 1 行"写法 → 说明这是**某次手工压缩的遗留**，而非刻意区分；④ 两份被 Vite 打进不同 chunk，属真实冗余。
  ⚠️ **与 §5 的 FE-CSS-01 是同一处**（前端专项审计与 CSS 专项审计独立发现，结论一致）。**旧审计称两文件"字节完全相同"——hash 已分叉**（`F68606…` vs `A5513F22…`），但**规则集仍相同**。
- **推荐操作**：**合并**（新建 `pages/system/log.css` 或在 `shared.css` 增补 `.log-*` 五条）。⚠️ **注意不要顺手把压缩文件格式化**（`AGENTS.md` §2 基线保护；`role/index.css` 的同类压缩不在本任务范围）。
- **风险等级**：**P2**　**证据可信度**：高　**审批状态**：待确认

#### [FE-CSS-05] intranet 两个字段页的同组规则逐字重复，且窄屏媒体查询**不对称**

- **位置**：`transaction/fields/index.css:5-8` vs `dataasset/fields/index.css:14-17`。
- **问题证据**：① `① .xxx-field-page .ant-table .ant-input{min-height:32px!important}` 与 `② …select-selector{min-height:32px!important}` **除前缀类名外逐字相同**；② 两页都由**同一个 `InlineEditableTable`** 渲染表格（`transaction/fields/index.tsx:208` 与 `dataasset/fields/index.tsx:260` 都挂 `page-fill`）→ 该"表内控件压到 32px"的意图是**组件级而非页面级**；③ `D-012` 已确认 `InlineEditableTable` 是 intranet 内部共享能力，**把这两条下沉到该共享层与 D-012 一致**。
  ⚠️ **另注**：`dataasset/fields/index.css:18-24` 有 `@media (max-width:600px)` 窄屏折叠，而 `transaction/fields/index.css` **没有**对应媒体查询（两页工具栏结构相同）→ **不对称的响应式覆盖**。
- **推荐操作**：**合并**（下沉到 `pages/intranet/components/` 的共享 CSS）。顺带统一两页的窄屏媒体查询（会改窄屏视觉，需目视）。
- **风险等级**：**P2**　**证据可信度**：高　**审批状态**：待确认

#### [FE-CSS-06] `.table-toolbar` 与 `.log-toolbar` 的「共享骨架 + 单属性覆盖」是**跨文件隐式契约**

- **位置**：`shared.css:5-13`（`.table-toolbar` 的 margin/display/justify-content/gap + `> .ant-input-affix-wrapper{width:370px}`）vs `loginlog/index.css:2` / `operatelog/index.css:5-7`（`.log-toolbar{align-items:flex-start}` **仅 1 条声明**）。
- **问题证据**：`.log-toolbar` 的**单条声明必须与 `.table-toolbar` 同时挂在同一元素上才有意义**——实测两者确实同时传入（`loginlog/index.tsx:80`、`operatelog/index.tsx:87` 的 `toolbarClassName='table-toolbar log-toolbar'`），`SmartTable.tsx:249` 把 `toolbarClassName` 原样赋给一个 `div` → **是同元素并列生效，不是覆盖**。但这意味着**"删掉 `.table-toolbar` 的调用"会让日志页工具栏瞬间塌成 `display:block`**——**没有任何注释或类型约束保护**这条契约。
- **推荐操作**：**保留**（功能正确），但**建议加注释**声明"必须与 `.table-toolbar` 同时使用"。
- **风险等级**：P3　**证据可信度**：高　**审批状态**：待确认

#### [FE-CSS-07] 两个组件根类零引用（`.base-chart` / `.code-viewer`），其中 `CodeViewer` 与父级 Card 视觉重复

- **位置**：`components/BaseChart/baseChart.css:1-5`、`components/CodeViewer/codeViewer.css:1-6`。
- **问题证据**：① `BaseChart.tsx:45` 与 `CodeViewer.tsx:86` 都支持 `className` prop，但**全仓库唯一消费点** `home/index.tsx:399,434,458` **均未传**；② `CodeViewer` 的两个使用点都包在 `<Card className='showcase-card'>` 里（`home/index.tsx:432,440`），而 `.showcase-card`（`home.css:10-15`）已有 `border` + `border-radius:12px` → **边框与圆角双重叠加**，`.code-viewer` 的 8px 圆角被嵌在 12px 卡片内。
- **依赖关系**：⚠️ `.code-viewer.is-fullscreen`（`codeViewer.css:18-26`）**以 `.code-viewer` 为父选择器**——若整块删除，全屏规则会连带失效。
- **推荐操作**：**待确认 → 建议保留 `.base-chart`、简化 `.code-viewer` 的 border/radius**。删除会让"未包 Card 时"失去外框，但**当前无此用法**（无证据支持删除，故不推荐删）。
- **风险等级**：P3　**证据可信度**：高（调用点已穷举）　**审批状态**：待确认

#### [FE-CSS-08] 字号阶梯存在 `13px` 硬编码，**绕过**了"只改 `--fs-base` 全站联动"的设计

- **位置**：`components/SmartTable/SmartTable.css:19`（`.smart-table-search-field{font-size:13px}`）、`pages/intranet/transaction/fields/index.css:11`（`.transaction-field-filter-count{font-size:13px}`）。
- **问题证据**：① `theme.css:36` 定义 `--fs-sm: calc(var(--fs-base) - 1px)`，在默认 `--fs-base:14px` 下**恰好等于 13px**；② `ThemeProvider.tsx:113` 会把 `--fs-base` 写成 `12~16px` → 用户把字号调成 16 时 `--fs-sm` 变 15px，而**这 2 处仍是 13px，不跟随**；③ 同类正确写法在仓库里占绝对多数（`var(--fs-sm)` 19 处、`var(--fs-lg)` 9 处、`var(--fs-xl)` 6 处），`reset.css:12-17` 的注释**明确要求**统一用 `--fs-*` 阶梯 → **这 2 处是漏网**；④ 同位置的 `iconPicker.css:71,102` 的 `11px` 与 `:63` 的 `20px` **没有对应档位**。
- **推荐操作**：**简化**——`13px` → `var(--fs-sm)`（**默认值下零视觉变化**，这是本项最容易安全落地的一条）；`iconPicker.css` 的 `11px/20px` **建议保留**（无对应档位，强改会改视觉）。
- **风险等级**：P3　**证据可信度**：高　**审批状态**：待确认

#### [FE-CSS-09] `CodeViewer` 全屏高度用 `calc(100vh - 46px)` 魔法数字

- **位置**：`codeViewer.css:25` 与 `:8`（`.code-viewer-toolbar{min-height:46px}`）。
- **问题证据**：`46` 在两处各写一次、**无一处在另一处引用**（无 `--code-viewer-toolbar-h` 变量）；工具栏是 **`min-height`（可增长）**，一旦按钮换行或加高，46 立刻失准——**这正是 D-031 明文批判的 `calc(100vh - N)` 同型问题**。
  ⚠️ **但必须区分并澄清**：D-031 的决策范围**明确限定**为 `.app-content` 内的页面级滚动（「影响范围：… `frontend/src/pages/intranet/{transaction,dataasset}/fields/index.{tsx,css}` … **未修改其它任何页面**」），`CodeViewer` 全屏是 **`position` 上的覆盖层、不参与 `.app-content` 高度链**，**不属于 D-031 的迁移对象，不构成"违反 D-031"**。
- **推荐操作**：**保留**（当前行为正确且取值与工具栏一致），或简化为 `calc(100vh - var(--code-viewer-toolbar-h))`。
- **风险等级**：P3　**证据可信度**：高　**审批状态**：待确认

#### [FE-CSS-10] 硬编码颜色：同一语义色在多个文件重复，且部分颜色**在深色主题下失效**

- **位置（按重复次数）**：
  - `#1677ff` **3 处**：`login.css:248`（`.login-unified-button:hover` 的 `color`）、`register.css:32`（兜底）、`theme.css:24`（兜底，合法）
  - `#07c160` / `#24292f` / `#ea4335` **各 3 处**：`login.css:219-228`（**硬编码**）与 `security.css:8-10`（**已变量化为 `--provider-*`**，合法）
  - `#e7edf8` 3 处（`codeViewer.css:3,13` 兜底 + `theme.css:56` 合法）；`#eaf3ff` 2 处（`login.css:5`、`setup.css:8`）；`#112a5d` 2 处（`login.css:112`、`setup.css:60`）；`#536581` 2 处（两个日志 `index.css` 的 `.log-mono`，与 FE-CSS-04 同源）
- **问题证据**：
  1. ⚠️ **`security.css` 已建立正确模式**（`:7-11` 把三个平台色定义为作用域变量 + `:12-14` 提供 `[data-theme='dark']` 覆写，注释明确说明「GitHub 的品牌色是近黑，**在深色主题下等于不可见**」）；而 **`login.css:218-233` 把同一组品牌色再硬编码一遍，且没有深色主题覆写** → **深色主题下登录页 GitHub 圆形按钮 hover 成 `#24292f`（近黑）在深色背景上不可见**。**这是 `security.css:4-7` 注释里描述的同一个 bug 在登录页的未修复副本。**
  2. `#536581`（`.log-mono`）与 `fieldLabel.css:7` 的 `#9aabc4`、`notifications/index.tsx:110` 的 `#aeb8c8` 都是"介于 `--muted` 与 `--ink` 之间的中蓝灰"，属**近似但不等**的散落灰阶；深色主题下 `#536581` **反而比正文更暗**。
- **依赖关系**：⚠️ `theme.css` 顶部注释明确"品牌色由 `ThemeProvider` 用 inline style 覆盖，**改 `theme.css` 里的品牌色没有效果**"→ 修 `login.css:248` 应改为 `var(--brand)`。
- **推荐操作**：**合并/变量化**——(a) `login.css:218-233` 改为引用 `--provider-*` 并**补 `[data-theme='dark']` 覆写（修掉深色下 hover 不可见）**；(b) `login.css:248` → `var(--brand)`；(c) `.log-mono` 的 `#536581` → `var(--muted)`（**会改视觉**）；(d) `login.css` 内部重复色提为局部变量。
- **影响面（视觉回归）**：**中**。(a) **会改变深色主题下登录页社交按钮 hover 颜色**（这正是修复目标）；(c)(d) 会改变浅色观感。**必须目视**：登录页（浅 + 深 × hover/disabled 四态）、两个日志页 `.log-mono`、账号安全页三种身份卡（浅 + 深）。
- **风险等级**：**P2**（其中**深色主题下 GitHub hover 不可见为 P1 缺陷**，因仅 hover 时可见而整体降为 P2；**若按缺陷单列应记 P1**）　**证据可信度**：高（全仓库颜色字面量聚合 + `security.css` 的既有修复形成**对照证据**）　**审批状态**：待确认

#### [FE-CSS-11] `theme.css` 用 `#fff` 兜底语义色，深色主题下会**白闪**

- **位置**：`baseChart.css:15`（`var(--surface, #fff)`）、`codeViewer.css:5`（同）。
- **问题证据**：① `theme.css:58,60` 已定义 `--surface`（亮 `#fff` / 深 `#151f31`）→ `var()` **始终有值，兜底永不触发**，是**死分支**；② 但兜底值 `#fff` 是**亮色语义**：一旦 `@import` 顺序被改或变量名被重命名，这两处会**静默回退到白底，在深色主题下闪现白块**；③ 同类正确写法见 `loadingScreen.css:14-18` 的注释（「曾经这里是三个写死的浅蓝渐变…切深色主题会闪一块白」）→ **团队已识别此类风险，这两处是漏改项**。
- **推荐操作**：**简化**——删除兜底值（`var(--surface)`）或改为深色安全的 `color-mix`/`transparent`。**当前零视觉变化**。
- **风险等级**：P3　**证据可信度**：高　**审批状态**：待确认

#### [FE-CSS-12] `.ant-*` 覆盖清单：**49 条覆盖中 46 条为必要修补，未发现"因 antd 5 支持 token 而变冗余"的历史覆盖**

- **必要修补（46 条，建议保留，均能指出对应的 antd token 缺口）**：布局/结构类（`.ant-layout`、`.app-sider .ant-menu*`、`.ant-layout-sider-collapsed`——`layout.css:49-52` 的注释准确解释了为何需要 `!important`）；弹窗外壳（`.system-dialog .ant-modal-*` + `.ant-form*`——`shared.css:30-36` 说明 antd 的 `contentPadding`/`headerPadding` token 会与三段 padding 叠加故必须清零重排，`shared.css:75-77` 说明 `labelWrap` 需放开 antd 写死的 32px）；表格/分页（见 FE-CSS-03/05，`.ant-table-tbody .ant-table-cell .ant-space{vertical-align:middle}` 的注释解释了 inline-flex 基线问题）；以及 `.ant-input-affix-wrapper`、`.ant-tree`、`.ant-list-item`、`.ant-divider-horizontal`、`.ant-popover-inner`、`.ant-card*`、`.ant-collapse*`、`.ant-statistic*`、`.ant-btn*`、`.ant-typography`、`.ant-tabs-nav*`、`.ant-breadcrumb`、`.ant-tag` 等。
- **与 antd 5 token 能力重复（3 条，判定为"保留 + 补注释"）**：
  1. `SystemSettings/systemSettings.css:28-37` 的 `.theme-color-swatch` 写死 `border-radius:50%`——该文件 `:17-18` 的注释**自己说明**"取色器的圆角由 AntD 的 `borderRadius` token 统一控制，这里不要写死"。**圆形色板是刻意的视觉设计**（token 会给 8px）→ **保留，但建议注释说明"圆是刻意的，不是遗漏"**。
  2. `login.css:167-172` 的 `.ant-btn-lg` 用 `clamp(56px,6.6vh,64px)` 高度——token **无法表达 `clamp()`**（`:104-107` 注释解释了矮屏/高屏诉求）；`border-radius:10px` 与全局 `borderRadius:8` 不一致，属**页面级刻意偏离** → **保留**。
  3. `security.css:32-33` 的两条 `!important` 是为盖 antd `Divider` 默认字号，**必须 CSS** → **保留**。
- **依赖关系**：⚠️ 这些覆盖的选择器特异性普遍为 (0,2,0)~(0,3,0)，**低于** antd 运行时注入的 `:where()` 包裹规则（antd 5 的 `cssVar` 模式用 `:where()` 降权，`ThemeProvider.tsx:147` 已启用 `cssVar:{key:'app'}`）——**这是这些覆盖能生效的前提**。**若将来关闭 `cssVar`，部分无 `!important` 的覆盖会失效。**
- **推荐操作**：**保留**（46 条）／**待确认**（3 条仅补注释）。
- **影响面**：**无**（不改动）。
- **风险等级**：P3　**证据可信度**：**中高**（每条的"必要性"判断基于 antd 默认行为与源码注释的交叉验证；**未实际修改 DOM 逐一验证 antd 5 的每条默认值**，属静态审计的固有限度）　**审批状态**：待确认

#### [FE-CSS-13] `.icon-picker-popover` 的 `min-width:460px` 与内部面板 `width:min(460px,100vw-32px)` 双写同一尺寸

- **位置**：`iconPicker.css:1-3` 与 `:10-12`。
- **问题证据**：① `460px` 两处各写一次；② ⚠️ **窄屏路径已处理**：`:105-108` 的 `@media (max-width:520px){min-width:0}`（源序更后、同特异性）**成功压掉** `min-width` → **不是缺陷**；③ 风险仅在"492~520px 临界带"（`min-width:0` 未生效而面板已按 `100vw-32px` 收缩）→ **当前无证据表明实际溢出**（**未做浏览器实测**，属未验证推测）。
- **推荐操作**：**保留**，可选简化为 `min-width:0` 并完全交给 `__panel` 的 `min()`。
- **风险等级**：P3　**证据可信度**：**中**（媒体查询逻辑已确认；临界带溢出为**未验证的推测**）　**审批状态**：待确认

#### [FE-CSS-14] 媒体查询断点分散为 **8 个不同值、共 10 处**

- **位置与断点**：`520px`（`iconPicker.css:105`）、`600px`（`shared.css:214`、`dataasset/fields/index.css:18`）、`640px`（`security.css:81`）、`650px`（`setup.css:99`）、`700px`（`global.css:139`、`home.css:107`）、`720px`（`notFound.css:77`）、`800px`（`login.css:271`、`SmartTable.css:22`）、`900px`（`layout.css:174`、`AiAssistant.css:333`）。
- **问题证据**：① `layout.css:174-199` 在 **900px** 把侧边栏收成图标条，而 `shared.css:214` 在 **600px** 才折叠工具栏 → **在 600~900px 区间，侧边栏已经收窄但表格工具栏仍是横向 flex**，`shared.css:11-13` 的 `.table-toolbar > .ant-input-affix-wrapper{width:370px}` 在变窄的内容区里可能挤压；② `transaction/fields/index.css` **完全没有媒体查询**，而 `dataasset/fields/index.css:18-24` 有（见 FE-CSS-05）。
- **推荐操作**：**待确认**（统一断点会改动多处窄屏视觉，收益/风险比不佳）。务实做法：只补两个字段页的对称媒体查询（已并入 FE-CSS-05）。
- **风险等级**：P3　**证据可信度**：**中**（断点清单为实测；600~900px 的挤压为**未实测的推断**）　**审批状态**：待确认

### 6.4 内联 style 与公共样式重复

**结论：不存在「内联 style 重写了某个公共类已有属性」的硬重复**（29 处 `style={{…}}` 逐条对比后无命中）。

真正的可优化点：
- **7 处重复的 `style={{width:'100%'}}`**（`DeptDialog:135`、`MenuDialog:127`、`NewTransactionDetailDialog`、`RequirementDialog:84`、`TransactionCallDetailDialog:48`、`TransactionChangeDetailDialog:38`、`SystemSettings:83`）——项目里没有 `.w-full` 之类工具类；抽公共类属**基线新增，需批准**。P3。
- **`notifications/index.tsx:108,110` 的硬编码颜色**（`rgba(22,119,255,.035)`、`#aeb8c8`）**不随 `--brand`/`--muted` 变**（见 FE-CSS-10）。
- **正例（职责分离清晰，可作为其余条目的参照）**：`home/index.tsx:377` 的 `mini-status-icon` + 内联 `color`——公共样式管字号、内联只管颜色。
- **正确用法的内联**：`SystemSettings:53,88`（值来自用户偏好）、`home/index.tsx:372,377`（ECharts/Canvas 需求）、`DynamicPage:62`（`display:none` 路由缓存）。

### 6.5 ⚠️ D-031 迁移的专项核验结论：**已完成且干净**

- 旧写法 `.table-card{min-height:calc(100vh - 240px)}` 在 `transaction/fields` 与 `dataasset/fields` **已彻底移除**；
- `page-fill` 已挂（`transaction/fields/index.tsx:208`、`dataasset/fields/index.tsx:260`）；
- `.card-scroll` 由 `InlineEditableTable.tsx:56` **自带**（与 D-031「自带 `.card-scroll` 容器」一致）；
- `global.css:40-90` 的约定注释与实现一致；
- 全仓库 `calc(100vh` 现存 **4 处**：`codeViewer.css:25`（见 FE-CSS-09，**不属 D-031 范围**）、`shared.css:60`（弹窗内容区上限，有 `min()` 三条件保护，**合理**）、`AiAssistant.css:194`（原始 JSON 抽屉上限，**合理**）、`global.css:41`（注释文字）。

**逐条比对 `D-012`/`D-025`/`D-029`/`D-031` 后，未发现与已确认决策不一致的残留**，故本节**未就这些决策重复报告任何"问题"**。

### 6.6 该模块的「未检查范围与残余不确定性」

- **未执行构建/打包**：所有层叠结论来自**现有 `dist/` 产物**（时间戳 2026-10-12）。若源码在该产物之后有改动，产物偏移可能与当前源码不完全对应——但每处冲突都**同时**用源码行号与产物偏移双向确认。
- **未做浏览器实测**：D-031 明确「布局效果构建验证不出，必须按 `HANDOFF.md` §4 清单在浏览器实测」，本次为只读审计，**未执行任何目视回归**。所有涉及视觉变化的建议均已标注"必须目视"。具体未验证项：① `page-fill`/`card-scroll` 在极矮视口下的降级表现（D-031 已登记为已知风险）；② 两个字段页 `fixed:'right'` 操作列与 antd `sticky` 表头的 z-index 层级（D-031 已登记需目视）；③ `icon-picker-popover` 在 480~560px 的临界带（FE-CSS-13）。
- **未验证 antd 5 内部实现的每条默认值**：FE-CSS-12 的"必要修补 vs token 可替代"判断基于 antd 5 公开行为与本仓库注释交叉验证，**未通过 DevTools 逐条比对** → 该项可信度标为**中高**。
- **未检查** `node_modules/**`（含 antd 样式、nprogress、monaco 的 CSS）、`npm-offline/**`、antd `ConfigProvider` 的 `components` 级 token（本仓库未使用）、ECharts/G6 的 Canvas 内联样式。
- **未检查 Git 历史与已删除文件**：因此"历史样式残留"的判定依据是**当前源码内自相矛盾的证据**（如 `security.css:28-30` 自己记录 `--text-strong` 已废、`loadingScreen.css:14-15` 与 `index.html` 的双份实现漂移），而非提交历史。
- **残余不确定性**：FE-CSS-02 中"给 `index.html` 加 class 后首屏视觉不变"的推论**基于特异性计算**（`#app-loading` (1,0,0) > `.app-loading` (0,1,0)），**未经浏览器验证**；FE-CSS-14 的 600~900px 挤压、FE-CSS-13 的临界带溢出均为**静态推断**。

> **本节汇总**：14 条问题——**P1 两条**（FE-CSS-01 未定义变量、FE-CSS-02 首屏双实现选择器不匹配）、**P2 四条**（FE-CSS-03/04/05 重复样式、FE-CSS-10 硬编码色 + **深色主题下 GitHub hover 不可见**）、**P3 八条**。
> **没有任何一条建议"删除"**——最激进的操作是"合并"，且**全部标注了必须浏览器目视回归**。

---

## 7. 无效代码与依赖清单（已完成部分）

### 7.1 审计方法与「无效」的判定标准

本节的判定**不依赖单纯文本搜索**。每条结论都额外排除了以下动态使用方式：

- Spring 组件扫描（`@Component` / `@Service` / `@Configuration` / `@Mapper`）
- MyBatis 注解 SQL 与 Mapper 接口（`@Select` / `@MapperScan`）
- `application*.yml` 属性绑定（`@ConfigurationProperties`）与 `logging.level.*` 日志器名
- 前端 `import.meta.glob` 动态页面扫描与后端 `sys_menu.component` 配置驱动
- 前端模板字符串拼接的类名（如 `` `social-${provider.key}` ``）
- 构建脚本、打包脚本、CI/文档引用

### 7.2 已确认的问题

#### [CFG-01] `frontend/package.json` 声明了从未使用的依赖 `html2canvas`

- **位置**：`frontend/package.json:23`；已核实 `frontend/src/**` 与 `vite.config.ts` 中 `html2canvas` **0 命中**（全仓库仅 `package.json` 与 `package-lock.json` 命中）。
- **当前实现的作用**：声明了一个前端运行时依赖，无任何代码消费。
- **判断证据**：对 `html2canvas` 做全仓库检索，`src` 下 0 命中；`package-lock.json` 中它是被普通依赖解析出来的（不会被其他依赖间接使用，`html2canvas` 无下游依赖者）。
- **依赖关系**：无代码依赖。删除需同时 `npm install` 刷新 lock（这属于修改阶段的事）。
- **推荐操作**：删除（安装依赖时同步清理）。
- **影响面**：无极小——不打包即不进入产物，删除只影响 `node_modules` 与 lock 体积。*若将来要做"导出为图片"再按需加回。*
- **风险等级**：P0　**证据可信度**：高　**审批状态**：待确认

#### [CFG-02] `frontend/pack-incremental.mjs` 是无任何引用的构建脚本

- **位置**：`frontend/pack-incremental.mjs`（已 `git ls-files` 确认**入库**）。
- **当前实现的作用**：把 npm 包打成离线 tarball，维护 `npm-offline/state.json`、`pending.json`，用于内网离线安装依赖。
- **判断证据**：`package.json` 的 `scripts` 只有 `dev` / `build` / `preview`，**没有**引用它；`vite.config.ts`、`tsconfig*.json`、`docs/**`、`AGENTS.md` 中 `pack-incremental` **0 命中**；仓库根与 `frontend` 下**无 CI 配置文件**（无 `.github/workflows`）。它只能被人工 `node pack-incremental.mjs` 调用。
- **依赖关系**：无代码依赖；它自己依赖系统 `tar` 命令与 `npm-offline/`（`npm-offline/` 已被 `.gitignore` 忽略）。
- **推荐操作**：**待确认**——如果内网离线部署**仍然依赖**这个手工流程，它就必须保留（属于"真实存在但不在构建流水线里"的运维脚本）；如果内网部署方式已经改为内网 Nexus/私服，则可删。
- **影响面**：删除只影响人工离线打包流程，不影响 `npm run build`。
- **风险等级**：P2　**证据可信度**：高（"无引用"是事实）／"是否还需要"是业务问题　**审批状态**：待确认

#### [CFG-03] `frontend/.prettierrc.json` 是「配置存在但工具不存在」的孤儿配置

- **位置**：`frontend/.prettierrc.json`（已入库）。
- **当前实现的作用**：声明 Prettier 格式规则（`printWidth: 200`、`singleQuote` 等）。
- **判断证据**：`frontend/package.json` 的 `dependencies` 与 `devDependencies` 中**没有** `prettier`；全仓库（排除 `node_modules`）**不存在** `.eslintrc*`、`eslint.config.*`、`.prettierrc*` 之外的第二份配置，也**没有** lint/format 脚本。因此这份配置在任何流水线里都不会生效。
- **依赖关系**：无。注意 `SmartTable.tsx` 等文件当前存在**用户本地的格式化改动**（见 `HANDOFF.md` §0.1），说明确实有人在本地跑过格式化工具——但工具不在仓库声明里。
- **推荐操作**：二选一，**待确认**：① 删除该文件；② 补 `prettier` 到 `devDependencies` + 加 `format` 脚本，让配置真正生效。二者都不是「清理」，需要你决定团队的格式约定。
- **影响面**：仅影响开发体验，不影响构建与运行（`tsc --noEmit` 不做格式检查）。
- **风险等级**：P2　**证据可信度**：高　**审批状态**：待确认

#### [CFG-04] 前端 3 处 `eslint-disable` 注释指向不存在的 ESLint

- **位置**：`frontend/src/components/ai/useAgentChat.ts:73`（`no-constant-condition`）、`frontend/src/pages/register/index.tsx:89`、`frontend/src/pages/intranet/dataasset/AssetDialogs.tsx:20`（`react-hooks/exhaustive-deps`）。
- **当前实现的作用**：抑制 lint 告警。
- **判断证据**：项目无 `eslint` 依赖、无 eslint 配置、无 lint 脚本（同 CFG-03 的检索）。这三行是**空操作**。
- **依赖关系**：无。
- **推荐操作**：**待确认**——如果这些注释是"给未来接 ESLint 时留的说明"，建议改为普通注释并写明原因（例如"此处依赖数组故意留空，原因：xxx"），保留信息量、去掉失效指令。
- **影响面**：无运行时影响。
- **风险等级**：P0（就"无效指令"而言）／但涉及**注释语义**，建议按上句改写而非直接删　**证据可信度**：高　**审批状态**：待确认

#### [CFG-05] `application-local.yml` 的 `logging.level.org.kariya` 指向不存在的包

- **位置**：`backend/src/main/resources/application-local.yml`（`logging.level` 下 `org.kariya: DEBUG`）。
- **当前实现的作用**：意图开启本项目业务包的 DEBUG 日志。
- **判断证据**：全仓库 `org.kariya` **0 个包**——所有后端源码的包名都是 `org.lbl`（`package org.lbl...` 计数 > 0，`org.kariya` 计数 = 0）。因此这条日志级别**永远匹配不到任何日志器**，业务 DEBUG 日志实际不会输出。同一配置在 `application-dev.yml` 中写的是 `org.lbl: DEBUG`（正确），说明这是**项目改名后的遗留**。
- **依赖关系**：无代码依赖，但**有真实运行影响**：local profile 下调试日志开关失效。
- **推荐操作**：改成 `org.lbl: DEBUG`（对齐 dev），或删除该行。
- **影响面**：修正后 local profile 会开始输出 `org.lbl` 的 DEBUG 日志（日志量上升，这是原本的意图）。
- **风险等级**：P0（明确的无效配置项）　**证据可信度**：高　**审批状态**：待确认

#### [CFG-06] `application-dev.yml` 与 `application-local.yml` 存在整段重复配置

- **位置**：`backend/src/main/resources/application-dev.yml` 与 `application-local.yml` 的
  `server.port`、`logging.pattern.console`、`logging.level.*`（除 `org.kariya`/`org.lbl` 差异）、`mybatis-plus.configuration.log-impl`、`spring.datasource.driver-class-name` 全部逐字相同。
- **当前实现的作用**：两个 profile 各自声明同一套运行参数。
- **判断证据**：逐行比对两文件，上述键值完全一致；`application.yml`（主配置）中没有这些项，因此不能靠"提到主配置"消除，只能上移到 `application.yml` 或抽 `spring.config.import`。
- **依赖关系**：无代码依赖。`application-local.yml` **已被 `.gitignore` 忽略且未入库**（`git ls-files --error-unmatch` 报 pathspec 不匹配），`application-dev.yml` **已入库**。
- **推荐操作**：**待确认**——可把公共部分上移到 `application.yml`；但注意 `application-local.yml` 是你的**本机私有配置**，改动它不会影响仓库，收益仅限于本机可读性。若你更看重"每个 profile 自解释"，保留也无害。
- **影响面**：上移配置会改变所有 profile 的配置来源，需要一次真实启动验证。
- **风险等级**：P2（重复配置，无功能缺陷）　**证据可信度**：高　**审批状态**：待确认

#### [CFG-07] `application-local.yml` 与多份文件的注释存在中文乱码（编码损坏）

- **位置**：`backend/src/main/resources/application-local.yml`（`hikari` 段与 `lbl.security` 段的大段中文注释）、`application-prod.yml`、`.gitignore`、`frontend/pack-incremental.mjs`。表现为 `淇濇寔鑷冲皯 5 涓┖闂茶繛鎺?` 这类乱码。
- **当前实现的作用**：注释本意是解释连接池参数、Secure Cookie、可信代理等关键取舍；现在已不可读。
- **判断证据**：乱码是 UTF-8 字节被按 GBK 重新解码的典型形态（"保持至少 5 个空闲连接" → `淇濇寔鑷冲皯...`）。**这些是注释，不影响运行**；但 `application-local.yml` 里的注释包含「生产用 https 时必须把 secure-cookie 置为 true」这类安全提示，不可读会削弱其作用。
- **依赖关系**：无。
- **推荐操作**：**待确认**——按文件重新以 UTF-8 写回注释原文（内容可从 `application-dev.yml` 的同义注释与 `.gitignore` 上下文还原）。属于**文档/可读性修复**，不是代码精简。
- **影响面**：无功能影响；改动 `application-local.yml` 只影响本机。
- **风险等级**：P2（可读性与安全提示丢失）　**证据可信度**：高　**审批状态**：待确认

#### [CFG-08] 端口/应用命名在多处并存三套口径

- **位置**：`backend/pom.xml`（`artifactId=lbl-shit`）、`application.yml:3`（`spring.application.name=lbl-shit`）、`frontend/package.json:2`（`name=lbl-shit-frontend`）；对照 `backend/src/main/resources/db/init_schema.sql:1`、`init_data.sql:1`（"Kariya Admin"）、`frontend/src/layouts/AppLayout.tsx:239`（"Kariya的个人开发工作台"）、`frontend/src/pages/home/index.tsx`（示例中的 `kariya-admin` / `kariya_admin`）、`backend/.../RealtimeEventPublisher.java:11`（Redis 通道 `kariya-admin:realtime:events`）、`AgentProperties.java:31`（User-Agent `KariyaAdmin-Agent/1.0`）。
- **当前实现的作用**：项目标识字符串。
- **判断证据**：命名体系存在 `lbl-shit`、`Kariya Admin`、`kariya-admin`、`lbl` 四套写法同时出现。
- **依赖关系**：⚠️ **Redis 通道名 `kariya-admin:realtime:events` 不能单独改**——它是发布/订阅双方约定的字符串；改名必须同时改发布方与订阅方，且**部署期间新旧实例混跑会导致实时事件丢失**。`spring.application.name` 影响 Actuator/日志展示。Maven `artifactId` 改名会改变产物 jar 名与部署脚本。
- **推荐操作**：**待确认（产品/命名决策）**——建议本轮**只记录不改**，或仅统一**面向用户的名称**（`AppLayout` 文案、数据库脚本头注释）。改名收益低、牵连部署。
- **影响面**：涉及部署脚本、Redis 通道、日志检索与监控。
- **风险等级**：P3（跨实例通道与部署契约）　**证据可信度**：高　**审批状态**：待确认

### 7.3 已核实**不是**问题的项（避免后续会话误判）

| 项 | 结论 |
| --- | --- |
| `backend/target/`、`frontend/dist/`、`frontend/node_modules/`、`frontend/npm-offline/` | 均已被 `.gitignore` 忽略（`git check-ignore` 确认），非仓库内容，不属审计范围 |
| `mybatis-plus-jsqlparser` 依赖 | **在用**：`config/MybatisConfig.java:5-21` 引入 `PaginationInnerInterceptor`，注释明确说明 MyBatis-Plus 3.5.9 起该分页插件被拆到此模块 |
| `spring-boot-starter-actuator` 依赖 | **在用**：`application.yml:27-39` 暴露 `health,info`，`config/WebSecurityConfig.java:49` 放行 `/actuator/health` |
| `poi-ooxml` 依赖 | **在用**：`common/util/ExcelExportUtil.java`、`system/user/service/UserExportService.java` |
| `aspectjweaver` 依赖 | **在用**：`system/log/aspect/OperationLogAspect.java`（`@OperationLog` 切面） |
| `jasypt-spring-boot-starter` | **在用**：`application-local.yml` 的 `ENC(...)` 值，以及 `src/test/java/org/lbl/JasyptEncryptorTest.java` |
| `backend` 无 Maven Wrapper | 事实，不是缺陷；但意味着**构建依赖本机 Maven**（`HANDOFF.md` 记录当前机器未发现 Maven） |
| `pages/**/index.tsx` 无人 import | **设计使然**：`import.meta.glob` 扫描 + `sys_menu.component` 配置驱动（`componentRegistry.tsx:19,45-58`） |
| `docs/intranet/*` 的"待确认"条目 | 属于**有意保留的规划项**，不是无效文档；不在本报告建议删除范围内 |

---

#### [CFG-09] `pages/**` 下有 16 个组件型 `.tsx` 没有 `export default`，会被动态页面扫描收录为「菜单组件」候选，一旦被选中将导致页面崩溃

- **位置**：`frontend/src/router/componentRegistry.tsx:19`（`import.meta.glob(['../pages/**/*.tsx', ...])`）与
  `:64-68`（`availableComponents()`），消费者是 `frontend/src/pages/system/menu/MenuDialog.tsx`（「前端组件」下拉）。
  当前命中但不含 `export default` 的文件（实测逐个检查 `export default` 关键字）：
  ```text
  pages/intranet/components/FieldText.tsx
  pages/intranet/components/InlineEditableTable.tsx
  pages/intranet/dataasset/AssetDialogs.tsx
  pages/intranet/requirement/NewTransactionDetailDialog.tsx
  pages/intranet/requirement/RequirementApprovalDetailModal.tsx
  pages/intranet/requirement/RequirementDetailDrawer.tsx
  pages/intranet/requirement/RequirementDialog.tsx
  pages/intranet/requirement/TransactionCallDetailDialog.tsx
  pages/intranet/requirement/TransactionChangeDetailDialog.tsx
  pages/intranet/transaction/DataResourceDialog.tsx
  pages/intranet/transaction/TransactionDialog.tsx
  pages/system/dept/DeptDialog.tsx
  pages/system/menu/MenuDialog.tsx
  pages/system/role/RoleDialog.tsx
  pages/system/role/RolePermissionDialog.tsx
  pages/system/user/UserDialog.tsx
  ```
- **当前实现的作用**：这些是业务对话框/共享组件，本不该出现在页面对话框里。
- **判断证据**：`import.meta.glob` 的模式是 `**/*.tsx`（**不是** `**/index.tsx`），因此它会把上述 16 个文件也收进 `modules` 映射并出现在 `availableComponents()` 结果中。而 `resolveMenuComponent`（`componentRegistry.tsx:45-58`）只在 `modules[key]` **不存在**时返回 `null`（→ 渲染 `PageUnavailable` 诊断页）；这 16 个文件的键**存在但没有 default 导出**，`lazy()` 解析出的模块 `default` 为 `undefined`，React 渲染时会抛 "Element type is invalid"（白屏报错），而**不是**走进那个友好的诊断页。这与该模块注释宣称的设计意图（`:43,62`「解析不到时渲染 PageUnavailable，而不是静默显示 404」「不会配出一个渲染不出来的菜单」）相矛盾。
- **依赖关系**：⚠️ 涉及**基线路由基础设施**（`AGENTS.md` §2 要求先申请批准）。当前**只是潜在风险**：只要菜单配置规范要求只选 `index.tsx`，就不会实际触发（`ARCHITECTURE.md` §2 已记录 2026-10-11 你决定"暂不修改扫描规则，由菜单配置操作规范规避"）。但内网需求模块本轮新增了 5 个 `.tsx`（`RequirementDialog`、`RequirementDetailDrawer`、`RequirementApprovalDetailModal`、`TransactionCallDetailDialog`、`TransactionChangeDetailDialog`），把候选列表从 11 个扩大到 16 个——**风险面在扩大**。
- **推荐操作**：**待确认**（三种改法，互斥）：
  1. glob 收敛为 `**/index.tsx`——全仓库 9 个菜单 `component` 值都以 `/index` 结尾（已核对 `init_data.sql:28-35` 与 `db/intranet/V2/V6/V9/V11/V14`），该约定是安全的；属**基线修改**需批准。
  2. 在 `resolveMenuComponent` 里校验「loader 解析后没有 default 就返回 null」——也是基线修改，但**改动最小**且能让诊断页真正生效。
  3. 不改代码，靠菜单操作规范规避——风险自担，且每次新增组件型 tsx 都会扩大。
- **影响面**：改法 1/2 都触及动态路由基线，需要回归**所有**菜单页面的加载（`/home`、6 个 system 页、4 个 intranet 页 + 2 个隐藏 fields 页）。
- **风险等级**：P1（症状是白屏崩溃；当前靠规范规避）　**证据可信度**：高（文件清单与 glob 模式是事实）／中（"选中即崩"是从 `componentRegistry.tsx:45-58` 控制流**静态推导**，未实际构建复现）　**审批状态**：待确认
- **备注**：仓库历史中曾有一份前端专项审计（`frontend-deadcode-audit.md`，现已从工作区删除、仍在 HEAD）记录过同一问题（其 #52 条目，当时为 10 个文件）；本条目是在**当前代码**上重新核实的更新版。

#### [CFG-10] `frontend/tsconfig.app.json` 未开启 `noUnusedLocals` / `noUnusedParameters`，导致死导入不会被构建发现

- **位置**：`frontend/tsconfig.app.json`（`compilerOptions` 中没有 `noUnusedLocals`、`noUnusedParameters`）；`npm run build` = `tsc --noEmit` + `vite build`。
- **当前实现的作用**：类型检查不包含"未使用变量"规则。
- **判断证据**：因为缺少这两条规则，下面这些**真实的死代码通过了 `tsc --noEmit`**（全部由我逐文件核实）：
  | 位置 | 内容 |
  | --- | --- |
  | `frontend/src/pages/system/menu/index.tsx:5` | `MenuOutlined` 从 `@ant-design/icons` 导入后**全文再无使用** |
  | `backend/src/main/java/org/lbl/auth/controller/ExternalAuthController.java:8` | `import org.lbl.auth.session.LoginSession;` 全文仅导入行出现一次（Java 侧同样无 lint 门禁） |
  - 说明：`frontend/src/types/table.ts:36` 的 `export interface SmartTableSelection<T>` 全仓库 0 引用（类型导出不会被 `tsc` 报为未用）。
- **依赖关系**：无。
- **推荐操作**：**待确认**——这是**工程门禁决策**而非代码精简：开启后需先清理现存死导入（上表 3 处），并确认不会因第三方类型声明产生噪音。也可选择不开启，改用 ESLint（见 CFG-03，当前也没有）。
- **影响面**：开启 `noUnusedLocals` 会让 `npm run build` 对未使用变量报错——**可能打断当前构建**，必须先清理。属于"改一处影响全仓构建"的项，建议单独一批执行。
- **风险等级**：P2　**证据可信度**：高（配置事实与 3 处实例均已核实）　**审批状态**：待确认

#### [CFG-11] 8 个 `.css`/`.yml`/`.mjs` 文件存在中文注释乱码（编码损坏）

- **位置与实例**（每个文件都反复出现，举例）：
  | 文件 | 乱码样例 | 还原后的原意 |
  | --- | --- | --- |
  | `frontend/src/pages/system/shared.css`（第 1 行起大段） | `绯荤粺绠＄悊妯″潡鍏变韩鏍峰紡` | 「系统管理模块共享样式」 |
  | `frontend/src/pages/system/user/index.css:2` | `寮圭獥澶栧３` | 「弹窗外壳」 |
  | `frontend/src/pages/login/login.css` 多处 | `璁剧疆涓?5鍒嗛挓` 类形态 | 「设置为 15 分钟」等 |
  | `frontend/pack-incremental.mjs`（第 1 行起） | `鍘嬬缉鍖呬腑鏈彂鐜板敮涓€鐨勯《灞?package.json` | 「压缩包中未发现唯一的顶层 package.json」 |
  | `backend/src/main/resources/application-local.yml` | `鏍稿績杩炴帴姹犻厤缃?` | 「核心连接池配置」 |
  | `backend/src/main/resources/application-prod.yml` | `鐢熶骇璧?https` | 「生产走 https」 |
  | `.gitignore` | `鍓嶅悗绔垎绂婚」鐩?` | 「前后端分离项目」 |
- **当前实现的作用**：注释解释关键取舍（滚动条/层叠/连接池参数/Cookie Secure 要求等）。
- **判断证据**：乱码形态是 **UTF-8 字节被按 GBK 解码**的典型结果（"保持至少 5 个空闲连接" → `淇濇寔鑷冲皯 5 涓┖闂茶繛鎺?`）。**不影响编译与运行**（注释与字符串字面量之外的代码都是 ASCII），但 `application-prod.yml` 里那句「生产走 https，刷新 Cookie 必须带 Secure」的安全提示已经不可读。
- **依赖关系**：无。
- **推荐操作**：**待确认**——按 UTF-8 重新写回注释原文。注意：⚠️ **只重写注释文本，不要动任何配置键值**；`application-local.yml`、`.gitignore` 里有部分乱码出现在**字符串值**之外的注释行，但也有文件（如 `pack-incremental.mjs`）的乱码在 **catch 的异常消息字符串**里，改写会轻微改变运行时提示文案。
- **影响面**：纯文本改动，无功能影响；但 `.gitignore` 重写需要确认没有把有效忽略规则写坏。
- **风险等级**：P2（可读性与安全提示失效，非功能缺陷）　**证据可信度**：高　**审批状态**：待确认

---

---

## 8. 需要业务确认的问题

> **这是本报告最需要你逐一回答的部分。** 下列问题**不能由我替你假设**，必须由你判断后才能进入修改阶段。
> 每条都标注「需要你回答什么」与「不回答的后果」。

### 8.1 高优先级（各自连带决定多条其他条目）

**Q1. `/home` 这张 554 行的「组件示例页」是否就是生产首页？**

- 事实：`db/init_data.sql:28` 把它登记为菜单「首页」→ `/home`；页内自述「不读取业务接口，全部本地 mock」（`home/index.tsx:220`）。
- **它牵连的决策**：`CodeViewer`/`BaseChart`/`G6Graph` 三个组件的去留、`monaco-editor`/`@monaco-editor/react`/`echarts`/`@antv/g6` 四个依赖、`public/monaco/vs` **13.35 MB** 静态资源、9 个从未被传入的 prop。
- ⚠️ **`G6Graph` 必须单独确认**：`AGENTS.md` §4 与 `ARCHITECTURE.md` 明确「血缘图优先复用现有 `G6Graph`」——**若该规划仍在执行，它必须保留**。
- **不回答的后果**：这条链上的所有条目（FE-PAGE-01/03、FE-CMP-02/03、FE-DEP-02/08）都无法处置。

**Q2. `AccessPolicy.requiresPlatformTransferReview` 是「死代码」还是「未接线的安全闸门」？**

- 事实：该方法**全仓库零调用**，用途是「调部门会改变动态角色（`DEPT`/`DEPT_AND_CHILDREN`）的数据范围，这类迁移交由平台管理员审批」。
- 当前两个调部门入口**都不用它**：`UserService.update:175-208` 直接改 `deptId`；部门调动审批是固定 `SOURCE→TARGET` 两步，**代码中不存在"平台管理员复核"步骤**。
- **需要你回答**：① 按设计它**应该**接入 `UserService.update` 的调部门路径吗？（那这是一条**真实的安全缺口**）② 还是设计已废弃（可删 197-204 行及无用 import）？
- **不回答的后果**：若属情况 ①，删掉它会**丢失一条已声明的安全意图**。

**Q3. 验证码「可关闭」开关还要不要？**

- 事实：`RegistrationRequest:12-15` 与 `CaptchaService:100-118` 的 Javadoc 承诺 `lbl.security.captcha-enabled=false`，但——**配置里没有这个键**（全仓仅 1 处命中，就是那句注释）、**`SecurityProperties` 没有这个分量**、**`CaptchaService:91` 注入了它却从未读取**、前端 `types/auth.ts:27-31` 已按"存在开关"写好且 `register/index.tsx:71` 的判断恒为真。
- **需要你回答**：① 实现真实开关（补配置字段 + `issue()`/`verify()` 分支 + `Challenge.enabled`）？② 还是放弃（删无用构造参数、死 `log` 字段、过期 Javadoc，前端删 `enabled` 与恒真条件）？
- ⚠️ **前后端必须同批改**——单独"修好前端"会制造"前端隐藏、后端仍强制"的**真实故障**。
- **不回答的后果**：代码、配置、注释、前端类型**四处长期不一致**（这是审计中发现的**唯一一个文档承诺与实现完全脱节**的功能）。

**Q4. UIAS 统一认证入口还要不要？**

- 事实：`UiasAssertionConsumer`/`EnterpriseDirectoryPort` **0 实现**（全仓 13 命中，全部是定义/注入/使用点）→ `isUsable` **恒 false** → UIAS 链路**恒不可达**；登录页的统一认证按钮**已被注释**（`login/index.tsx:119-134`）；但 `application-local.yml:63` 明确"实际实现只在内网部署时提供，本仓库仅保留流程编排与适配端口"。
- **需要你回答**：① 保留（则前端的注释代码、`unifiedProvider` 变量与 `.login-unified-button` 4 条 CSS **也应保留**，并明确 Owner/接入时间）？② 还是确定不做（则前后端一起清，共 4 处）？

### 8.2 行为语义确认（改动会影响用户可见行为）

**Q5. 日志查询的时间范围是「闭区间」还是「左闭右开」？**

- 事实：**同一个 `page` 端点用闭区间 `[b,e]`（`LoginLogService:69-70` 的 `.ge/.le`），而 `statistics` 与 Agent 路径走半开 `[b,e)`**（`LoginLogMapper:76-89` 的 `>=` 且 `<`）；而 `LoginLogController:44` 的 Javadoc **写着"左闭右开"**，与它自己控制器里的 `page` 端点矛盾。
- **后果**：**边界那一秒的日志会出现在列表里、却不出现在统计里**（用户可见的数据对不上）。
- **需要你回答**：统一成哪一种？

**Q6. 审批中心「我已处理」要不要显示历史轮次？**

- 事实：`processed` 复用 `COLUMNS`（含 `t.approval_round = a.approval_round`），但 `countProcessed` **不过滤轮次** → 驳回重提后**列表少行、total 多算、出现空白尾页**。
- **需要你回答**：① 列表与计数都限定当前轮（少行但一致）？② 还是 `processed` 去掉轮次限制（能看历史轮次）？

**Q7. 外部身份「重复绑定」是幂等还是拒绝？**

- 事实：`RegistrationService:181-183` 对"已有同 provider"**一律拒绝**；`ExternalLoginPersistence:127-130` 在"同 provider 且同 subject"时**提前 return 实现幂等**。两处文案也不同。
- **需要你回答**：统一为哪种语义？（DB 唯一索引是最终边界，两种都不越权）

**Q8. 前端「删除后回退页码」的判定条件哪个是对的？**

- 事实：`system/user/index.tsx:121-122` 用 `currentRecordCount === 1`，而 `loginlog:43-45`/`operatelog:41-43` 用 `=== 选中条数` → **语义已分叉**。
- **需要你回答**：哪个正确？（统一前必须先定）

**Q9. `notification.created` 与 `approval.request.updated` 两个实时事件是否合并？**

- 事实：两个事件在**唯一消费点** `AppLayout:112-115` 做**完全相同**的处理（`loadNotifications` + `getPendingApprovalCount`）→ 每次审批动作对每个参与者产生 **2×REST 全量拉取**；而 `approval.request.updated` 目前**没有独立消费者**（审批中心表格未订阅实时，靠手动 `load()`）。
- **需要你回答**：合并为一个，还是给两者各自明确的消费端？

### 8.3 工程与流程决策

**Q10. 是否收紧前端类型检查（`noUnusedLocals`）与接入 ESLint/Prettier？**

- 事实：项目**无 ESLint 配置、无 Prettier 依赖**（但 `.prettierrc.json` 在仓库里）、3 处 `eslint-disable` 是**空操作**、`tsconfig.app.json` 未开 `noUnusedLocals`（全仓**唯一**实锤死导入是 `MenuOutlined`）。
- **需要你回答**：① 开 `noUnusedLocals`（⚠️ **会打断当前构建**，需先清理）？② 补 `prettier` 依赖 + `format` 脚本，还是删掉孤儿配置？③ 接不接 ESLint？
- 三者是**同一批工程门禁决策**，建议一起定。

**Q11. `pages/**` 的 glob 扫描规则要不要收紧？**

- 事实：glob 是 `**/*.tsx`（**不是** `**/index.tsx`），把 **16 个对话框/共享组件**收进菜单组件候选；这 16 个**没有 `export default`** → 若被选中，`lazy()` 解出 `undefined` → **React 白屏崩溃，而不会走友好的 `PageUnavailable` 诊断页**（与该模块注释宣称的设计意图矛盾）。
- 你已于 2026-10-11 决定"暂不改扫描规则，靠菜单配置规范规避"（`ARCHITECTURE.md` §2）；但**内网需求模块本轮新增 5 个 tsx，候选从 10 → 16，风险面在扩大**。
- **需要你回答**：① glob 收敛为 `**/index.tsx`（已核对 9 个种子 `component` 值全以 `/index` 结尾，安全）？② 在 `resolveMenuComponent` 校验 default 为空则返回 `null`（**改动最小且能让诊断页真正生效**）？③ 继续靠操作规范规避？
- ⚠️ 三者都触及**基线基础设施**，按 `AGENTS.md` §2 需批准。

**Q12. `.env.development` / `.env.production` 是否移出仓库？**

- 事实：两文件**已入库**（`git ls-files` 确认）；`.gitignore` 未覆盖它们。当前值本身不敏感（`http://localhost:8080/api` 与 `/api`），但违反 `AGENTS.md` §3 的精神。
- ⚠️ **若改为 `.env.example`，必须同批补文件**——否则新克隆仓库的 `VITE_API_BASE_URL` 为 `undefined`，`utils/request.ts:5` 的 `baseURL` 变空值。

**Q13. 是否给两个密码入口补「来源级」限流？**

- 事实：`/api/auth/login` 目前**只有失败计数 + 锁定，没有请求级上限**；失败路径每请求 2 次 Redis `INCR` + 1 次 `REQUIRES_NEW` 的 DB 插入，**锁定期内仍各写一条 LOCKED 日志**（`LoginLogService:43-44` 的 javadoc 自己承认这会被刷高）。这是**加强而非削弱**。
- ⚠️ 但 `requireSourceQuota` 只在可信代理下采信转发头，**共享出口 NAT 会误伤**。
- **需要你回答**：加不加、阈值多少、是否接受误伤。

**Q14. `TRUSTED_PROXIES` 的部署形态确认**

- 事实：`application-local.yml` 该项**默认为空** ⇒ 反向代理后所有用户共享网关地址，**"来源级"锁定会互相误伤**。代码注释已写明"部署在 Nginx 后面时必须填写"。
- **需要你确认**：内网实际部署是否在网关后；若是，必须把该项写入部署清单。

### 8.4 其余待确认项（低风险，但需你给出方向）

| 编号 | 需要你决定什么 | 简要 |
| --- | --- | --- |
| BE-CMN-06 | `MaxUploadSizeExceededException` + yml multipart 是否保留？ | 很可能是为 **D-032 的 Excel 导入**（已预确认将新建 multipart 端点）保留的 → 建议保留并在文档标注"当前无上传入口" |
| BE-CMN-05 | `CurrentUser.deptId` 保留还是删除？ | 零读取；但若打算用它省去数据范围判断时的一次回查则应保留 |
| BE-CMN-07 | `AccessPolicy.isSuperAdmin` 是否降为 `private`？ | 仅类内使用；javadoc 移位置不影响"口径唯一"的意图 |
| BE-AGT-01/05 | `AgentRunObserver` 是**实现**还是**删除**？ | 实现则能补上当前**完全缺失**的运行可观测性（耗时/失败率/token 用量）；删除则省约 50 行 |
| BE-AGT-06 | `AgentProperties` 8 个未配置项是否显式写进 yml？ | ⚠️ 同时需修正 `AGENT-GUIDE.md:378-380` 的速查表（**当前按表操作会以为已生效**） |
| BE-LOG-02 | `AuditRiskController`（前端 0 消费者）删除还是留作手工调试口？ | 若保留，**必须写进文档**，否则会持续误导读者以为有页面在用 |
| BE-LOG-06 | `lbl.log.retention` 是否在 `application-prod.yml` 补显式契约？ | 当前 6 项全走默认值，运维改保留天数只能靠外部 yml |
| BE-LOG-08 | 两个"只写不读"的排行榜 SQL：**删除**还是**真正使用**？ | 每次报告白跑 2~3 条带 `HAVING/ORDER BY/LIMIT` 的分组查询 |
| BE-REQ-01 | 「期望上线时间」的**权威来源**是主表还是调用明细？ | 同一弹窗当前渲染两个同名字段；孤儿列在新增交易明细表（`V12:25`，无实体字段、0 引用） |
| BE-REQ-02 | 是否新增 `intranet:requirement:approve` 权限码？ | 当前 approve/reject 复用 `:submit`，语义与受众混同；非超管候选审批人若无 list 权限会在审批中心 403 |
| BE-APR-04 | `approveCurrent` 的「指定下一审批人」参数保留还是简化？ | 生产 3 个调用点**全传 null**，只有测试用它 |
| BE-APR-11 | 是否在 `MIGRATION.md` 写明「全新库不得执行 `db/approval/V1~V4`」？ | `V4` 的 `ALTER` 列已存在于 `init_schema.sql:188-191`，对新库重复执行会失败 |
| BE-AUTH-18 | `SessionService` 的旧竖线格式兼容分支是否可删？ | 需确认所有实例升级超过 14 天且 Redis 无历史 key |
| BE-VAL-08 | HBase/ES 字段 `sortOrder` 是否补 `@Min(0)`？ | 当前只有 `@NotNull`，同类 `MenuRequest`/`DeptRequest` 都是 `@Min(0) @Max(9999)` |
| BE-VAL-17 | `RoleController.grant` 是否补 `@Valid`？ | `List<@NotNull Long>` 因缺 `@Valid` **完全失效** |
| BE-VAL-18 | 是否接受「需求明细超长 → 500」这一表现？ | D-037 已决策不新增明细 DTO/校验；可选的低代价缓解是**前端补 `maxLength` 与列宽对齐**（不动后端契约） |
| FE-INF-01 / §8.4 | `assignRequirementTransaction`（前端零调用）保留还是删？ | ⚠️ 同样地，后端 `PUT /{id}/developer`（**分配开发负责人**）**前端连 api 函数都没有**——两者共同指向**未完成功能**，**不宜当死代码处理** |
| FE-DEP-02/03 | `monaco-editor` 移 `devDependencies`？`@types/node` 保留？ | 需确认 `public/monaco/vs` 的生成方式与 `tsconfig.node.json` 的类型需求 |
| CFG-11 | 8 个文件的乱码注释是否按 UTF-8 重写？ | ⚠️ 只重写注释，**不要动配置键值**；`pack-incremental.mjs` 的乱码在异常消息**字符串**里 |
| CFG-08 | 项目命名三套口径（`lbl-shit` / `Kariya Admin` / `kariya-admin`）是否统一？ | ⚠️ **Redis 通道名 `kariya-admin:realtime:events` 不能单独改**——改名必须同时改发布/订阅双方，且部署期间新旧实例混跑会丢事件 |
| CFG-02 | `frontend/pack-incremental.mjs`（离线打包脚本）还需要吗？ | 若内网离线部署仍依赖它则**必须保留**；若已改内网私服则可删 |
| FE-CMP-06 | `useClipboard` 的 `execCommand` 降级路径是否保留？ | 取决于内网浏览器内核基线；若仍有旧国产内核在用则必须保留 |
| FE-CMP-07 | 两个单消费者 hook 是否内联回组件？ | `useClipboard`/`useFullscreen` 都是薄封装；`useResizeObserver` 有真实生命周期价值，建议保留 |
| FE-INF-11 | 通知详情的三个入口是否收敛为 `useNotificationTarget()`？ | URL query + 组件 state + 直接 setState 三处写同一份弹窗状态 |

---

## 9. 建议保留的必要设计

> **这是本报告的反向清单**：以下内容已被多路审计**明确认定为必要**，任何"删重复/精简"都不适用于它们。
> 逐条给出理由，供你在审批时比对。**§7.3 的表格同样是"不是问题"的清单，两者互补。**

### 9.1 安全与鉴权（最高优先级，不得以"内网"为由削减）

1. **JWT 签名与令牌级一致性校验**：`JwtService.parse`（HMAC 验签、过期）+ `JwtAuthenticationFilter:61-62` 的 `subject`/`authVersion` 与 Redis 快照的双向一致性——**防伪造、防旧令牌重放的核心**，不因"会话已校验"而可删。
2. **会话双轨有效期**：空闲 TTL + `absoluteKey` 绝对上限；`SessionLifetimePolicy` 保证 `touch` **只续空闲、绝不续绝对期限**。
3. **刷新 Cookie 属性**：`HttpOnly` + `Secure` + `SameSite=Lax` + **`path=/api/auth`**。⚠️ **`path` 不能放宽到 `/`**——那会把刷新凭据发给每个业务请求。
4. **401/403 分流契约**：`/api/auth/onboarding/**` 的 `authenticated()`（删掉会导致**"页面卡死"**，`WebSecurityConfig:51-59` 记录了该真实故障）与"待改密用户必须能调改密接口"的**排除式**表达式。
5. **双层失败锁定 + 来源哈希化**：账号级严、来源级宽以对抗密码喷洒；Redis key **只存 SHA-256 而非明文 IP**。
6. **可信代理判定**（`TrustedProxyResolver`）：只信可信代理的转发头、从右往左取第一个不可信地址、CIDR 字节前缀匹配——审计 IP 与限流 key 的**信任根**。
7. **反账号枚举**：对外统一「用户名或密码错误」，差异化原因**只进 `sys_login_log`**。
8. **服务端验证码 + 一次性消费**（`getAndDelete` 无论对错即失效）+ **字体探测拒绝启动**（避免"永远猜不中且不报错"）。其 `catch (Throwable)` 是**必要的**（`AWTError` 属 `Error`）。
9. **密码规则只用于"设置新密码"**：历史密码/原密码**不套新规则**，否则老用户立刻登不进去。
10. **改密/重置/停用即失效所有会话**：`auth_version+1` + `sessions.removeAll` + `realtime.closeSession`（**会话失效必须能断开 WebSocket**）。
11. **解绑前的"必须保留一种可用登录方式"**（含 `selectByIdForUpdate` 并发保护与影响行数判定）。
12. **DB 唯一索引作为最终边界** + `DuplicateKeyException` → 400 的兜底。⚠️ Java 侧前置校验**不得**以"有 DB 约束"为由删除（否则用户看到 500/400 通用文案）。
13. **`PasswordChangePolicy` 不含 `authMethod` 判断**——文档已明确"**不要当成 bug 去掉**"。
14. **必须保留的参数校验纵深**：`countIncludingDeleted*` 系列（**含软删口径**——逻辑删除的物理索引项仍在，不能用"DTO 已校验"替代）、唯一性/存在性预检、`status` 复核、各 Controller 的 `@PreAuthorize`。

### 9.2 并发与数据一致性

15. **审批实例的事务内 `SELECT ... FOR UPDATE` + `@Transactional(MANDATORY)`**「先锁实例、再复核状态」顺序——D-034 并发正确性的**唯一保障**。
16. **用户行 `selectByIdForUpdate`**（登录方式变更、部门调动提交的串行化）。
17. **`sys_approval_instance` 的业务唯一键** + `create()` 的重复校验——并发最后防线。
18. **`ApprovalWorkflowService.resubmit` 的新轮次模型**——修 BE-APR-01 时**不得破坏**它。
19. **前端 `SmartTable` 的请求竞态保护**（`requestSequence` 丢弃过期响应）+ `tableStateVersion`——删掉会出现"列表显示上一次筛选结果"。
20. **`services/progress.ts` 的三层保护**（300ms 延迟 + `active` 并发计数 + `resetProgress` 收口）——分别对应三个**已发生过**的 bug。
21. **`utils/request.ts` 的 401 续期链 + `services/authSession.ts` 的 `authenticatedFetch`**——**两套并存是必要的**（一套给 axios、一套给 SSE，无法合并；`useAgentChat.ts:29-35` 解释了原因）。
22. **审批的禁止自审 + 服务端授权复核**——不能只依赖列表过滤。
23. **`IntranetRequirementService.requireOwner` / `requireEditableDetail`**——草稿/已拒绝才可编辑的业务闸门。

### 9.3 审计与可观测性

24. **`OperationLogAspect` 的 `@Order(0)` + `finally` 落库**——权限被拒绝也要记 FAILURE 尝试，是审计价值。
25. **日志写入的 `REQUIRES_NEW` + 内部吞异常**——业务回滚不能把审计一起回滚；审计失败不能反过来让用户登不进来。
26. **`NotificationService.afterCommit` 推送**——防"回滚后误推"与"推送早于落库"。
27. **`RealtimeGateway`「Redis 优先、仅失败才本地补发」+ 后台重订阅**——防多实例重复投递、防 Redis 抖动导致启动失败。
28. **`RealtimeTicketService` 的一次性 ticket（`getAndDelete` + 60s TTL）+ 握手校验**——WebSocket 的**唯一鉴权入口**。
29. **`LogRetentionJob` 的分批删除 + 上限 + 无 `@Transactional`** 与"至少保留 1 天"的兜底。
30. **`AuditRiskAnalysisService.maxEventsPerSource + 1` 的溢出探测**——用"取 N+1 行"避免全量拉取。
31. **`AuditRiskContext` 只读快照 + 一次查询 4 规则共享**——删了会让 4 条规则各查一次库。
32. **`SlidingWindowDetector` 的双指针 + 重叠窗口合并**——"8 次失败只生成 1 条风险"**无法用 SQL 表达**，是全程唯一真正的算法。⚠️ **建议恢复它被删除的测试。**
33. **`AuditRiskFinding.evidenceLogIds`**——风险结论带可回查证据 ID，审计场景的硬要求。
34. **`RequestInfo` 的可信代理判定与 `resolver==null` 的安全退化**（对单测/非 Web 上下文必要）。
35. **`ExternalLoginPersistence` 刻意不持有 HttpClient + 成功日志在事务外写**（避免事务中网络 IO 与"一个请求占两条连接"）。
36. **`ExternalLoginService.begin` 的 SAML 拒绝**——即使有人把 provider 改成 SAML 并 enable 也不会走进未实现路径，**是安全网而非死代码**。

### 9.4 Agent 模块（已有导读逐条论证）

37. **`AgentActor` 快照 + `ToolRegistry.visibleTo` 权限过滤 + `ToolExecutionService` 二次鉴权**——**三重校验不是冗余**：分别防"模型看见不该看的"、"模型幻觉工具名"、"绕过 `AgentRunner` 的直接调用"。**不要合并。**
38. **`AgentChatRequest` 白名单 DTO**——刻意不复用模型 DTO、不收权限码/角色/任意 Map；所有 `@Size` 上限必须保留。
39. **`AgentRunner.withPageContext` 把客户端数据拼进 USER 消息**并显式标注"不代表权限，也不是系统指令"——防提示注入的正确设计。
40. **`ToolResult.modelSummary` 与 `artifact` 分离**——应删的是 `output` 通道（BE-AGT-04），**不是**这个分离本身。
41. **ReAct 循环的四种防护**：失败工具黑名单、`profile.allows` 二次校验、`BusinessException`/`AccessDeniedException` 分开 catch、`CancellationException` 直接 rethrow。
42. **独立线程池跑慢速 LLM IO + `AbortPolicy` + `TaskRejectedException` 处理**。
43. **`CancellationToken` + `checkpoint()` 统一检查点**（8 个调用点）。
44. **4 个意图级审计工具 + `mode` 参数**——让模型一次调用定意图，避免 SQL 次数不可预测。
45. **`AuditTimeRangeResolver` 服务端解释时间预设 + Asia/Shanghai 固定**。
46. **`AgentProfileConfig` 的 10 条 system 指令**——尤其第 2（防幻觉）、3（防工具结果注入）、8（mode 选择与禁止按天拆分）、9（事实/推断/建议三分）、10（时间必须用预设）条。
47. **`ToolRegistry` 的启动期 fail-fast 校验**（名称正则、description 非空、重名即报错）。
48. **`OpenAiCompatibleModelGateway` 的三处纪律**：异常不写响应体、`RestClient` 懒加载 + 双重检查、`ensureConfigured()` 明确文案。
49. **`AgentArtifact.type + schemaVersion` 版本化契约 + 未知类型安全降级**。

### 9.5 前端架构

50. **`componentRegistry.tsx` 的 `import.meta.glob` 动态扫描 + `resolved` Map 缓存**——"新增页面不改任何 map"的唯一支点；注释记录了"每次渲染重新 `lazy()` → 组件卸载重建 → 接口重复请求"的真实缺陷。
51. **`AuthGuard` 的会话恢复 + `onboardingElsewhere` 多拦一帧**——避免开户态先渲染 `AppLayout` 触发一批必然 403 与注定失败的 WebSocket 重连。
52. **`permission/Permission.tsx` + `DynamicPage` 的三级判定（我的菜单 → 403 → 404）+ `keepAlive`**——**菜单树本身就是后端算好的授权结果**，前端不再维护"路径→权限码"映射。
53. **`theme/ThemeProvider` 的「AntD token 与 CSS 变量两套必须同投」** + `tokens.ts` 唯一定义处 + `SystemSettings` 唯一生效入口。
54. **`store/notificationSlice` 的通知单源设计**——注释完整记录了"两处 `useState` → 红点不消失"的真实 bug。
55. **真正有效的共享抽象**（与三个单点组件性质不同）：`SmartTable`（11 消费点/37 命中）、`EllipsisText`（11 文件/40 命中）、`FieldLabel`（4 文件/30 命中）、`GlobalSearch`。
56. **`index.html` 与 `loadingScreen.css` 的两份首屏样式必须都存在**（React 挂载前无 JS 样式可用）——**只是选择器已分叉**（`#app-loading` vs `.app-loading`），建议补注释而非合并。
57. **`utils/passwordPolicy.ts` 的前端副本**——必须与后端 `PasswordRules.REGEX`/`MESSAGE` 逐字符一致（文件头写明了同步义务与漂移后的用户可见症状）。
58. **`UserActivityManager` 的交互节流 `touch`**（5 分钟窗口）——服务端仍有空闲超时与绝对有效期，这里是"用户确实在操作时续期"。

### 9.6 已在代码注释中自证必要、**请勿删除**的若干处

| 位置 | 注释自证的内容 |
| --- | --- |
| `GlobalExceptionHandler:160-180` | `declaredMessage` 的反射**是为了不把 `@Pattern` 的正则原文漏给用户**；失败方向安全 |
| `AccessPolicy:425-437` | `isSuperAdmin` 必须带 `status=1`——否则出现"后端认为不是超管、前端显示是超管"的双口径 |
| `LoginAttemptGuard:26-34` | 双层（账号 + 来源）限流的原因 |
| `CaptchaService:226-229` | `catch (Throwable)` 是必要的（`AWTError` 属 `Error`） |
| `UserMapper:12-20` | 逻辑删除只置 `deleted=1`，**物理唯一索引项仍在** → 必须用 `countIncludingDeleted*` |
| `DeptService:44-46` | 曾经是 N+1，改为批量取名的原因 |
| `UserController:144-154` | 同一 SpEL 口径的**真实漂移史**（"全项目其余成员接口都是这个口径……只有这里漏了"） |
| `WebSecurityConfig:51-59` | 删掉 `onboarding/**` 的 `authenticated()` 会导致**页面卡死** |
| `AccountSessionController:52-56` | 为什么刷新 Cookie 的 `path` 不能放宽 |
| `PasswordChangePolicy:59-63` | 为什么 `authMethod` 走会话快照、且**不要当成 bug 去掉** |
| `AssetDialogs:9-23` | 已写好可复用的 `useDialogLoad`——**说明"重复"是可消除的** |
| `store/notificationSlice:11-25` | 两处 `useState` 导致红点不消失的真实 bug |
| `componentRegistry.tsx:28-31,52-54` | 为什么必须缓存 `lazy()` 结果、为什么用 `trackProgress` 包装 |
| `security.css:4-7` | 平台品牌色在深色主题下不可见（**登录页是它的未修复副本**） |
| `theme.css:8-31` | 历史上三个"改了没反应"的陷阱 |
| `LoginTransaction:11-12` | Redis key/字段名是**存储契约**，改名会让在途事务反序列化失败 |
| `RequirementDetailDrawer` 之外的多处 | （见 §7.3 的完整表） |

---

## 10. 推荐的分批治理顺序

> **前提：本报告所有条目均为「待确认」，未做任何修改。** 下列批次是**建议**，最终由你逐项审批后决定。
> 分批原则：**先做零风险的 → 再做只碰 intranet 的 → 最后才碰基线公共代码**；每批都要有可独立验证的出口。

### 第 0 批：**先补验证能力**（建议排在所有修改之前）

| 事项 | 理由 |
| --- | --- |
| 恢复/新增 `SlidingWindowDetector` 的跨窗口测试 | 该算法是"8 次失败只生成 1 条风险"的唯一实现，**当前无任何测试**（D-030 已确认本期不补 P1 领域测试；**本条建议单独评估**——见 §9.3 第 32 条） |
| 为 `passwordPolicy.ts` ↔ `PasswordRules` 加**构建期一致性断言** | 前后端正则已 6 处手抄且**无护栏**；加断言**不改运行时**（BE-VAL-19 / BE-AUTH-12/19） |
| 为 JSON Schema ↔ Bean Validation 的 6 组约束加断言 | 漂移会导致"Schema 允许但服务端拒绝，且**用户看不到原因**"（BE-LOG-13） |
| 在 `MIGRATION.md` 写明「全新库只用 `init_schema.sql`/`init_data.sql`，**不得**执行 `db/approval/V1~V4`」 | 纯文档，零风险（BE-APR-11） |

**为什么排在第 0 批**：本报告发现的多处"重复实现"（正则、Schema、状态映射）之所以危险，**根源是缺少自动护栏**；先建护栏，后续合并才有回归保护。

### 第 1 批：**零风险清理**（P0 级，逐条可独立验证，**不改变任何行为**）

| 编号 | 内容 | 验证方式 |
| --- | --- | --- |
| CFG-01 / FE-DEP-01 | 删除未使用的 `html2canvas` 依赖 | `npm install` + `npm run build` |
| BE-CMN-02 | 删除 `ExternalAuthController:8` 的未使用 import | `mvn compile` |
| FE-DEP-07 / CFG-10 | 删除 `pages/system/menu/index.tsx:5` 的 `MenuOutlined` 导入 | `tsc --noEmit` |
| CFG-05 | `logging.level.org.kariya` → `org.lbl`（或删除） | 启动后看日志 |
| BE-AUTH-09 | 删除 `SessionGrant.onboarding`（⚠️ **不要误删同名的 `LoginSession.onboarding()`**） | `mvn compile` |
| BE-AUTH-06 | 合并两份逐字相同的 `normalizeIssuer` | `mvn compile` |
| FE-INF-04 | 删除 19 个多余 `export`（含 `SmartTableSelection`） | `tsc --noEmit` |
| FE-INF-06 | 删除 10 个前端死字段（`AgentMetric.format/status` 等） | `tsc --noEmit` |
| BE-AGT-07 | `AgentPageContext.menuId`：补一行使用，或 4 处一起删 | `mvn compile` |
| BE-AGT-08 | `AgentMessage.Role.SYSTEM` 零构造分支 | `mvn compile` |
| FE-DEP-06 / CFG-04 | 3 处 `eslint-disable` 改写为普通注释（**保留原因说明**） | 人工复核 |
| CFG-11 | 8 个文件的乱码注释按 UTF-8 重写（⚠️ **只重写注释**） | 逐文件 diff |
| FE-CMP-04 | `StatusTag` 的 `label` 入口（1 处调用）改为 `children` | `tsc --noEmit` + 目视用户管理页 |

**风险**：全部为"删死代码"，`mvn compile` / `npm run build` 即可验证；**本批不含任何行为变更**。

### 第 2 批：**仅 intranet 模块内的清理**（不碰基线，可独立回归）

| 编号 | 内容 |
| --- | --- |
| BE-DA-01 | `EsAssetService` 的 N+1 改为批量 `selectByIds`（**同项目已有 `DeptService` 的修复先例可对照**） |
| BE-DA-02 | 实体镜像型 VO 的处理（**保留不可变 record，仅明确其定位**）；前端 `TransactionDataResources` 删 1 份 |
| BE-REQ-03 | 前端需求类型/状态映射 4 份 → 1 份 |
| FE-PAGE-08 | 三个明细弹窗的局部 `Text` 组件与弹窗外壳合并 |
| BE-VAL-07 | `:65` 的内联条件改为调用 `requireEditableDetail`（**行为不变**） |
| BE-VAL-22 | 4 个写弹窗补齐同步提交闸门（与已有 6 处统一） |
| BE-REQ-05/06 | `decide()` 异常文案拆分；需求概要两套渲染合并 |
| FE-PAGE-01（若 Q4 为"不做"） | 登录页 `unifiedProvider` 死变量 + `.login-unified-button` CSS（**需目视回归登录页**） |

**风险**：intranet 模块可按 `AGENTS.md` §3 整体剥离，改动不波及基线；**验证**：构建 + 相关页面人工联调（P1 领域测试已按 D-030 决定不补）。

### 第 3 批：**基线公共代码的重复消除**（**必须逐项获批**，按 `AGENTS.md` §2 走批准流程）

| 编号 | 内容 | 回归面 |
| --- | --- | --- |
| BE-VAL-01/02/04/05 | 删除 4~5 处"DTO 已同规则同文案覆盖"的 Service 冗余校验（**合计约 10 行**） | 4 个 Service 的报错文案 |
| BE-VAL-03 / BE-SYS-01 | 7 处重复的分页守卫收敛为共享工具 | 7 个分页接口 |
| BE-AUTH-12 | 抽 `UsernameRules` 常量（比照 `PasswordRules`） | 4 个 DTO + 2 个前端页面 |
| BE-AUTH-02 | 「凭据是否可用」7 处收敛（**注意两种写法 null 安全性不同**） | 7 个调用点，含"解绑最后一个登录方式"判定 |
| BE-AUTH-03 | 「会话有效性」4 处收敛（**保留 filter 的令牌级校验**） | 每条已认证请求 + refresh/touch/me |
| BE-SEC-04 | 8 份 SpEL + 2 份权限码列表收敛（**保留排除式语义**） | 8 个 Controller + filter + `/auth/me` |
| BE-SEC-02/03 | JWT 死 claim 与登录回程重复解析（**不能让 sid 进入出网 DTO**） | 登录/注册/开户签发路径 |
| BE-AUTH-01 | 「密码校验+计数+日志」编排合并（**必须保证两条入口都继续计数**） | 登录主链路 + 四条行为回归 |
| BE-AUTH-05 | 外部身份绑定两处合并（**保留 DB 唯一索引**；先定 Q7 语义） | 绑定/开户流程 |
| BE-NTF-03 | 建立 `businessType`/`notificationType` 常量（前后端各一处） | 审批中心查询、通知跳转 |
| BE-SYS-02 | `DeptPaths` 增 `ancestorIds`，两处解析收敛 | 部门列表 + 角色可授权部门 |
| BE-SYS-03 | `MenuController` 引入 `MenuVO` 或对持久化字段加 `@JsonIgnore` | 菜单管理页 |
| BE-SYS-04 | 内置菜单种子双份来源消除（**不建议只删一侧**） | 新库初始化 + 老库升级 |
| BE-LOG-01/03/04/05/11 | 日志侧死方法/死守卫/死字段清理（**保留 `enabled` 字段与枚举值**） | 日志分析与风险扫描 |
| BE-LOG-13 | Schema 与注解共享常量 | 4 个 Agent 审计工具 |
| BE-AGT-02/04 | Profile registry 与 `ToolResult.output`（**保留 `profile.allows()` 与 `modelSummary/artifact` 分离**） | Agent 工具调用 |
| FE-CSS-03/04/05 | 6 处重复的分页 margin、两份日志 CSS、两个字段页 CSS 合并（⚠️ **必须逐页目视**） | 5 个列表页 + 2 个日志页 + 2 个字段页 |
| FE-PAGE-06 | 抽 `useDialogLoad`（**复用已有实现**）+ `SystemDialog` 外壳 + `useSubmitLock()` | 9 个弹窗，**注意加锁时机不一致** |
| FE-PAGE-04/05 / BE-NTF-02/06 / BE-APR-09 | 部门变更审批 Modal + Steps 状态映射（**三份合一**） | 审批中心 + 消息中心 + 账号安全页 |
| FE-PAGE-07/09/10/11/13 | 两个日志页同构、会话列表项、回退页码（**先定 Q8**）、分页契约适配、时间格式化 | 若干列表页 |
| FE-PAGE-12 | 错误提示收敛（⚠️ **抽 hook 而非改拦截器**，风险最低） | 30 个页面 |
| FE-INF-05/08/09 | barrel 类型转发、`navigateTarget`、重连抖动 | 类型层 + 重连时序 |
| FE-CSS-01/08/11 | `--fs-md` 修正、`13px`→`var(--fs-sm)`、删 `var(--surface,#fff)` 死兜底 | **需目视** AI 助手 Markdown 标题、交易列表查询区、深色主题 |

**风险**：这一批**触及大量基线公共代码**，必须逐项获批 + 完整回归（用户/角色/部门/菜单/日志/审批/内网全部列表页 + 登录/改密/解绑全链路）。**建议一次只做 1~2 项并单独验证。**

### 第 4 批：**需要业务/产品决策后**才能动的

| 待决策 | 连带条目 |
| --- | --- |
| Q1（`/home` 演示页去留） | FE-PAGE-01/03、FE-CMP-02/03、FE-DEP-02/08（3 组件 + 4 依赖 + 13.35 MB；⚠️ **`G6Graph` 单独确认血缘图规划**） |
| Q2（`requiresPlatformTransferReview`） | BE-CMN-04（**可能是安全缺口，不能当死代码删**） |
| Q3（验证码开关） | BE-CMN-01、BE-VAL-12/15、BE-AUTH-11（**前后端必须同批**） |
| Q4（UIAS 入口） | BE-AUTH-15、FE-PAGE-01/03 |
| Q11（glob 扫描规则） | CFG-09、FE-CMP-01 |
| Q10（工程门禁） | CFG-03/10、FE-DEP-05/07 |
| Q12（`.env` 出库） | FE-DEP-04（**必须同批补 `.env.example`**） |
| Q13（密码入口限流） | BE-AUTH-16 |
| CFG-08（项目命名） | ⚠️ **Redis 通道名牵连部署** |
| §8.4 其余项 | 见该表逐条 |

### 第 5 批：**性能与正确性改进**（不属"精简"，但审计中发现，建议单独立项）

| 编号 | 内容 | 收益 |
| --- | --- | --- |
| BE-LOG-08 | 删除/使用 2~3 条白跑的分组 SQL | 每次登录/操作报告省 2~3 次带 `HAVING+ORDER BY+LIMIT` 的分组查询 |
| BE-LOG-09 | 分片回看量修正 | 31 天风险报告 DB 读取量**约减半** |
| BE-LOG-10 | 日志区间语义统一（**需 Q5 决策**） | 修掉"边界那一秒列表与统计不一致" |
| BE-CMN-01 / BE-SEC-01 | `AccessPolicy.actor()` 请求级缓存 / 消除 filter 重复查询 | 每请求省 **5~10 次查询**（其中递归菜单 CTE 成本最高） |
| BE-AUTH-07 | 会话不再重复从 Redis 读取 | 登录/注册/开户/刷新/回调全路径省 1~3 次 Redis 往返 |
| BE-APR-01 | 「我已处理」列表与计数口径一致（**需 Q6 决策**） | 修掉空白尾页 |
| BE-NTF-01 | 两事件合并或分别接线（**需 Q9 决策**） | 每次审批动作的实时刷新请求量减半 |
| BE-VAL-20/21 | **日志脱敏（加强方向）** | 避免密码/`SQL 片段`进入日志与 `sys_operation_log` |
| BE-REQ-02 | 新增 `intranet:requirement:approve` 权限码 | 修掉非超管候选审批人 403 |
| FE-DEP-01（§7.2） | `favicon.ico` 0.95 MB 压缩 | 首屏资源体积（它位于首屏渲染路径） |
| BE-AUTH-16 | 密码入口来源级限流（**需 Q13 决策**） | 降低登录日志表被刷高的速度 |

### 10.1 分批原则回顾（供你判断批次时参考）

1. **P0 不代表允许自动删除**——第 1 批看似零风险，仍逐条列出等你勾选。
2. **不把"代码行数减少"当目标**——本报告的多条建议（`TextValues`、`FIELD_LABELS`、`NotificationService.afterCommit` 兜底、`AgentMessage` 的 SYSTEM 值、各 `enabled` 字段、`session.deserialize` 旧格式、`maxUploadSize` 处理器、实体镜像 VO）**结论都是保留**，因为它们承载了可读性、兼容性或未来价值。
3. **严格区分四类**（§4.4~4.7 已明确分类）：可删的重复防护 / 可简化的必要防护 / 必须保留的安全边界 / 待业务确认。
4. **基线代码优先保护**（`AGENTS.md` §2）：凡触及 `frontend/src/{components,router,styles,layouts,store,utils,services,theme}`、`backend/src/main/java/org/lbl/{common,config,security,system,approval,notification,realtime,agent}` 的改动，**一律走批准流程**。
5. **每批必须有可验证的出口**：`mvn compile` / `npm run build` 能覆盖的放前面；**只能浏览器目视的（CSS、布局）单独成批并逐项请你确认**。
6. **文档类修复（CFG-02/03/06/07/11、BE-APR-11/12、BE-LOG-12、BE-AGT-06、BE-AUTH-19）可随时做**，不影响运行，但**不要与代码改动混在同一批**，以免混淆验证结果。

---

## 附录 A：本次审计的检索手段与可信度说明

### A.1 执行方式

- **全部只读**：`read` / `grep` / `glob` + 只读 PowerShell（`Get-ChildItem`/`Select-String`/`Get-FileHash`/`Measure-Object`）+ 只读 Git 查询（`status`/`log`/`diff --stat`/`show`/`check-ignore`/`ls-files`）。
- **未执行**：任何 `mvn`/`npm`/`tsc`/lint/test、任何数据库连接与 SQL、任何浏览器操作、任何 Git 写命令、任何文件的修改/新建/删除——**除本报告 `docs/code-audit.md` 本身**。
- **组织方式**：1 个主审计会话 + **7 个分模块只读子审计会话**（基线分层与工具类、登录鉴权与会话、参数校验与幂等、公共审批与通知、Agent 与日志、前端页面与组件、CSS 与 UI）。每条子审计结论都由我复核后并入，并保留其"已检查/未检查范围"的原始声明。

### A.2 证据可信度分级依据（§0.3 的执行标准）

| 级别 | 判定标准 | 本报告中的典型 |
| --- | --- | --- |
| **高** | 全仓库逐符号引用计数 + 调用链，且**动态使用方式已排除** | 死字段/死导入/重复正则/死配置项/文件清单 |
| **中** | 引用计数明确，但存在无法静态排除的动态使用，或依赖运行期行为 | "选中即白屏"（静态控制流推导）、日志是否真写出密码值、`SessionService` 旧格式是否还有历史 key、`@Valid` 缺失后 `null` 元素的实际表现 |
| **低** | 仅静态推导，需实际运行或业务确认 | 媒体查询临界带溢出、600~900px 挤压、CSS 视觉变化的具体观感 |

### A.3 已排除的动态使用方式（"零引用"判定的前提）

Spring 组件扫描与 `@Bean`、`List<T>` 集合注入（`AgentTool`/`AgentRunObserver`）、MyBatis 注解 SQL 与 Mapper 接口、`@ConfigurationProperties` 属性绑定、`logging.level.*` 日志器名、`@PreAuthorize` 权限码、`@Scheduled` 定时任务（`LogRetentionJob`、`RealtimeGateway.heartbeat`）、`ApplicationRunner`（`SystemPermissionInitializer`）、前端 `import.meta.glob` 页面扫描、后端 `sys_menu.component` 配置驱动、前端模板字符串拼接的类名、antd 5 的 `classNames`/`rootClassName` 注入、`public/**` 静态资源引用、数据库脚本的人工按序执行。

### A.4 本报告的已知局限（**必须在审批时一并考虑**）

1. **没有任何构建/测试/运行验证**——所有"不可达/白屏/N+1/性能"结论都是**静态推断**。
2. **没有查库**——D-034/D-035 的迁移执行状态、`init_schema.sql` 与线上实际表结构是否一致、V7 唯一索引是否已执行、Redis 是否有历史 key，**均无法复核**。
3. **没有浏览器验证**——所有 CSS/布局结论（含 D-031 相关）都需要你按 `HANDOFF.md` §4 的清单目视回归；这也是 D-031 明文要求的（"布局效果构建验证不出"）。
4. **未审阅的区域**：`db/intranet/{V1,V3,V4,V5,V7,V8,V10}` 的完整数据语义、`docs/intranet/*.md` 全文与代码的一致性、构建/部署脚本、`node_modules` 完整依赖树、`pom.xml` 依赖可用性（仅核对了"是否在用"，未评估版本策略）。
5. **"零引用"不等于"可删除"**——本报告刻意把多处零引用项判为**保留**（`TextValues`、`FIELD_LABELS`、`afterCommit` 兜底、`AgentMessage.SYSTEM`、各 `enabled` 字段、`session.deserialize` 旧格式、`maxUploadSize` 处理器、实体镜像 VO、`pack-incremental.mjs`）。
6. **本报告的条目数不是"问题数"**——其中相当一部分（§7.3、§9、§2.2.4、§2.3.3、§2.5.3、§6.5）是**反向清单**，用来防止后续会话误删。

### A.5 审计过程中新增的文件

**仅 1 个**：`docs/code-audit.md`（本报告）。**未修改、未删除、未移动、未格式化任何现有文件**；工作区中你已有的未提交改动**未被触碰**。

### A.6 阅读路径建议

- 只想快速决策 → 先读 **§8（需要业务确认的问题）** + **§10（分批顺序）**；
- 关心安全边界 → 读 **§4.6（必须保留）** + **§9.1**；
- 关心前端 → 读 **§5** + **§6**；
- 关心某个具体模块 → 读 **§2.1~2.5** 的对应小节；
- 复核我的判定 → 每条都带 `路径:行号` 与检索命中数，可直接在仓库中验证。

---

## 附录 B：与既有前端专项审计记录（`frontend-deadcode-audit.md`）的复核对照

### B.1 该文件的处理方式说明

工作区中存在一条 Git 记录：`D frontend-deadcode-audit.md`——该文件**已从工作区删除，但仍存在于 HEAD**。它是一次**更早的前端专项审计**（56 条，样本量当时为 158 个文件 / 10,910 行，对应当前 `frontend` 已缩减为 131 个文件 / 9,028 行）。

**我的处理方式**：把它当作**既有审计记录**，在当前代码上**逐条复核**后引用，**不把它当作本次审计的原创新结论**。复核结果如下。

### B.2 已失效、**请勿再引用**的旧结论（8 条）

| 旧条目 | 旧结论 | 当前事实 |
| --- | --- | --- |
| 旧 #1 / #2 | `theme/index.ts`、`theme/types.ts` 是整文件死代码 | **文件已不存在**（`theme/` 现仅 `ThemeProvider.tsx` + `tokens.ts`） |
| 旧 #6 | `ThemeProvider.tsx` 的兼容再出口是死代码 | **已移除**（当前只导出 `useThemePreference`） |
| 旧 #10 | `system/user/index.css` 的 `.password-note` 是死样式 | **已移除**（全仓 `password-note` 0 命中） |
| 旧 #11 | `shared.css` 的 `.form-grid` 是预留样式 | **已移除**（全仓 `form-grid` 0 命中） |
| 旧 #21 | `StatusTag` 的 `label` prop 是死 prop（"3 个调用点全用 children"） | **已失效**——`label` 现有 1 个消费者（`pages/system/user/index.tsx:60`） |
| 旧 #22 | `classnames` 依赖冗余 | **已移除**（`package.json` 中已无 `classnames`） |
| 旧 #32 | 两个日志 `index.css` **字节完全相同** | **hash 已分叉**（`F68606…` vs `A5513F22…`），但**规则集仍相同** |
| 旧 #36 | 提交锁 8 处 | 现为 **9 处**（新增 `RequirementDialog:18`、`TransactionDialog:22`） |

### B.3 仍然成立、且本次已**升级/扩面**的旧结论

| 旧条目 | 旧状态 | 本次复核后的变化 |
| --- | --- | --- |
| 旧 #52（glob 收集非页面组件） | 10 个文件 | **→ 16 个文件**（内网需求模块本轮新增 5 个 `.tsx`）→ **CFG-09 / FE-CMP-01** |
| 旧 #45（演示页即生产首页） | 待确认 | 结论不变；**本次明确了它牵连的完整链条**（3 组件 + 4 依赖 + 13.35 MB + 9 个 prop）→ **FE-PAGE-01 / FE-CMP-02/03** |
| 旧 #23/#24/#25（依赖与 Monaco 资源） | 冗余 | 结论不变；**权重测**（103 文件 / 13.35 MB / 81 语言目录 / 9 个 nls）→ **FE-DEP-01/08** |
| 旧 #26/#27/#54（孤儿配置 / 空 eslint-disable / `.env` 入库） | — | 结论不变 → **CFG-03/04、FE-DEP-04/05/06** |
| 旧 #29/#30/#31（审批 Modal / Steps / 会话列表三处重复） | — | 结论不变 → **FE-PAGE-04/05/09**（并与后端侧 BE-NTF-02/06、BE-APR-09 **互相印证**） |
| 旧 #33/#34/#36/#37/#38/#44（契约适配 / 回退页码 / 提交锁 / 抖动 / 剪贴板降级 / barrel） | — | 结论不变 → **FE-PAGE-10/11、FE-PAGE-06、FE-INF-09、FE-CMP-06、FE-INF-05** |
| 旧 #39–#44（建议**保留**的防御性设计） | — | **结论一致**，已并入 §9.5（`resolveRedirect`、`requestSequence`、`progress.ts` 三层、401 续期、`AuthGuard`） |

### B.4 本次相对该文件**新增**的发现（摘要）

| 新增发现 | 编号 | 级别 |
| --- | --- | --- |
| **`--fs-md` 变量不存在**（自动化全仓库变量集合求差的**唯一缺口**） | FE-CSS-01 | P1 |
| **首屏 loading 选择器不匹配**（`#app-loading` vs `.app-loading` → 容器级规则完全不生效，两份实现已漂移） | FE-CSS-02 | P1 |
| **`message.error(getApiErrorMessage(...))` 重复 82 次 / 30 文件**（量级证据） | FE-PAGE-12 | P2 |
| **`AssetDialogs` 已有 `useDialogLoad` 却无人使用**（"可抽取却未抽取"的关键反证） | FE-PAGE-06 | P2 |
| **`?.replace('T',' ')` 16 处 / 13 文件，而项目已装 dayjs 却只用 2 次** | FE-PAGE-13 | P3 |
| **19 个"导出但仅本文件内使用"的符号**（逐符号计数） | FE-INF-04 | P3 |
| **深色主题下登录页 GitHub hover 近黑不可见**（`security.css` 注释描述的同一个 bug 的**未修复副本**） | FE-CSS-10 | P2（缺陷本身为 P1） |
| **3 处 eslint-disable 是空操作但承载真实信息** | CFG-04 | P0（建议改写） |
| **`public/favicon.ico` 0.95 MB**，且位于**首屏渲染路径**（`index.html:107` 用它做加载图标） | FE-DEP-01（§7.2） | P2 |
| **后端 `PUT /{id}/developer`（分配开发负责人）前端连 api 函数都没有** | FE-INF-01 与 §8.4 | P2（**未完成功能，非死代码**） |

---

## 附录 C：本报告条目索引（按风险等级）

> 便于你逐项审批时检索。**全部条目默认「待确认」。**

**P0（明确冗余，删除风险极低）**
CFG-01（`html2canvas`）、CFG-04（失效 `eslint-disable`※）、CFG-05（`org.kariya` 日志器）、
BE-CMN-02（未使用 import）、BE-AUTH-06（`normalizeIssuer` 逐字重复）、BE-AUTH-09（`SessionGrant.onboarding`）、
FE-INF-02（`SmartTableSelection`）、FE-PAGE-01（`unifiedProvider` 死变量）、FE-DEP-01（同 CFG-01）

> ※ CFG-04 就"无效指令"而言是 P0，但涉及**注释语义**，报告建议**改写而非直接删**。

**P1（明显过度设计 / 静默失效，适合简化）**
BE-CMN-01（`actor()` 无请求级缓存 + filter 重复查询）、BE-SEC-01/02/04（重复 token 解析 / JWT 死 claim / 8 份 SpEL）、
BE-AUTH-01/02/03/04/05/07/08/10/11/12/17（认证链路的重复与死字段）、
BE-APR-01（我已处理列表与计数口径不一致）、
FE-CSS-01（`--fs-md` 不存在）、FE-CSS-02（首屏选择器不匹配）、
CFG-09 / FE-CMP-01（glob 收进 16 个非页面组件 → 选中即白屏）、FE-PAGE-03 / FE-CMP-02（演示页与三个单点组件）

**P2（疑似过度设计 / 需业务确认）**
CFG-02/03/06/07/08/10/11、BE-CMN-03/04、BE-AGT-01/03/06/09、BE-LOG-02/06/08/09/10/13、
BE-APR-05/07/11、BE-NTF-01/02/03、BE-REQ-01/02、BE-AUTH-13/14/15/18、
BE-SEC-03、BE-DA-01、BE-VAL-12/18/19/20/21、
FE-PAGE-04/06/07/08/12、FE-INF-01（`assignRequirementTransaction`）、FE-DEP-04/05/07、
FE-CSS-03/04/05/10

**P3（涉及安全/并发/一致性/关键业务，默认建议保留；仅在明确批准后简化）**
BE-CMN-01（另一视角）、BE-AGT-02/04/05/07/08、BE-LOG-01/03/04/05/07/11/12/14、
BE-APR-02/03/04/06/08/09/10/12、BE-NTF-04/05/06、BE-REQ-03/04/05/06、
BE-AUTH-16/19、BE-VAL-01~09/11/13~17/22~25、
FE-PAGE-02/05/09/10/11/13、FE-CMP-03/04/05/06/07、FE-INF-03/04/05/07/08/09/10/11、
FE-DEP-02/03/06/08、FE-CSS-06/07/08/09/11/12/13/14

---

> **本报告到此结束。**
> 我没有修改任何现有业务代码；所有条目均为「待确认」。
> **请逐项审批（保留 / 简化 / 合并 / 删除 / 待确认）**，我据此再进入代码修改阶段。
> 特别希望你优先回答 **§8.1 的 Q1~Q4**——它们各自连带决定多条其他条目。
