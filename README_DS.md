# README_DS — Kariya Admin 项目全景说明书与健康度评估

> 本文档由一次对仓库的完整通读 + 实测构建 + 逐文件交叉核对产出。
> 所有结论都标注了 `路径:行号`，可以直接跳过去复核。
> **凡是没能在代码或实测中确认的内容，都明确写了「未验证」**，不做猜测性陈述。
>
> 生成时间基准：仓库当前工作区（HEAD = `518856d`，但工作区有 96 个未提交改动，见第 3 章）。

---

## 0. 先读这一节（若只看一页）

### 0.1 一句话结论

这是一个**成熟度远超普通"vibe coding 产物"的后台管理系统**：认证/会话/权限/数据范围四层做得相当扎实，代码可编译、可离线构建、TypeScript 严格模式零 `any`，注释质量罕见地高（大量注释在解释"为什么这样设计"和"以前踩过什么坑"）。

但它**目前还不满足你"内网快速迭代移植"的目标**，卡点不在架构，而在四件事：

| # | 卡点 | 严重度 | 说明 |
|---|---|---|---|
| 1 | **生产 profile 起不来** | 🔴 阻断 | `application-prod.yml` 只有 18 行，`lbl.security.*`（含 `jwt-secret`）等必填项全部缺失，而 `SecurityProperties` 是无默认值的 record → 绑定失败/密钥为空 |
| 2 | **默认 profile 指向公网** | 🔴 阻断 | 默认 `spring.profiles.active: local`，而 `application-local.yml` 连的是**公网服务器** `124.222.152.55:3308/6381`，且 AI 默认打 `api.deepseek.com`。真正的内网/断网环境直接不可用 |
| 3 | **测试覆盖 ≈ 0** | 🔴 高风险 | 全部测试只有 **1 个文件 53 行**，而且它不是测试（断言恒真）。另有 3 个 Agent 测试类**已被暂存删除**。这意味着你后续任何改动都没有安全网 |
| 4 | **96 个改动未提交 + 14 份文档被删** | 🟠 高 | 工作区有 96 个文件改动（含 net **-7,071 行**删除）从未提交；`README.md` 本身未纳入版本控制；`docs/` 下 14 份文档（约 6,000 行，含 736 行《内网移植指南》）已被删除但**仍完整保存在 git 中，可一键恢复** |

### 0.2 你可能不知道的两件事

**第一件：这个项目的评估和移植文档，之前已经写过一遍，然后被删掉了。**

`git` 里躺着一整套 `docs/` 文档（HEAD 中仍在，工作区被删）：

| 文件 | 行数 | 内容 |
|---|---|---|
| `docs/INTRANET-MIGRATION.md` | 736 | **内网移植指南**（离线构建、仓库镜像、nginx、WebSocket 代理、密钥处理、迁移 checklist） |
| `docs/ASSESSMENT.md` | 763 | **项目评估报告**（已指出公网 MySQL、Jasypt 口令、测试缺失等问题） |
| `docs/DEVELOPMENT.md` | 976 | 二次开发指南 |
| `docs/DATA-SCOPE-ROLE-DEPT.md` | 865 | 数据范围与角色部门设计 |
| `docs/ARCHITECTURE.md` | 578 | 架构文档 |
| `docs/AUTH.md` | 545 | 认证设计 |
| `docs/THIRD-PARTY-LOGIN.md` | 505 | 第三方登录 |
| `docs/README.md` | 309 | 文档索引 |
| `docs/THEME.md` | 265 | 主题体系 |
| `docs/ARCHIVE-removed-code.md` | 171 | 已删代码存档 |
| `docs/AI-AGENT.md` | 14897 B | Agent 设计（`application-local.yml:155` 还在引用它） |
| `docs/APPROVAL-ARCHITECTURE.md` | 82 | 审批架构 |
| `docs/UIAS-INTEGRATION.md` | 40 | 企业统一认证接入 |
| `docs/sql/sample-notifications.sql` | 63 | 示例通知数据 |

恢复命令（**只读操作，不会覆盖你现有的任何文件**，因为这些文件在工作区确实已不存在）：

```bash
git checkout -- docs/
```

**第二件：你的 `.gitignore` 是乱码，而且 `README.md` 没进版本控制。**
`.gitignore` 用 UTF-8 打开是乱码（文件本身是 GBK 编码），功能上不影响 git 生效，但任何人和任何工具读它都会看到乱码。另外 `README.md`、`frontend/src/api/approvals.ts`、`backend/.../realtime/RealtimeEnvelope.java` 等 20 个文件是**未跟踪**状态 —— 也就是说，**你现在读的那份 754 行 README 根本没被 git 管理，一次 `git clean -fd` 就没了**。

### 0.3 我应该先做什么（建议顺序）

1. `git checkout -- docs/` 把文档找回来（它们包含对你最有用的内网移植清单）。
2. **先提交一次**，把当前 96 个改动固化成一个安全的还原点（现在你没有任何已知良好的提交点）。
3. 修 `application-prod.yml`（补全 `lbl.security.*` + `lbl.file-upload` + `lbl.log.retention`），让 prod profile 能启动。
4. 把 `application-local.yml` 从"连公网"改成"连本机/内网"，或新增一个 `application-intranet.yml`。
5. 再谈二次开发 —— 架构本身是好的，不需要重写。

---

## 1. 项目概况

| 项 | 值 |
|---|---|
| 定位 | 前后端分离的通用后台管理系统（RBAC + 数据范围 + 审批 + 通知 + 实时 + AI Agent） |
| 后端 | Java 17、Spring Boot 3.4.x、Spring Security、MyBatis-Plus、Jasypt、Actuator |
| 数据 | MySQL 8+、Redis（会话、限流、验证码、Pub/Sub） |
| 前端 | React 18、TypeScript 5.8（`strict: true`）、Vite 6、React Router 7、Redux Toolkit、Ant Design 5 |
| 实时 | 原生 Spring WebSocket + Redis Pub/Sub（支持多实例） |
| AI | OpenAI Chat Completions 兼容协议 + SSE + 工具调用框架 |
| 可视化 | ECharts、AntV G6、Monaco Editor |
| 代码量 | 后端主代码 **182 个文件 / 10,401 行**；后端测试 **1 个文件 / 53 行**；前端 **100 个 ts/tsx 文件 / 7,256 行**（含 CSS 共 127 文件 / 9,259 行） |
| 数据库 | 14 张表（`init_schema.sql` 228 行）；种子数据 66 行 |
| 分层统计 | 19 个 Controller、21 个 Service、16 个 Mapper、12 个 Entity |

### 1.1 目录结构

```text
kariya_admin/
├─ backend/
│  ├─ pom.xml                          # 单模块 Maven 工程（无 mvnw wrapper）
│  └─ src/
│     ├─ main/java/org/lbl/
│     │  ├─ LblShitApplication.java     # 启动类（@EnableScheduling）
│     │  ├─ agent/                      # AI Agent 框架（端口-适配器）
│     │  ├─ approval/                   # 部门变更审批 + 统一待办
│     │  ├─ auth/                       # 登录、注册、会话、第三方登录、开户
│     │  ├─ common/                     # Result/PageResult/异常/Excel/工具
│     │  ├─ config/                     # SecurityProperties、WebSecurityConfig 等
│     │  ├─ file/                       # 暂存文件上传
│     │  ├─ notification/               # 站内通知
│     │  ├─ realtime/                   # WebSocket + Redis 广播
│     │  ├─ security/                   # JWT、过滤器、AccessPolicy（数据权限核心）
│     │  └─ system/                     # user / role / menu / dept / log
│     ├─ main/resources/
│     │  ├─ application.yml             # 基础配置（含 Jasypt 口令）
│     │  ├─ application-local.yml       # ⚠️ 未被 git 跟踪，含真实凭据
│     │  ├─ application-dev.yml         # 空模板
│     │  ├─ application-prod.yml        # ⚠️ 不完整，缺 lbl.security.*
│     │  └─ db/{init_schema.sql,init_data.sql}
│     └─ test/java/org/lbl/JasyptEncryptorTest.java   # 唯一的"测试"
├─ frontend/
│  ├─ vite.config.ts                    # 极简：只有 react 插件 + port
│  ├─ .env.development                  # VITE_API_BASE_URL=http://localhost:8080/api
│  ├─ .env.production                   # VITE_API_BASE_URL=/api
│  ├─ dist/                             # 已构建产物（136 文件 / 20.43 MB）
│  └─ src/
│     ├─ api/                           # 12 个文件：一个后端资源一个文件
│     ├─ components/                    # 15 个组件（ai/ SmartTable/ CodeViewer/ ...）
│     ├─ layouts/AppLayout.tsx          # 工作台外壳（侧栏/顶栏/通知/实时/续期）
│     ├─ pages/                         # 23 个页面
│     ├─ permission/Permission.tsx      # 按钮级权限
│     ├─ router/                        # 静态骨架 + DynamicPage 动态出口
│     ├─ services/                      # authSession / RealtimeClient / progress ...
│     ├─ store/                         # Redux（auth + notifications）
│     ├─ theme/                         # Design Token + 亮暗主题
│     ├─ types/                         # 前后端契约
│     └─ utils/request.ts               # Axios 实例 + 401 单次续期
└─ README.md                            # ⚠️ 未跟踪；README_DS.md 为本文档
```

### 1.2 架构总览

```text
浏览器 (React SPA)
  │
  ├─ REST/JSON ──> Controller ──> Service ──> Mapper ──> MySQL
  │                    │             └───────────────> Redis（会话/限流/验证码）
  │                    ├─ @PreAuthorize        ← 第一层：功能权限（能不能做这类动作）
  │                    └─ AccessPolicy         ← 第二层：数据权限（能对哪些数据做）
  │
  ├─ SSE ────────> AgentChatController ──> AgentRunner ──> ModelGateway
  │                     (POST /api/agent/chat)   │              └─> OpenAI 兼容网关
  │                                              └─ ToolExecutionService ──> 业务 Service
  │
  ├─ WebSocket ──> RealtimeGateway <── Redis Pub/Sub <── NotificationService
  │   /ws/realtime            (kariya-admin:realtime:events)
  │
  └─ Refresh Cookie ──> POST /api/auth/refresh ──> 新的 access token
      (lbl_refresh, HttpOnly, Path=/api/auth)
```

四条不可动摇的设计边界（代码里贯彻得很彻底）：

1. **JWT 只证明"这个令牌属于哪个会话"**，真实会话在 Redis。所以"登出/踢设备/改密/停用"能立刻生效（见 §5.4）。
2. **前端菜单只决定"看不看得见"**，安全和数据边界完全在后端 `@PreAuthorize` + `AccessPolicy`。
3. **通知先落 MySQL，事务提交后再发实时事件**；实时通道只是"提醒你去拉数据"，不是数据源（见 §7.5）。
4. **Agent 不直接碰 Mapper / Controller / SecurityContext**，只能通过显式注册、执行前再次鉴权的工具调用业务 Service（见 §8）。

---

## 2. 实测验证结果（我真的跑了构建）

环境实测到的工具链：

| 工具 | 版本 | 是否满足项目要求 |
|---|---|---|
| Java | 17.0.12 (Oracle) | ✅ 项目要求 17 |
| Maven | 3.9.14（`D:\software\apache-maven`） | ✅ |
| Node | v24.14.0 | ✅ `package.json` 要求 `>=20.19 <25` |
| npm | 11.9.0 | ✅ |
| Maven 本地仓库 | `D:\Repository\Maven`（见 `D:\software\apache-maven\conf\settings.xml:55`） | ✅ 依赖已缓存 |
| Maven Wrapper | **不存在**（无 `mvnw` / `.mvn`） | ⚠️ 见 §12 |

### 2.1 后端：编译通过 ✅

| 项目 | 结果 |
|---|---|
| 命令 | `mvn -o -B -DskipTests compile`（**离线**，`-o`） |
| 结果 | **BUILD SUCCESS**，Compiling **182 source files**，耗时约 10.7 秒 |
| 警告 | 仅 `UserService.java` 使用/覆盖了已过时 API（deprecation），非错误 |
| 离线可行性 | ✅ **完全离线可编译**，`D:\Repository\Maven` 里已有全部依赖 |

> **为什么是在副本里编的**：本会话的沙箱进程运行在 Windows **低完整性级别（Low IL）**，而 `backend/`、`frontend/`、`.git/` 这些**已存在**的目录是 Medium IL，低完整性进程无法写入（这是 Windows 强制完整性控制，不是 DACL 权限问题）。因此我把源码复制到工作区内新建的目录再编译。**这是本会话沙箱的限制，不是项目缺陷**，在你自己的终端里直接 `mvn compile` 不会遇到。

### 2.2 前端：类型检查通过 ✅

| 项目 | 结果 |
|---|---|
| 命令 | `npx tsc --noEmit -p tsconfig.app.json` |
| 结果 | **exit 0，零错误、零警告** |
| `strict` | `tsconfig.app.json` 中 `"strict": true` |
| `any` 使用 | 全 `src` 下 **0 处**（`: any` / `<any>` / `as any` 全部零命中） |

`package.json` 的 build 脚本是 `tsc --noEmit -p tsconfig.app.json && vite build`，所以**类型检查这一半已经被验证通过**。

### 2.3 前端生产构建：本会话无法验证（沙箱限制）

`npx vite build` 失败，报：

```
Error: spawn EPERM
    at ensureServiceIsRunning (.../esbuild/lib/main.js:1978:29)
```

这是本会话沙箱**明确记录的边界**：受限于命名管道，Node 的 `child_process.spawn` 无法以管道 stdio 启动子进程（esbuild 需要一个常驻 service 进程，必然走管道）。**这与项目代码无关。**

间接证据表明生产构建是可用的：`frontend/dist/` 已存在，**136 个文件、20.43 MB**，构建时间 `2026/10/3 17:01`（就在今天），`index.html` 与 `assets/`、`monaco/` 齐全。

> ⚠️ 但 `dist/` 里的产物是**改动之前**的版本（工作区还有 96 个未提交改动），发布前必须重新 `npm run build`。

### 2.4 基础设施连通性实测（只做 TCP 连接，未认证、未查询）

| 目标 | 配置位置 | 实测 |
|---|---|---|
| `124.222.152.55:3308`（MySQL, root） | `application-local.yml:3-5` | **REACHABLE** |
| `124.222.152.55:6381`（Redis） | `application-local.yml:25-27` | **REACHABLE** |
| `api.deepseek.com:443`（默认 LLM 网关） | `application-local.yml:157` | **REACHABLE** |

**这三个"REACHABLE"正是内网移植的核心矛盾**：默认 profile 依赖公网数据库、公网 Redis 和公网大模型 API。真正隔离的内网环境里，这三条全断，系统**登录都做不了**（会话在 Redis）。详见 §12。

---

## 3. 版本控制与工程现状（这是你"掌控力弱"的直接原因）

这一节可能是全文对你最有实际价值的部分。

### 3.1 提交历史没有信息量

```
518856d  2026年9月29日 17:46:23
f40c0d4  2026年9月29日 14:56:20
faa950e  2026年9月28日 22:40:45
765476b  2026年9月28日 20:37:10
9eb293d  2026年9月27日 23:04:47
de07f67  2026年9月23日 18:03:06
b9e4801  2026年9月23日 00:55:34
afd96e2  2026年9月22日 18:08:19
64d5678  2026年9月21日 18:08:50
```

**9 个提交，commit message 全部是一个时间戳。** 这意味着你无法通过 `git log` 回答"这个功能是什么时候、为什么加的"，`git bisect` 也基本失效。这是 vibe coding 最典型的副作用：改动量大、迭代快、但历史不可读。

### 3.2 工作区有 96 个未提交改动（含一次大规模删除）

```
git diff HEAD --stat  →  76 files changed, 842 insertions(+), 7071 deletions(-)
git status --short    →  96 个条目
```

被删除的 7071 行里，绝大部分是 `docs/`（约 6,000 行）和 3 个测试类。当前状态分三类：

| 状态 | 内容 |
|---|---|
| **已暂存删除**（`D ` 在索引里） | 3 个 Agent 测试类：`AgentRequestMapperTest.java`、`AgentRunnerTest.java`、`ToolExecutionServiceTest.java` |
| **未暂存删除**（` D`，工作区已无文件） | `docs/` 全部 14 个文件（见 §0.2 表格） |
| **未跟踪**（`??`） | `README.md`、`.dsh-acl-report/`，以及 **20 个新源码文件/目录**，包括 `realtime/RealtimeEnvelope.java`、`RealtimeEventPublisher.java`、`RealtimeEventSubscriber.java`、`RealtimeSubscriptionStarter.java`、`auth/session/RefreshCookieFactory.java`、`SessionLifetimePolicy.java`、`approval/task/`、`UserExportService.java`、`UserPasswordService.java`、`UserSessionAdministrationService.java`、`frontend/src/services/authSession.ts`、`frontend/src/pages/account/approvals/`、`frontend/src/components/common/GlobalSearch.tsx`、`frontend/src/api/approvals.ts` 等 |

**解读**：你最近一轮迭代（多实例 Realtime 重构、会话生命周期抽类、统一待办中心、用户导出/改密/会话管理拆分到独立 Service）**全部处于未提交状态**。这些改动质量不错，但它们只存在于你的磁盘上——没有提交、没有备份点、无法回退、无法对比。

### 3.3 `.gitignore` 与凭据跟踪状态（好消息）

| 检查 | 结果 |
|---|---|
| `application-local.yml` 是否被 git 跟踪 | ❌ **从未被跟踪**（`git log --all -- application-local.yml` 为空） |
| Git 历史中是否出现过 GitHub Client Secret | ❌ 未出现 |
| Git 历史中是否出现过 Google Client Secret | ❌ 未出现 |
| Git 历史中是否出现过 MySQL/Redis 的 `ENC(...)` 密文 | ❌ 未出现 |
| Git 历史中是否出现过公网 IP `124.222.152.55` | ✅ **出现过**（在 `docs/ASSESSMENT.md`、`docs/INTRANET-MIGRATION.md` 中被引用） |
| 被跟踪文件里的明文口令 | ⚠️ `application.yml:15` — Jasypt 主口令 `kariya` **明文入库** |

`.gitignore` 确实生效了（`application-local.yml` 在忽略列表中且确实未被跟踪），所以**你的第三方登录密钥和数据库口令没有泄露到 git 仓库**。但有两个真实问题：

1. **Jasypt 主口令 `kariya` 是明文提交的。** `ENC(...)` 的安全性完全依赖这个口令。口令公开 = 只要拿到 `application-local.yml` 就能解密 MySQL root 口令、Redis 口令和 LLM API Key。`application.yml:12` 的注释自己都写了"该值仅供开发环境使用；生产环境请改用环境变量或 Kubernetes Secret"，但**代码里根本没走环境变量**。
2. **`application-local.yml` 被 gitignore 是一把双刃剑。** 好处是凭据不入库；坏处是**任何新克隆仓库的人都无法启动项目**，而仓库里唯一一份可用的数据库/Redis/外部登录配置就在这个"不该提交"的文件里。这正是"内网移植"和"快速迭代"的直接摩擦点。

### 3.4 一个会影响你排查问题的细节

`.gitignore` 文件本身是 **GBK 编码**，用 UTF-8 阅读会全部乱码（例如 `# 鍓嶅悗绔垎绂婚」鐩?`）。它功能正常，但如果后续有人用工具自动改写这个文件，很容易把中文注释写坏或者误改忽略规则。

---

## 4. 完成度与代码质量评估

### 4.1 功能完成度

| 功能域 | 完成度 | 证据与说明 |
|---|---|---|
| 本地密码登录 | 🟢 完成 | `AuthService.login`：BCrypt + 失败限流 + 强制改密 + 登录日志 |
| 服务端会话（可吊销） | 🟢 完成 | Redis 会话 + `authVersion` + sid；JWT 只带 `sid`/`authVersion` |
| 静默续期 | 🟢 完成 | `RefreshCookieFactory`（HttpOnly/Path=/api/auth/SameSite=Lax）+ 前端单飞 refresh |
| RBAC 权限码 | 🟢 完成 | 24 个业务权限码，`@PreAuthorize` 全覆盖（§5.3 有全表） |
| 数据范围（5 种 + CUSTOM） | 🟢 完成 | `AccessPolicy` 428 行，含提权防护、平台级权限保护、默认拒绝 |
| 动态菜单 → 动态路由 | 🟢 完成 | 后端递归 CTE 出菜单树，前端 `import.meta.glob` 自动注册页面 |
| 部门树 | 🟢 完成 | `ancestors` 物化路径，增删改带环检测与越界校验 |
| 菜单管理 | 🟢 完成 | 类型/父级/routePath/component/permissionCode 校验 + 内置保护 |
| 用户管理 + 导出 | 🟢 完成 | 分页、表单、用户名可用性、SXSSF 流式导出、设备会话管理 |
| 操作审计 | 🟢 完成 | `@OperationLog` AOP，独立事务写审计（业务回滚也留痕）、IP/UA/RequestId/耗时 |
| 日志保留清理 | 🟢 完成 | `LogRetentionJob` 定时分批物理删除 |
| 站内通知 | 🟢 完成 | 落库 + 事务提交后推送 + 未读数 + 已读 |
| 多实例实时推送 | 🟢 完成 | 一次性 ticket 握手 + Redis Pub/Sub 广播 + 跨实例强制下线 |
| 部门变更审批 | 🟢 完成 | 两阶段步骤（原部门→目标部门）、行锁防并发、禁止自审、撤销/拒绝 |
| 统一待办中心 | 🟢 完成 | `pending/processed/mine` 三种视图 |
| 第三方登录（GitHub/Google/微信） | 🟡 可用但需凭据 | 流程完整（PKCE + state 一次性 + 绑定/开户）；凭据只在 local profile |
| 企业统一认证 UIAS | 🔴 **空壳** | `EnterpriseDirectoryPort` / `UiasAssertionConsumer` **零实现类**，`isUsable()` 恒 false，路径不可达 |
| AI Agent 框架 | 🟡 框架完成、业务为空 | 模型调用/SSE/工具注册/权限/参数校验/超时/取消/风险抽象齐全，但 **0 个 `AgentTool` 实现**，`ApprovalPolicy.REQUIRED` 无确认协议 |
| 文件上传 | 🟡 仅暂存 | 只能上传到暂存区换 token，没有业务侧消费示例 |
| 数据迁移工具 | 🔴 缺失 | 手工 SQL 文件，无 Flyway/Liquibase |

### 4.2 做得好的地方（这些不是客套）

1. **认证/授权分层非常干净。** `JwtAuthenticationFilter` 只做认证，权限码走 `@PreAuthorize`，数据范围走 `AccessPolicy`。三层各司其职，没有出现"在 Mapper 里读 SecurityContext"这类常见污染。
2. **真服务端会话 + 双保险吊销。** 安全信息变化时既 `authVersion + 1` 又 `sessions.removeAll()`（`UserPasswordService.java:58-60,80-82`、`UserService.java:204-206`），旧 JWT 即使未过期也失效。
3. **提权防护是立体的，不是一层。** `canManageUser` 要求**严格权限超集**（`containsAll && !equals`），且逐角色做 `grantFits` 数据范围比对；`canAssignRole` 拒 `builtin` 与 `super_admin`；`requireAssignableRoles` 按目标部门重算范围；`requireGrantableMenus` 只能授出自己有的菜单；平台级权限（菜单管理、重置密码）有单一真相来源 `PLATFORM_ONLY_PERMISSION_CODES` 且写入侧二次拦截。
4. **数据范围默认拒绝。** `applyUserScope` 在既非 dept 也非 self 时执行 `condition.apply("1 = 0")`（`AccessPolicy.java:143`）——查不到比查多了安全。
5. **反 CSRF / 反 XSS 的细节到位。** 刷新 Cookie 用 HttpOnly + `Path=/api/auth`（业务请求根本不带它）；access token 只放 Redux 内存不落 localStorage；开放重定向有 `resolveRedirect` 用 `URL` 解析做 origin 比对（`router/menu.ts:55-65`）。
6. **登录防爆破设计细致。** 账号+来源双维度（5 次 / 20 次，15 分钟窗口，15 分钟锁），IP 只存 `SHA-256` 摘要不用明文，且**外部身份绑定路径复用了同一套计数**（这是最容易漏的旁路）。
7. **`TrustedProxyResolver` 正确处理了 `X-Forwarded-For`**：从右往左取第一个不可信地址（nginx realip 语义）、按字节做 CIDR、还原 IPv4-mapped IPv6、配置写错只告警并降级为"不信转发头"。多数同类项目这里都是错的。
8. **"事务提交后再推送"是真的实现了**，机制是 `TransactionSynchronizationManager.registerSynchronization(...).afterCommit()`（`NotificationService.java:41-46`），不是空口注释。
9. **注释质量罕见。** 大量注释在解释**为什么**以及**以前踩过什么坑**，例如：
   - `DynamicPage.tsx:83-93` 解释为什么不写死 `/home`（否则用户会被 403/404 死循环困住）；
   - `componentRegistry.tsx:27-31` 解释为什么必须缓存 `React.lazy` 结果（否则页面反复卸载重建）；
   - `LoginSession.java:21-33` 解释 `rememberMe` 为什么必须放进开户态会话（否则用户勾选被静默丢弃）；
   - `AuthGuard.tsx:47-55` 解释为什么开户态要在跳转前保持 Loading（否则会白发一批必然 403 的请求 + 建立注定失败的 WebSocket 重连）。
   
   这类注释的价值在于：**它们把"设计意图"和"历史教训"固化下来了**，这正是你现在觉得"看不懂代码"时最需要的东西。
10. **前端类型纪律严格。** `strict: true`，全项目 0 处 `any`，`types/` 里维护前后端契约，`api/` 一资源一文件。

### 4.3 做得不好的地方

| 问题 | 具体表现 | 影响 |
|---|---|---|
| **测试几乎为零** | 全部测试只有 `JasyptEncryptorTest.java`（53 行）。而它**不是测试**：`:33` 把 `plaintext` 写死成 `""`，所以 `:39` 的 `assertEquals` 恒真；它还是 `@SpringBootTest`，需要真实 MySQL/Redis 才能跑 | 🔴 任何重构都没有安全网。这是"不敢改代码"的根因 |
| **刚删掉了仅有的业务测试** | 3 个 Agent 测试类（`AgentRunnerTest` 97 行、`ToolExecutionServiceTest` 99 行、`AgentRequestMapperTest` 39 行）处于**已暂存删除**状态 | 🔴 Agent 模块从此完全无测试，而它恰恰是行为最复杂、最需要测试的模块 |
| **上帝类** | `ExternalLoginService` 493 行、`AccessPolicy` 475 行、`UserService` 386 行、`RoleService` 281 行、`GlobalExceptionHandler` 271 行、`CaptchaService` 228 行。其中 `AccessPolicy` 一个文件同时承担：身份快照、范围计算、用户/角色/部门/菜单四类授权判定、平台级权限声明、2 个内部 record —— **单文件 8 个职责** | 🟠 单文件职责过多，定位和改动都费劲 |
| **无集中式会话失效入口** | `setAuthVersion(getAuthVersion()+1)` 内联在 3 处（`UserPasswordService.java:58,80`、`UserService.java:204`），没有 `bumpAuthVersion()` 之类的方法 | 🟠 新增"需要踢人"的场景极易漏掉版本号或漏掉 `removeAll` |
| **会话失效策略不一致** | 管理员改用户**任何字段**（含手机号）→ `authVersion+1` + 踢掉该用户全部设备；而用户自助改联系方式不失效 | 🟠 "改个邮箱就被踢下线"是明显的体验缺陷 |
| **无数据库迁移工具** | 只有 `init_schema.sql`（面向空库）+ 注释说"已有库请用专用迁移工具"，但仓库里没有迁移脚本 | 🟠 内网升级靠人工执行 SQL，顺序错就出事 |
| **无 Maven Wrapper** | 没有 `mvnw` / `.mvn` | 🟠 内网构建强依赖预装 Maven + 私有镜像 |
| **死代码 / 半成品残留** | UIAS 两个端口零实现；JWT 的 `principalType` claim 只写不读；`lbl.security.captcha-enabled` 被注释引用但 `SecurityProperties` 里根本没这个字段；`CaptchaService` 注入 `SecurityProperties` 却从未使用；`SessionService.deserialize` 保留旧竖线格式分支；`ToolArgumentBinder.bind(Map)` 无调用者 | 🟡 增加阅读负担，也是"看不懂"的来源之一 |
| **文档与代码漂移** | `README.md:386,400` 说 ticket TTL = **30 秒**，实际 `RealtimeTicketService.java:21` 是 **60 秒**；`README.md:476` 说 `AgentRun` "供测试、审计"，但 `AgentRunObserver` **零实现**、无任何持久化；`application-local.yml:155` 指向 `docs/AI-AGENT.md`，而该文件已被删除 | 🟡 会让人按错误前提去排查问题 |
| **`.gitignore` 编码乱码** | GBK 编码 | 🟡 工具改写易坏 |
| **单一提交点缺失** | 96 个改动 + 9 个无语义提交 | 🔴 见 §3 |

### 4.4 综合评级

| 维度 | 评分 | 说明 |
|---|---|---|
| 架构设计 | **A-** | 分层清晰、边界明确、安全模型完整，明显高于同类个人项目 |
| 安全性 | **B+** | 认证/授权/提权防护/代理/CSRF 都考虑到了；扣分在 Jasypt 口令明文、prod 配置缺失、SSO 边界靠字符串约定 |
| 代码可读性 | **A-** | 命名一致、注释极佳、类型严格；扣分在上帝类与死代码 |
| 可测试性 / 测试覆盖 | **F** | 约等于零，且刚删掉仅有的业务测试 |
| 构建可复现性 | **B-** | 离线编译已验证可行；扣分在无 wrapper、无迁移工具、prod 配置不完整 |
| 内网移植就绪度 | **D** | 默认连公网三件套（MySQL/Redis/LLM），prod profile 起不来 |
| 版本控制健康度 | **D** | 96 个未提交改动、无语义提交信息、README 未跟踪、文档被删 |
| **总评** | **B（有极大潜力的半成品）** | **代码质量足以支撑二次开发，工程习惯不足以支撑"快速迭代"** |

---

## 5. 认证、会话与权限体系

> **路径简写约定**（本章及以后统一使用）：
> `BE/` = `backend/src/main/java/org/lbl/`　　`RES/` = `backend/src/main/resources/`　　`FE/` = `frontend/src/`

### 5.1 一次密码登录的完整链路

用户在 `FE/pages/login/index.tsx` 填用户名密码 → `FE/api/auth.ts` 发 `POST /api/auth/login`。

后端按以下**精确顺序**执行（`AuthService.login`，`BE/auth/service/AuthService.java:45-73`）：

| 步骤 | 位置 | 做什么 |
|---|---|---|
| 1 | `WebSecurityConfig.java:58` | `/api/auth/login` 在 `permitAll` 白名单里（`/api/auth/**` 整段放行） |
| 2 | `JwtAuthenticationFilter.java:84-88` | `shouldNotFilter` 让 `/api/auth/**`（除 `/api/auth/onboarding/**`）跳过 JWT 过滤器 |
| 3 | `AuthController.java:53-64` | `@Valid` 校验 `LoginRequest`（username `@NotBlank`+`@Size(64)`，password `@Size(72)`），失败 → 400 |
| 4 | `AuthService.java:46-48` | username trim；算 `loginKey`（小写归一 + 截断 64）；算 `currentSource` = `base64url(SHA-256(客户端IP))` |
| 5 | `:49-51` | `LoginAttemptGuard.isLocked` → 锁定则 **429** |
| 6 | `:53-56` | 查 `sys_user` 与 `sys_local_credential`；判定 `user.status==1 && credential.enabled==1 && BCrypt.matches(...)`。**用户不存在 / 已停用 / 无本地凭据 / 密码错，走同一分支、同一对外文案**（防用户名枚举） |
| 7 | `:57-62` | 失败：`recordFailure` → 写 `sys_login_log` → 若已触发锁定返回 **429**，否则 **400** |
| 8 | `:65` | 成功：清空两个维度的失败计数 |
| 9 | `:66` | `PasswordChangePolicy.requiresChange` 判断是否需要强制改密 |
| 10 | `:67-68` | 写 `sys_login_log`（成功） |
| 11 | `:69-70` | `SessionService.create` → 在 Redis 建会话，拿到 `sid` |
| 12 | `:71` | `JwtService.issue(sid, username, authVersion)` → 签发 access token |
| 13 | `AuthController.java:58-62` | 再解析 token 取 `sid`；若浏览器旧 Cookie 里的 sid 与本次不同，**顺手删掉旧会话**（同一浏览器只保留一个会话）；下发刷新 Cookie |

**注意：登录不校验图形验证码。** `CaptchaService` 只被 `RegistrationService.java:65`（`/register`）调用，`AuthService.login` 里没有验证码逻辑。防爆破完全靠 `LoginAttemptGuard`。

#### JWT 里到底装了什么

`BE/security/jwt/JwtService.java:26-39`：

```java
// 登录 / 刷新 走这个重载
Jwts.builder().subject(username)
    .claims(Map.of("sid", sid, "authVersion", authVersion))
    .issuedAt(now).expiration(now + accessTokenMinutes * 60).signWith(key)

// 注册 / 第三方登录 走这个重载（多一个 principalType）
.subject(onboarding ? "onboarding:" + onboardingId : username)
.claims(Map.of("sid", sid, "authVersion", ..., "principalType", ...))
```

| 字段 | 值 |
|---|---|
| `sub` | 用户名；开户态为 `onboarding:{onboardingId}` |
| `sid` | Redis 会话 ID（**JWT 的全部意义就是携带它**） |
| `authVersion` | 用户授权版本号，用于"改了角色/密码后旧令牌立即失效" |
| `iat` / `exp` | 签发时间 / 过期时间 |
| `principalType` | 仅在注册/外部登录路径写入 |
| 算法 | HS256（`Keys.hmacShaKeyFor` 派生密钥） |
| **没有** | `iss`、`aud`、`jti`、`kid`、角色、权限码 |

⚠️ 两个小瑕疵：`principalType` 这个 claim **全仓库只写不读**（死字段）；两个 `issue` 重载产出的 claim 集合不一致，导致同一个会话存在两种令牌格式。

#### Redis 里真实存了什么

`LoginSession`（`BE/auth/session/LoginSession.java:8-12`）是一个 19 字段 record，序列化成 JSON 存进 Redis：

```
principalType, userId, username, authVersion, rememberMe, authMethod, providerKey,
onboardingId, issuer, subject, displayName, email, employeeNo, passwordChangeRequired,
createdTime, lastActiveTime, loginIp, userAgent
```

会话相关的 Redis key（**这是理解整个登录体系的关键**）：

| Key | 值 | TTL |
|---|---|---|
| `auth:session:{sid}` | `LoginSession` 的 JSON | 空闲 TTL（见下表） |
| `auth:session:absolute:{sid}` | `"1"`（仅勾选"记住我"/开户态才写） | 绝对上限 |
| `auth:user:sessions:{userId}` | 该用户所有 sid 的集合 | 随会话延长，用于踢全部设备 |
| `auth:captcha:{id}` | 验证码明文答案 | 2 分钟 |
| `auth:login:fail:account-source:{loginKey}:{srcHash}` | 失败计数 | 15 分钟 |
| `auth:login:lock:account-source:{loginKey}:{srcHash}` | 锁定标记 | 15 分钟 |
| `auth:login:fail:source:{srcHash}` / `auth:login:lock:source:{srcHash}` | 来源级计数/锁 | 15 分钟 |
| `auth:quota:{bucket}:{srcHash}` | 各入口的来源配额计数 | 各自窗口 |
| `auth:password-change:fail:{userId}` | 改密原密码错误计数 | 15 分钟 |
| `auth:external:transaction:{state}` | 第三方登录事务（PKCE/returnTo） | 10 分钟 |
| `auth:uias:transaction:{state}` | UIAS 登录事务 | 10 分钟 |
| `realtime:ticket:{ticket}` | `userId\|sid` | 60 秒（见 §7） |

`sid` 本身是 **48 字节 `SecureRandom`**（`SessionService.java:206-210`）；对外展示设备列表时只暴露 `SHA-256` 前 22 字符的引用（`:212-219`），所以**就算设备列表泄露也无法直接冒用会话**。

#### 会话时长（全部由配置驱动）

`BE/auth/session/SessionLifetimePolicy.java` 是唯一口径来源：

| 场景 | 空闲超时 | 绝对上限 | 刷新 Cookie 类型 |
|---|---|---|---|
| 普通会员会话 | 2 小时（`idle-hours: 2`） | 14 天 | **会话 Cookie**（关浏览器即失效，`maxAge=-1`） |
| 勾选"记住我" | 7 天（`remembered-idle-days: 7`） | 14 天（`remembered-absolute-days: 14`） | 持久 Cookie（14 天） |
| 开户态（ONBOARDING） | 30 分钟（常量） | 2 小时（常量） | 持久 Cookie（2 小时） |
| access token | —— | 15 分钟（`access-token-minutes: 15`） | —— |

**"记住我"不是"永不过期的 JWT"**，它改的是 Redis 会话的空闲期 + 绝对期 + Cookie 持久性。而且 `SessionService.touch`（`:92-109`）只延长空闲 TTL，并且**被绝对剩余时间封顶**——所以持续操作也无法突破 14 天。

#### 刷新 Cookie 的确切属性

`BE/auth/session/RefreshCookieFactory.java:33-41`：

```java
ResponseCookie.from("lbl_refresh", sid)
    .httpOnly(true)
    .secure(secure)        // lbl.security.secure-cookie：local 默认 false，prod 默认 true
    .sameSite("Lax")       // 写死，无配置项
    .path("/api/auth")     // ★ 业务请求根本不会携带它
    .maxAge(maxAge)        // 会话 Cookie 或绝对期限
    .build();
```

`Path=/api/auth` 这个设计很关键：它让"刷新令牌"只在认证端点出现，把 CSRF 面压到最小。副作用见 §5.6 和第 14 章的 `@CookieValue` 缺陷。

### 5.2 每个业务请求的鉴权过程

`BE/security/filter/JwtAuthenticationFilter.java:42-81`，严格按此顺序：

1. **读 `Authorization` 头**，不是 `Bearer ` 开头 → 直接匿名放行（`:44-45`）。
2. **`jwt.parse`**：验签 + 校验 `exp`（HS256，`JwtService.java:41-43`）。
3. **取 `sid` claim**（`:47`）。
4. **`sessions.find(sid)`** 查 Redis 会话（`:48`）。
5. **开户态分支**（`:49-58`）：会话是 ONBOARDING 且 `sub == "onboarding:"+onboardingId` → 构造 `OnboardingPrincipal`，只授予 3 个固定权限：`onboarding:access`、`onboarding:account:create`、`onboarding:account:bind`。
6. **会员分支**（`:60-62`）四重校验，缺一不可：
   - `user.status == 1`（未被停用）
   - `user.authVersion == session.authVersion`（用户版本未变）
   - `session.username == JWT.sub`（会话与令牌属于同一人）
   - `session.authVersion == JWT.authVersion`（会话与令牌版本一致）
7. **装载权限**（`:63-66`）：调 `MenuMapper.selectByUserId(userId)`，把返回的每个 `permissionCode` 变成 `SimpleGrantedAuthority`。
   ⚠️ **如果 `authMethod == "PASSWORD"` 且 `passwordChangeRequired == true`，权限集合被清空**——这就是"待改密用户什么都干不了，只能改密"的实现。
8. **principal** = `CurrentUser(id, username, deptId)`，并 `authentication.setDetails(sid)`（`:67-69`）——业务侧靠 `getDetails()` 拿 sid（例如申请实时 ticket、判断"当前设备"）。
9. **异常处理**（`:72-79`）：`JwtException`/`IllegalArgumentException` → debug 日志；**其它任何异常（Redis 挂了、数据损坏）→ warn 后继续以匿名身份放行**。
10. 统一 `chain.doFilter`（`:80`）。

**Redis 会话缺失或落库失败时的行为**：不设置 Authentication，请求以匿名身份往下走；受保护路径由 `authenticated()` 交给 `authenticationEntryPoint` 返回 **401 JSON**（统一 `Result` 结构）。过滤器**不会**主动吊销 Cookie，也不清 Redis。

⚠️ **一个真实缺陷**：第 5 步的开户态分支在 `try` 块内部就调用了 `chain.doFilter` 并 `return`。如果下游抛异常，会被 `:75` 的 `catch (Exception)` 吞掉，然后 `:80` **再次调用 `chain.doFilter`** —— 同一请求的下游逻辑有被执行两次的可能。对 `/onboarding/account/create|bind` 这类写接口尤其值得修。同时日志会误导成 "Unable to resolve bearer token"。

### 5.3 权限双层模型（这是全项目最核心的设计）

#### 第一层：功能权限 —— "你能不能做这类动作"

实现方式：`sys_menu` 表里 `menu_type = 'BUTTON'` 的记录携带 `permission_code`，通过 `sys_role_menu` 授权给角色，角色再通过 `sys_user_role` 给用户。加载时被翻译成 Spring Security 的 `GrantedAuthority`，Controller 上用 `@PreAuthorize("hasAuthority('...')")` 校验。

**后端全部权限码（共 24 个业务权限码 + 1 个特殊标记）**，全部实测于源码：

| 模块 | 权限码 | 校验位置（`BE/`） |
|---|---|---|
| 用户 | `system:user:list` | `system/user/controller/UserController.java:50` |
| | `system:user:add` | `:62,72,85` |
| | `system:user:update` | `:56,62,92,114,121,129` |
| | `system:user:delete` | `:99` |
| | `system:user:export` | `:78` |
| | `system:user:reset-password` | `:107` |
| 角色 | `system:role:list` | `system/role/controller/RoleController.java:27` |
| | `system:role:add` | `:33,45` |
| | `system:role:update` | `:33,39,52` |
| | `system:role:delete` | `:59` |
| | `system:role:grant` | `:67,73,79` |
| 菜单 | `system:menu:list` | `system/menu/controller/MenuController.java:24` |
| | `system:menu:add` | `:36` |
| | `system:menu:update` | `:30,43` |
| | `system:menu:delete` | `:50` |
| 部门 | `system:dept:list` | `system/dept/controller/DeptController.java:25` |
| | `system:dept:add` | `:37,43` |
| | `system:dept:update` | `:31,37,50` |
| | `system:dept:delete` | `:57` |
| 登录日志 | `system:loginlog:list` / `:delete` | `system/log/controller/LoginLogController.java:29,40` |
| 操作日志 | `system:operatelog:list` / `:delete` | `system/log/controller/OperationLogController.java:25,37` |
| AI | `agent:chat:use` | `agent/api/AgentChatController.java:59` |
| 开户态 | `onboarding:account:create` / `:bind` | `auth/controller/OnboardingAccountController.java:34,45` |
| **排除式标记** | `onboarding:access` | 8 处 `!hasAuthority('onboarding:access')`，用于把"未注册的第三方临时身份"挡在成员接口之外 |

**平台级权限**有单一真相来源 `AccessPolicy.PLATFORM_ONLY_PERMISSION_CODES`（`BE/security/context/AccessPolicy.java:265-271`）：

```java
{ system:menu:list, system:menu:add, system:menu:update, system:menu:delete,
  system:user:reset-password }
```

这些权限**不允许下放给普通角色**——读取菜单树时会被级联剔除（含空壳父节点），写入侧 `requireNotPlatformOnly` 再拦一次。

#### 第二层：数据权限 —— "你能对哪些数据做动作"

`BE/security/context/AccessPolicy.java`（428 行）是全项目最重要的授权类。核心机制：

**① 授权快照 `actor(...)`**（`:50-75`）：按 `authentication.getName()` 回查用户并校验 `status==1` → 判断是否超管（要求 `role_code = 'super_admin'` 且角色启用）→ 一次性加载部门字典建 `ScopeContext` → 批量预取所有角色的权限码和 CUSTOM 部门（**避免逐行 N+1**）→ 遍历启用角色，把每个角色的 `Scope` 做 **union**，同时建立"权限码 → 该权限下的可操作范围"映射。

**② 数据范围 `roleScope`**（`:110-134`）：`sys_role.data_scope` 5 种取值的翻译规则：

| `data_scope` | 含义 | 计算方式 |
|---|---|---|
| `ALL` | 全部数据 | `Scope.unrestricted()` |
| `DEPT_AND_CHILDREN` | 本部门及以下 | 以**角色持有人**的 `deptId` 为根，用 `DeptPaths.isInsideSubtree` 取整棵子树 |
| `DEPT` | 仅本部门 | 同上，但只取根本身 |
| `SELF` | 仅自己 | `(all=false, self=true, 空集合, 空集合)` |
| `CUSTOM` | 自定义部门 | `sys_role_dept` 里的**精确 id 集合**，**不自动包含下级** |
| 其它值 | —— | 抛 `BusinessException("角色的数据范围配置无效")` |

最后 `ids.retainAll(available.keySet())` 剔除已删除的部门，避免权限悬空。

**③ 应用到查询 `applyUserScope`**（`:136-145`）——注意**默认拒绝**：

```java
// all → 不加条件
// 否则包一层 AND：
//   dept_id in (...)            （deptScope 非空时）
//   或 id = 当前用户             （self 时）
// 两者都没有 → condition.apply("1 = 0")   ← 查不到，比查多了安全
```

使用点：`UserService.java:80-82`。

**④ 提权防护（立体，不是一层）**：

| 方法 | 防的是什么 |
|---|---|
| `canManageUser` / `requireManageUser`（`:147-164`） | 拒绝管理：自己、`builtin=1` 的内置账号、看不见的用户、其它超管。且要求**严格权限超集**（`containsAll && !equals`），并逐角色做 `grantFits` 数据范围比对 |
| `canAssignRole`（`:166-177`） | 拒绝：已停用角色、`builtin=1` 角色、`super_admin` 角色；同样要求严格超集；CUSTOM/ALL 范围的角色还要 `grantFits` 到操作者自己 |
| `requireAssignableRoles`（`:180-190`） | 按**目标用户的目标部门**重算范围，拒绝"同级权限组合"和越范围分配 |
| `requireGrantableMenus`（`:239-243`） | 非超管只能授出**自己菜单树里确实有**的菜单 id |
| `requireScope`（`:232-237`） | 非超管不得把角色数据范围设为 `ALL`，也不得超出自身范围 |
| `requireSuperAdmin`（`:367-369`） | 菜单结构变更等操作的硬边界（配合 `MenuService.java:39-88`） |
| `canManageDept` / `requireCreateDept` / `requireMoveDept`（`:371-411`） | 部门树的越界创建/移动 |

⚠️ **一个需要小心的实现细节**：`Scope.contains`（`:458-463`）返回的表达式**没有加括号**，依赖 `&&` 优先于 `||`。它当前等价于 `!other.self || (target.deptId != null && departments.contains(target.deptId)) || (self && idsEqual)`。逻辑是对的，但**任何"为了可读性加个括号"或调整顺序的改动都可能静默放宽或收紧数据范围**。建议加括号 + 补测试。

#### 前端那一层：只负责"看不看得见"

`FE/permission/Permission.tsx` 只做条件渲染，**不承担任何安全责任**。全前端使用 `<Permission code=...>` 的地方共 19 处，分布在：

`layouts/AppLayout.tsx:309`（`agent:chat:use`）、`pages/system/user/index.tsx:71,78,85,111,169,174`、`pages/system/role/index.tsx:51,56,61,108`、`pages/system/menu/index.tsx:88,93,132`、`pages/system/dept/index.tsx:72,77,126`、`pages/system/loginlog/index.tsx:88`、`pages/system/operatelog/index.tsx:96`。

注意：列表类权限（如 `system:user:list`）**不用** `<Permission>` 包，而是靠"菜单授权 → 路由可达"来体现（无权限的菜单根本不会出现在菜单树里，`DynamicPage` 会渲染 403）。

### 5.4 续期、退出与强制下线

#### 静默续期（前端只发一次请求）

`FE/services/authSession.ts:14-27` —— 全应用唯一的续期入口：

```ts
let refreshPromise: Promise<string> | null = null;
export function refreshAccessTokenOnce(): Promise<string> {
  refreshPromise ??= axios.post(`${API_BASE}/auth/refresh`, {}, { withCredentials: true })
    .then(r => { store.dispatch(setAccessToken(r.data.data.accessToken)); ... })
    .finally(() => { refreshPromise = null; });
  return refreshPromise;   // ★ 并发 401 共享同一个 Promise
}
```

- Axios 的 401 拦截器和 AI 用的原生 `fetch`（`authenticatedFetch`，`:40-57`）**共用这一个函数**，所以"多个并发请求同时 401"只会发一次刷新，然后各自重试。
- 后端 `/api/auth/refresh`（`AuthService.refresh`，`:98-117`）会：查会话 → 会员分支校验 `status`/`authVersion`（不一致就 `sessions.remove(sid)` + 401）→ 重算 `passwordChangeRequired` 并回写 → `touch` 续期 → 重签 JWT。
- **403 不触发刷新**（身份有效但权限不足），只有 401 才刷新。
- 刷新失败才 `clearSession()` 并回登录页；`expireSession` 用 `expirationHandled` 标志 + 500ms 后复位来去重并发跳转。

#### 活跃续期（避免空闲页面制造流量）

- 前端 `FE/services/UserActivityManager.ts` 只在**真实鼠标/键盘交互**后调 `POST /api/auth/touch`（节流）。
- 后端 `AuthService.touch`（`:119-131`）只延长**空闲** TTL，且被绝对剩余时间封顶；任何校验失败都会 `sessions.remove(sid)`。

#### 退出登录

`AuthService.logout`（`:81-86`）：查会话拿用户名 → `sessions.remove(sid)`（**删除 Redis 会话 + 通过 WebSocket 断开该会话的连接**）→ 非开户态记一条成功日志。然后 `AuthController` 用 `refreshCookies.clear()` 下发**属性完全一致**的过期 Cookie（同名、同 Path、同 SameSite、`maxAge=0`）。

#### 强制下线（跨实例，链路完整）

```
SessionService.remove(sid)            BE/auth/session/SessionService.java:166
   └─> RealtimeGateway.closeSession(sid)     BE/realtime/RealtimeGateway.java:67
         └─> 发布 Redis CLOSE_SESSION 事件
               └─> 所有实例的 RealtimeEventSubscriber.onMessage 收到
                     └─> RealtimeGateway.closeSessionLocal(sid)   :71-80
                           └─> 遍历所有用户的 socket，按 attribute "sid" 精确匹配
                                 └─> session.close(CloseStatus.POLICY_VIOLATION)
```

**触发点**（全部实测）：登出 `AuthService.java:84`、刷新时校验失败 `:107`、touch 失败 `:128`、重新登录清理旧会话 `AuthController.java:61`、开户完成 `OnboardingAccountController.java:39,50`、用户自助下线设备 `AccountSessionController.java:40,48`、管理员踢单个/全部 `UserSessionAdministrationService.java:35,41`、停用/删除用户 `UserService.java:206,222`、改密/重置密码 `UserPasswordService.java:60,82`。

#### `authVersion` 机制

| 何时递增 | 位置 |
|---|---|
| 管理员重置密码 | `UserPasswordService.java:58` |
| 本人修改密码 | `UserPasswordService.java:80` |
| 管理员修改用户 | `UserService.java:204` |

三处都是**内联 `setAuthVersion(getAuthVersion()+1)` + `sessions.removeAll(userId)`**（双保险：即使旧 JWT 未过期也失效）。

⚠️ 三个问题：
1. **没有集中的 `bumpAuthVersion()` 方法**，新增场景容易漏。
2. 管理员改用户**任何字段**（哪怕只改手机号）都会踢掉该用户全部设备，而用户**自助**改联系方式却完全不失效 —— 策略不一致。
3. **删除用户**和**解绑第三方身份**不改 `authVersion`，只靠 `removeAll` 兜底。

### 5.5 验证码与登录防爆破（精确参数）

#### 图形验证码 `CaptchaService`（`BE/auth/service/CaptchaService.java`）

| 项 | 值 |
|---|---|
| TTL | 2 分钟（`:56`） |
| 长度 | 4 位（`:57`） |
| 字符集 | 30 字符 `23456789ABCDEFGHJKLNPQRSTUVXYZ`（去掉了易混的 0/O/1/I，`:71`） |
| 画布 | 148 × 44（`:78-79`） |
| 出题配额 | 每来源 60 次/分钟（bucket `captcha`，`:83-85,106`） |
| Redis key | `auth:captcha:{id}`（`:214-216`） |
| 校验方式 | `getAndDelete` **一次性消费**（`:123`），过期与"已用过"合并提示（防探测） |
| 启动自检 | 字体绘制探测失败就**拒绝启动**（`:231-259`）——避免"验证码永远是空白图"这种最难排查的故障 |
| 使用范围 | **仅 `/register`**（`RegistrationService.java:65`） |

⚠️ `RegistrationRequest.java:12-15` 引用了一个 `lbl.security.captcha-enabled` 开关，`CaptchaService` 的 javadoc 也描述了"`enabled=false` 直接放行"，但 `SecurityProperties` 里**根本没有这个字段**，`CaptchaService` 注入了 `SecurityProperties` 却从未使用 → **验证码无法关闭，且这是个死参数**。

#### 登录限流 `LoginAttemptGuard`（`BE/auth/service/LoginAttemptGuard.java`）

| 项 | 值 |
|---|---|
| 账号 + 来源 连续失败上限 | **5**（`:39`） |
| 来源 失败上限 | **20**（`:41`） |
| 统计窗口 | **15 分钟**（`:43`） |
| 锁定时长 | **15 分钟**（`:45`） |
| `loginKey` 归一化 | 小写 + 截断 64（`:47,64-68`） |
| 来源标识 | `base64url(SHA-256(IP))` —— **不存明文 IP**（`:138-145`） |
| 成功后 | 清空两个维度（`:109-112`） |
| 其它入口配额 | 外部登录 `/start` 30 次/分（`ExternalLoginService.java:47-48`）；UIAS `/start` 30 次/分；本人改密原密码错 5 次/15 分钟（`UserPasswordService.java:26-27`） |

⚠️ **限流计时非原子**：`LoginAttemptGuard.java:122-129,131-135` 是"先 `increment` 再 `expire`"，没有用 Lua/MULTI。如果 `EXPIRE` 丢失（进程被杀、Redis 主从切换），key 会**永久没有 TTL** → 该来源被永久锁定或配额永久耗尽。另外 `:93,97` 在锁定时删除计数，使"15 分钟窗口"在锁定那一刻被重置。

### 5.6 第三方登录、开户，以及 UIAS 的真相

#### 第三方登录（GitHub / Google / 微信）

流程是完整且安全的：

1. `GET /api/auth/external/{provider}/start` → `ExternalLoginService.beginLogin`（`:112-119`，先过来源配额）→ `begin`（`:129-167`）：
   - 生成 `state`（32 字节）与 PKCE `verifier`（48 字节）
   - `LoginTransaction(providerKey, verifier, redirectUri, returnTo, rememberMe, targetUserId, initiatingSid)` 以 JSON 存 `auth:external:transaction:{state}`，**TTL 10 分钟**
   - `code_challenge = S256(verifier)`（默认开启 PKCE）
   - `returnTo` 经 `safeReturnTo` 过滤，只允许 `/` 开头且非 `//`（防开放重定向）
2. `GET|POST /api/auth/external/{provider}/callback`：
   - `state` **一次性 `getAndDelete` 消费**并校验 provider 一致
   - 换 token（15 秒超时）、拉用户资料（15 秒）、`subject` 缺失即失败（微信回退 `openid`）
   - 绑定流程额外校验 `initiatingSid == 当前 Cookie 的 sid` 且属于同一用户
3. 落库 `ExternalLoginPersistence.settle`：**已绑定** → 建会员会话；**未绑定** → 建开户态会话（`onboarding=true`）
4. 开户接口 `/api/auth/onboarding/account/create|bind` 把临时身份换成正式会话，创建路径自动授予 `basic_role`

#### `OnboardingPrincipal` vs `CurrentUser`

两者**不是同一层级**，绝对不能混用：

| | `CurrentUser` | `OnboardingPrincipal` |
|---|---|---|
| 对应 | 已注册用户 | 第三方已验证但未注册/未绑定的临时身份 |
| `getName()` | 用户名 | `onboarding:{onboardingId}` |
| 权限 | 角色的权限码集合 | 固定 3 个：`onboarding:access/account:create/account:bind` |
| `AccessPolicy.actor()` | 能查到用户 | **查不到用户 → 抛 `UnauthorizedException`**（`AccessPolicy.java:53-54`） |

所以 8 个成员接口都必须显式写 `!hasAuthority('onboarding:access')`。⚠️ 这是**靠字符串约定维持的隔离**：新增成员接口时漏写这一句，就可能被"未注册的第三方临时身份"访问，而项目里没有任何授权测试能拦住这个错误。

#### UIAS（企业统一认证）：接线完整，实现为零 🔴

这是必须明确的结论：

| 检查 | 结果 |
|---|---|
| `EnterpriseDirectoryPort` 有实现类吗 | **0 个** —— 全仓库除接口自身、`UiasLoginService` 的 `ObjectProvider`、README 引用外无任何命中 |
| `UiasAssertionConsumer` 有实现类吗 | **0 个** |
| `UiasLoginService.isUsable()` | 要求"开关 + entryUrl + callbackUrl + 两个适配器都存在"，因两个 `ObjectProvider` 都取不到 bean → **恒为 false**（`:68-72`） |
| 是否接到 Controller | **是**。`ExternalAuthController.start`（`:48-50`）与 `callback`（`:74-77,107-118`）都直连 `UiasLoginService` |
| 运行时行为 | `begin` 先 `requireUsable()` 抛异常 → `/start` 返回 400 JSON；`complete` 先 `requireAssertionConsumer()` 抛异常 → `/callback` 302 回登录页 |
| 配置侧 | `uias.enabled=false`，entry/callback url 为空（`RES/application-local.yml:107-116`） |

**结论：UIAS 是一条"能点但必然失败"的路。** 仓库里只有流程编排和适配端口，企业 SDK 的实际实现从未提交。如果你内网需要接企业统一认证，这部分**等于要从零写**（好的一面是：编排逻辑、会话切换、开户闭环都已经写好了，你只需要实现两个端口 + 把配置填上）。

#### 微信登录的一个"坑注释"

`RES/application-local.yml:100-106` 有一段很有价值的注释，记录了微信 `sns/userinfo` 必须把 `access_token` 作为**查询参数**而非 Authorization 头，否则固定返回 `errcode 41001`，而错误表现却是"外部认证结果缺少稳定用户标识"，与真实原因完全对不上。这类注释是项目里最值得保留的资产。

---

## 6. 业务模块与数据库

### 6.1 数据库：14 张表

`RES/db/init_schema.sql`（228 行）定义了全部表。**全库没有任何 `FOREIGN KEY` 约束**，所有关系都是应用层逻辑。引擎 InnoDB，字符集 utf8mb4。

| # | 表 | 用途 | 主键 / 唯一键 | 逻辑删除 |
|---|---|---|---|---|
| 1 | `sys_dept` | 部门树（物化路径 `ancestors`） | PK `id`；UK `dept_code` | ✅ |
| 2 | `sys_user` | 用户主表 | PK `id`；UK `username`；UK `employee_no` | ✅ |
| 3 | `sys_local_credential` | 本地密码凭据 | PK `user_id` | ❌ |
| 4 | `sys_external_identity` | 第三方身份绑定 | PK `id`；UK `(provider_key,issuer,subject)`；UK `(user_id,provider_key)` | ❌ |
| 5 | `sys_role` | 角色（含 `data_scope`） | PK `id`；UK `role_code` | ✅ |
| 6 | `sys_menu` | 菜单 / 按钮 | PK `id`；UK `route_path`；UK `permission_code` | ✅ |
| 7 | `sys_user_role` | 用户 ↔ 角色 | PK `(user_id,role_id)` | ❌ |
| 8 | `sys_role_menu` | 角色 ↔ 菜单 | PK `(role_id,menu_id)` | ❌ |
| 9 | `sys_role_dept` | CUSTOM 数据范围的部门 | PK `(role_id,dept_id)` | ❌ |
| 10 | `sys_department_change_request` | 部门调动审批主单 | PK `id` | ❌ |
| 11 | `sys_department_change_step` | 审批步骤 | PK `id`；UK `(request_id,step_order)` | ❌ |
| 12 | `sys_notification` | 站内通知 | PK `id` | ❌ |
| 13 | `sys_login_log` | 登录审计 | PK `id` | ❌（刻意物理删除） |
| 14 | `sys_operation_log` | 操作审计 | PK `id` | ❌ |

**几个容易踩的细节**：

- `sys_user.auth_version BIGINT NOT NULL DEFAULT 1`（`:37`）—— 会话失效机制依赖它。
- `sys_menu` 的 `route_path` 和 `permission_code` 都是 **UNIQUE 且可为 NULL**。MySQL 的唯一索引不去重 NULL，所以多个 DIR/BUTTON 可以都没有 `route_path`，但**一条路径只能配一次**。
- `sys_role_dept` 的注释明确写了"**CUSTOM 仅包含明确指定的部门，不自动包含下级**"（`:131-132`）。
- `sys_department_change_request.version BIGINT`（`:151`）—— ⚠️ **它不是乐观锁**：实体类上没有 `@Version`，代码只把它 `+1`，从不放进 UPDATE 的 `WHERE`。并发控制完全靠 `SELECT ... FOR UPDATE`。字段名会误导人以为有第二道防线。
- **关系表没有任何时间列**，所以无法回答"这条授权是什么时候给的"。

### 6.2 三个必须知道的数据库设计约定

#### 约定一：逻辑删除的唯一索引口径（这是本项目最值得学习的一处纪律）

`sys_user`/`sys_role`/`sys_dept`/`sys_menu` 用 `deleted` 做逻辑删除，但 **MySQL 的唯一索引看不到 `deleted`**。所以"删掉一个用户再建同名的"会撞唯一键，而 MyBatis-Plus 的 `selectCount` 会自动追加 `deleted = 0`，导致"预检通过、插入失败"这种让用户无法理解的错误。

项目的处理方式：**专门写"包含已删除"的计数方法来做预检**，四处一致执行并都有注释说明原因：

| 方法 | 位置 |
|---|---|
| `UserMapper.countIncludingDeletedByUsername` | `BE/system/user/mapper/UserMapper.java:21-22` |
| `RoleMapper.countIncludingDeletedByRoleCode` | `BE/system/role/mapper/RoleMapper.java:53-57` |
| `DeptMapper.countIncludingDeletedByDeptCode` | `BE/system/dept/mapper/DeptMapper.java:23-27` |
| `MenuMapper.countIncludingDeletedByRoutePath` / `...ByPermissionCode` | `BE/system/menu/mapper/MenuMapper.java:76-87` |

调用点：`UserService.java:242-244`、`RoleService.java:81-83,101-103`、`DeptService.java:120,136`、`MenuService.java:123-128`。

**副作用（要知道）**：逻辑删除后，用户名、角色标识、部门编码、路由路径**永久被占用**。错误提示也如实说明了这一点，例如 `RoleService.java:82`："角色标识已被占用（已删除角色占用的标识不会被释放，请换一个）"。

#### 约定二：删除语义混用（一半逻辑删、一半物理删）

| 操作 | 逻辑删除 | 物理删除 |
|---|---|---|
| `UserService.remove()` | `sys_user` | `sys_user_role`、`sys_local_credential`、`sys_external_identity`（`:216-221`） |
| `RoleService.remove()` | `sys_role` | `sys_role_menu`、`sys_role_dept`（`:127-131`） |
| `MenuService.remove()` | `sys_menu` | `sys_role_menu`（`:94-97`） |

这是有意为之（关系表不需要留痕），但读代码时要知道：**删用户后，他的角色关联是真的没了，不是标记删除**。

#### 约定三：审计列分三档

- **完整档**（`deleted` + `deleted_time` + `created_by/time` + `updated_by/time`）：`sys_dept`、`sys_user`、`sys_role`、`sys_menu`。实体上用 `@TableLogic` + `@TableField(fill=...)`，由 `BE/config/MybatisConfig.java:25-47` 的 `auditHandler()` 自动填充（从 `SecurityContextHolder` 取当前用户 id）。
- **无逻辑删除档**：所有关系表、凭据表、外部身份表、审批两张表、通知表、两张日志表。
  ⚠️ 审批两张表**没有 `created_by`/`updated_by`**，"谁提交的"只能靠 `requester_id`。
- **物理删除档**：两张日志表（保留策略见 §6.9）。

### 6.3 菜单树是怎么算出来的（一条递归 CTE 决定一切）

`BE/system/menu/mapper/MenuMapper.java:14-32`：

```sql
WITH RECURSIVE granted AS (
    SELECT DISTINCT m.* FROM sys_menu m
    INNER JOIN sys_role_menu rm ON rm.menu_id = m.id
    INNER JOIN sys_user_role ur ON ur.role_id = rm.role_id
    INNER JOIN sys_role r ON r.id = ur.role_id
    WHERE ur.user_id = #{userId} AND m.deleted = 0 AND m.status = 1
      AND r.deleted = 0 AND r.status = 1          -- ★ 角色也必须启用且未删
), menu_tree AS (
    SELECT * FROM granted
    UNION
    SELECT parent.* FROM sys_menu parent
    INNER JOIN menu_tree child ON child.parent_id = parent.id
    WHERE parent.deleted = 0 AND parent.status = 1  -- ★ 递归补齐祖先
)
SELECT DISTINCT * FROM menu_tree
ORDER BY parent_id, sort_order, id
```

这条 SQL 是**整个前端动态路由的数据源**，它同时解决了四件事：

1. 只有 `用户 → 角色 → 菜单` 三级都有效（未删、启用）的菜单才会被授权；
2. **递归补齐祖先节点**：如果你只被授了深层子菜单，它的父目录也会自动出现（否则侧边栏无法显示层级）；
3. 排序由 SQL 决定（`parent_id, sort_order, id`），前端直接用；
4. 结果集**就是授权结果**，所以前端不需要再维护"路径 → 权限码"的映射表（`DynamicPage.tsx:21-24` 的注释解释了这一点）。

⚠️ **建库要求：MySQL 8.0+**。`WITH RECURSIVE` 在 5.7 上不存在。同理 `DELETE ... ORDER BY ... LIMIT`（日志清理）也是 8.0+。

批量版本 `selectPermissionCodesByUserIds`（`:35-58`）用于用户列表的按钮权限装配，避免逐行查询。

### 6.4 角色授权（`grantMenus`）—— 你问的"分配权限"到底发生了什么

这是用户最关心的流程。前端在角色页勾选菜单树 → `PUT /api/system/roles/{id}/menu-ids` → `RoleService.grantMenus`（`BE/system/role/service/RoleService.java:164-195`）。

**授权树本身就已经是过滤后的**（`grantableMenus`，`:154-162`）：

- 超管：`sys_menu` 里全部 `status=1` 的菜单；
- 非超管：**只有 `menus.selectByUserId(自己)` 返回的那些**；
- 两种情况都会调 `AccessPolicy.omitPlatformOnlyMenus` 剔除平台级权限，而且**剔除是"连同失去全部后代的父节点一起"做的** —— 菜单管理页下 4 个按钮全是平台级，于是"菜单管理"这个页面节点也会一起消失。这样用户根本看不到那几行，从源头避免误勾。

**`grantMenus` 的 8 步校验**：

| 步 | 做什么 | 位置 |
|---|---|---|
| 1 | 校验操作者对该角色有管理权 | `:168` → `AccessPolicy.requireManageRole` |
| 2 | **非超管 + 该角色已分配给用户 → 拒绝**（"已分配角色的菜单授权仅超级管理员可修改"） | `:169-170` |
| 3 | 过滤 null / ≤0 的 id 并去重 | `:171` |
| 4 | 校验所有菜单 id 都真实存在 | `:172-174` |
| 5 | `requireGrantableMenus` —— **不能授出自己没有的菜单** | `:175` → `AccessPolicy.java:239-243` |
| 6 | **纵深防御**：再拦一次平台级权限（防止绕过界面直接调接口） | `:178-179` |
| 7 | 非超管还要：授权内容必须落在自己的操作范围内，且**不能造出与自身同级的角色** | `:180-186` |
| 8 | `super_admin` 角色**原样保留**它已有的平台级授权；其它角色若有历史脏数据则自然清掉 | `:189-194` |

然后 `roleMenus.deleteByRoleId(id)` + 逐条 `insert`（`:192-194`）。

**关于第 6 步的注释很值得一读**（`:176-177`）：

```java
// 纵深防御：授权树已经过滤掉平台级权限，这里再拦一次，防止绕过界面直接调接口
// 把"永远用不了"的权限写进 sys_role_menu。对超管同样拒绝 —— 授下去也没有任何意义。
```

#### ⚠️ 一个必须纠正的重要误解：角色/菜单变更**不会**使会话失效

`README.md:184-186`、`:332`、`:643` 都声称"角色授权/菜单维护后，会找出受影响用户使其旧会话失效"。

**这是错的。** `RoleService` 的构造器（`:37-44`）只注入了 `RoleMapper`、`RoleMenuMapper`、`MenuMapper`、`UserRoleMapper`、`AccessPolicy`、`RoleDeptMapper` —— **根本没有 `SessionService`**。整个类不做任何 `authVersion` 变更。`MenuService`（构造器 `:31-36`）同理。

**实际生效机制是"每个请求都重算权限"**，而且这个机制本身是可靠的：

- `JwtAuthenticationFilter.java:64` 每个请求都重新查 `menus.selectByUserId(userId)` 来构造 authorities；
- `AccessPolicy.actor()` 每请求重算 `operationScopes`（`:63-74`）。

所以**权限变更会在下一个请求立即生效，不需要踢会话、也不需要重新登录**。安全性上没有问题 —— 但文档与实现不一致本身就是维护成本。真正会失效会话的只有用户相关路径：`UserService.java:206,222`、`UserPasswordService.java:60,82`。

### 6.5 部门树与 `ancestors` 物化路径

`BE/system/dept/support/DeptPaths.java` 是 `ancestors` 的**唯一口径**，同时提供 SQL 版和内存版：

- `subtreeQuery`（`:45-49`）：`ancestors = path OR ancestors LIKE 'path,%'` —— **注意那个逗号**。注释 `:14-18` 记录了这个坑：早期用 `LIKE '0,1,2%'` 会误匹配到兄弟子树 `0,1,20`。
- `isInsideSubtree`（`:57-60`）：同一口径的内存实现，用于权限判断。

**移动部门时 `ancestors` 的重算**（`DeptService.update`，`:130-163`）：

1. 先存旧路径 `oldPath`（`:140`）；
2. 重算自身 parent/ancestors，**做环检测**（`:179-193`，判断新父节点是否在自己的子树里）；
3. `requireMoveDept` 数据范围校验（`:143`）；非 `ALL` 范围时**禁止移动包含范围外部门的子树**（`:144-148`）；
4. `updateById`（`:150`）；
5. 路径变化时查子树，**逐行改写** `ancestors = newPath + child.ancestors.substring(oldPath.length())`（`:151-161`）。
   ⚠️ 子树 k 个节点就 k 次 UPDATE，没有批量、没有上限。

**删除部门**（`:170-176`）有四道前置拦截：内置根部门禁删、有子部门禁删、有用户禁删、被角色 CUSTOM 引用禁删。

⚠️ **部门列表不在 SQL 里做范围过滤**（`DeptService.java:42-47`）：全表查出后在 Java 里 filter。而且 `AccessPolicy.actor()` 每次请求都会 `depts.selectList(new LambdaQueryWrapper<>())` **全表读一次 `sys_dept`**（`:58`）。部门规模大时这是明显开销。

### 6.6 菜单校验规则（`MenuService.apply`）

| 规则 | 位置 |
|---|---|
| `menu_type` 只允许 `DIR` / `MENU` / `BUTTON` | `:102` |
| 父级不能选自己 | `:104` |
| 父级不能是 `BUTTON` | `:107` |
| 移动时不能进自己的子树（`isDescendantOf`，用 visited 防环） | `:108,149-158` |
| `MENU` 必须有 `routePath` 和 `component` | `:113,116` |
| `BUTTON` 必须有 `permissionCode` | `:117` |
| `route_path` / `permission_code` 唯一性按**含已删除**口径预检 | `:123-128` |
| 内置菜单不能改类型、不能删、有子节点不能删 | `:78-80,90-93` |
| 新建菜单自动授权给 `super_admin` | `:69-72` |

⚠️ **菜单管理的 4 个权限码实际上被超管硬校验覆盖**：`MenuService` 在 `:39/44/50/76/88` 调用 `access.requireSuperAdmin`，所以**非超管即使持有 `system:menu:*` 也一律 403**。这正是平台级权限的设计意图（菜单结构变更属于平台能力）。

### 6.7 用户模块

| 端点 | 权限码 | 说明 |
|---|---|---|
| `GET /api/system/users` | `system:user:list` | 分页（`page≥1`，`size≤100`，`:78`），叠加 `applyUserScope` 数据范围（`:81`） |
| `GET /{id}` | `system:user:update` | 详情 |
| `GET /form-options` | add 或 update | 角色/部门下拉选项 |
| `GET /username-available` | `system:user:add` | 用户名可用性（两段口径，`:239-244`） |
| `GET /export` | `system:user:export` | SXSSF 流式导出，按 500 条翻页 |
| `POST /` | `system:user:add` | 新增（密码确认校验、`registration_source='ADMIN'`） |
| `PUT /{id}` | `system:user:update` | 修改（**`authVersion+1` + `sessions.removeAll`**，`:204-206`） |
| `DELETE /{id}` | `system:user:delete` | 逻辑删用户 + 物理清关系 + 踢会话（`:216-222`） |
| `POST /{id}/reset-password` | `system:user:reset-password` | 生成 16 位临时密码、强制改密、踢全部会话 |
| `GET /{id}/sessions` | `system:user:update` | 该用户的设备会话列表 |
| `DELETE /{id}/sessions/{reference}` | `system:user:update` | 踢单台设备 |
| `DELETE /{id}/sessions` | `system:user:update` | 踢该用户全部设备 |
| `PUT /me/password` | 仅需登录（**刻意无权限码**） | 本人改密 |

**几个业务规则**：

- **内置用户保护**：`builtin=1` 不能禁用（`:180`）、不能删除（`:215`）、**必须保留 `super_admin` 角色**（`:185-188`）。
- **`basic_role` 不会被表单覆盖抹掉**（`:197-203`）—— 防止管理员误操作把普通用户的默认角色清掉。
- **重置密码要求操作者是超管且目标不是超管**（`UserPasswordService.java:54-55`）。
- **本人改密**：15 分钟窗口内原密码错 5 次限流；新旧密码相同也判失败（`:72`）。
- **列表按钮权限逐行计算**（`toListViews`，`:338-376`）：用 `forPermissions(actor, "system:user:update"/"delete")` 给每行打 `manageable`/`deletable` 标记，且**批量**取角色/部门/权限 —— **没有 N+1**。这是个好样板。

### 6.8 部门调动审批（状态机）

**状态取值全部是字面量字符串，没有 Java 枚举、也没有 DB CHECK 约束**：

| 对象 | 取值 |
|---|---|
| 主单 `status` | `PENDING_SOURCE`、`PENDING_TARGET`、`APPROVED`、`REJECTED`、`CANCELLED` |
| 步骤 `status` | `PENDING`、`WAITING`、`SKIPPED`、`APPROVED`、`REJECTED` |
| 步骤 `step_type` | `SOURCE`（`step_order=1`）、`TARGET`（`step_order=2`） |

**提交**（`submit`，`DepartmentTransferApprovalService.java:47-82`）：

1. **行锁本人** `SELECT * FROM sys_user WHERE id=? AND deleted=0 FOR UPDATE`（`:50`）—— 这是串行化"同一用户并发提交"的关键；
2. 已有进行中申请则拒绝（`:52`）；
3. 目标部门必须存在且启用，且不能等于现部门（`:53-55`）；
4. **无部门（`fromDeptId==null`）则跳过源部门步骤**：主单直接进 `PENDING_TARGET`、`current_step=2`，SOURCE 步骤写成 `SKIPPED`；
5. 审批人 = 当前步骤对应部门的 `leader_user_id`，但**部门无负责人 / 负责人已停用 / 负责人就是申请人本人 → 返回 null**（禁止自审，`:174-179`）；此时只有超管能批；
6. 给申请人、当前审批人、**所有活跃超管**分别写通知（`:74-80`）。

**审批**（`decide`，`:112-162`）：

1. `SELECT * FROM sys_department_change_request WHERE id=? FOR UPDATE`（`:114`）—— **唯一的并发防线**；
2. 主单必须是 `PENDING_*`；当前步骤必须存在且为 `PENDING`；
3. 权限判定：`actor.superAdmin() || actor.user().id == step.assigned_user_id`（`:119`）—— **超管可代批任何步骤**；
4. 申请人本人不能批（`:120`）；
5. **再次锁申请人并重校验前置条件**（`:122-130`）：申请人仍存在且启用、目标部门仍存在且启用、申请人当前部门仍等于 `from_dept_id`。任一不满足 → 主单置 `CANCELLED` + 通知参与者并返回（⚠️ **不复位步骤状态**）；
6. 通过且 `current_step==1` → 计算目标步骤审批人、置 `PENDING`、主单转 `PENDING_TARGET`；
7. 通过且 `current_step==2` → 若 `access.requiresPlatformTransferReview` 为真（该用户持有 DEPT/DEPT_AND_CHILDREN 角色且带权限码）则**要求超管处理**；否则**更新 `sys_user.dept_id`** → 主单 `APPROVED`；
8. ⚠️ **完成后只改 `dept_id`，不动 `authVersion`、不踢会话**。这也是合理的：DEPT 类范围是每请求按当前 `dept_id` 重算的，所以权限变动下一请求即生效。

**统一待办中心**（`BE/approval/task/`）：

- `GET /api/account/approvals?scope=pending|processed|mine`、`GET /pending-count`。
- 类级只要求登录且非开户态，**无权限码**；分页夹到 1..100。
- **`pending` 查询里非超管只能看 `assigned_user_id = 自己` 的步骤，而超管会看到所有人的待办**（SQL 里的 `OR #{superAdmin}=TRUE`），与 `decide()` 允许超管代批一致。
- ⚠️ 该 Mapper **未做数据范围（dept scope）过滤**，可见性完全由"申请人/审批人/超管"三种身份决定。
- ⚠️ `business_type` 目前只有 `'DEPARTMENT_CHANGE'` 一个字面量 —— "统一待办中心"现在是**单业务实现**（`approval/package-info.java` 里声明了未来 `roleelevation` 的规划）。

### 6.9 审计与日志

#### `@OperationLog` 切面

| 设计点 | 实现 | 为什么 |
|---|---|---|
| 切面顺序 | `@Order(0)`（`OperationLogAspect.java:29`） | 让它**早于 Spring Security 的方法级权限拦截器**成为最外层，从而"无权限的尝试"也能落一条 FAILURE（`:20-23` 注释） |
| 记录时机 | `finally` 块（`:50-55`） | 无论正常返回、业务异常还是权限拒绝都会执行 |
| **业务回滚后审计仍在** | `@Transactional(propagation = REQUIRES_NEW)`（`OperationLogService.java:49`） | 独立事务提交。✅ **已验证机制成立**：切面注入的是 Spring 代理（构造器注入），所以 REQUIRES_NEW 真实生效 |
| 目标对象 | 支持 SpEL，如 `#id`、`#id + ':' + #reference`、`#request.targetDeptId` | 不以 `#` 开头的按字面量处理，所以 `targetType="USER"` 是常量 |
| 容错 | SpEL 求值异常被吞掉返回 null（`:67-70`）；`record` 内部吞异常（`:52-72`） | "审计表达式写错不影响业务" |
| 操作人 | 从 `SecurityContextHolder` 取 `CurrentUser`（`OperationLogService.java:106-115`） | |
| 其它字段 | IP / UA / method / URI / MDC requestId（`RequestInfo`），全部按列宽截断 | `requestId` 由 `RequestLoggingFilter` 生成并写入 MDC，同时进 `Result.requestId` → **服务端日志能与响应体对账** |

⚠️ 两个风险：
1. `finally` 里若 `record` 自身抛出未捕获异常（例如代理提交阶段失败），会**覆盖**业务异常/返回值。
2. `REQUIRES_NEW` 会额外占一条数据库连接，而本地连接池 `maximum-pool-size: 10`。
3. ⚠️ `AuthService.login` 上有 `@Transactional`（`:44`）却**没有任何写操作**，而它调用的 `LoginLogService.record` 是 REQUIRES_NEW → **单次登录占用 2 条连接**。`ExternalLoginService.java:218-221` 明确规避了这个反模式，登录路径却没有。

#### 日志保留

`LogRetentionJob`（`@Scheduled(cron="${lbl.log.retention.cron:0 30 3 * * *}")`）：
- 分批物理删除，批间 sleep 200ms，到 `maxBatches` 打 WARN 留给下次；
- **刻意不加 `@Transactional`**（`:22-24` 注释），因此 SQL 用 `DELETE ... WHERE time < ? ORDER BY time LIMIT ?` 沿索引分批推进，避免长事务；
- `keepDays` 有"至少 1 天"保护，防止配置事故清空整表；
- 默认：登录日志保留 180 天、操作日志 365 天、batch 2000、maxBatches 50。
- ⚠️ **无分布式锁** → 多实例会重复清理（幂等但浪费）。

手工删除入口单次上限 1000 条，**删除动作本身也记审计**。

### 6.10 文件上传（当前比较薄）

| 项 | 事实 |
|---|---|
| 端点 | `POST /api/files/staged`、`DELETE /api/files/staged/{token}` |
| 权限 | ⚠️ **无任何 `@PreAuthorize`**，只靠 `anyRequest().authenticated()` |
| 校验 | 扩展名白名单（默认 `csv,xlsx`）+ 声明 MIME + 大小（默认 10 MB） |
| ⚠️ 内容校验 | **没有**。`FileUploadProperties.java:12` 自己承认"业务导入服务仍必须校验文件内容"，但**仓库里没有任何导入服务** |
| ⚠️ 存储 | `LocalStagedFileStorage` 用**进程内内存 Map** 存 token（`:27,59`）→ **多实例部署必然出现"文件不存在/无权访问"**，重启丢 token |
| TTL | 默认 `PT30M`，后台定时清理磁盘文件 |

**结论：文件上传目前只是一个"能换 token"的半成品**，缺少业务侧消费示例，且多实例下不可用。

### 6.11 全部 REST 接口速查

**认证 `/api/auth`**：`POST /login`、`POST /register`、`POST /refresh`、`POST /touch`、`POST /logout`、`GET /me`、`GET /captcha/*`、`GET /external/providers`、`GET /external/{provider}/start`、`GET|POST /external/{provider}/callback`、`POST /onboarding/account/create|bind`

**个人账号 `/api/account`**：`GET|PUT /profile`、`GET /sessions` + `DELETE /sessions/{ref}` + `DELETE /sessions/others`、`GET /identities` + `POST /identities/{provider}/start` + `DELETE /identities/{provider}`、`POST /realtime/ticket`、`GET /notifications` + `/unread-count` + `PUT /{id}/read` + `PUT /read-all`、`GET /approvals` + `/pending-count`、`GET|POST /department-change/**`（options/profile/detail/submit/approve/reject/cancel）

**系统管理 `/api/system`**：`/users/**`（13 个端点）、`/roles/**`（9 个）、`/menus/**`（5 个）、`/depts/**`（6 个）、`/login-logs`（2 个）、`/operation-logs`（2 个）

**其它**：`POST /api/agent/chat`（SSE）、`POST|DELETE /api/files/staged/**`、`/actuator/health/**`

⚠️ `POST /api/auth/register` 有一个隐含的前置条件：`SecurityProperties.java:24` 的注释提到"`/api/auth/register` 对匿名请求将没有任何门槛" —— 实际上注册**强制验证码**（`RegistrationService.java:65`），但**没有基于来源的配额**（对比外部登录 `/start` 有 30 次/分）。开户接口 `createFromOnboarding` 更是**既无配额也无验证码**（`:73-91`），只做用户名查重。

---

## 7. Realtime / WebSocket 详解

### 7.1 为什么这里必须用 WebSocket

HTTP 是"一问一答"：浏览器不先发请求，服务器没有任何办法主动告诉它"你有新消息"。轮询（每 N 秒问一次）能实现，但代价是大量空请求和延迟。

WebSocket 在一次 HTTP `Upgrade` 握手之后保留一条**双向长连接**，服务端可以随时推送。本项目用它推送三类东西：

| 事件名 | 含义 | 前端是否处理 |
|---|---|---|
| `notification.created` | 你有新通知 | ✅ 重新拉通知列表和未读数 |
| `approval.request.updated` | 审批状态变了 | ✅ 重新拉待办数量 |
| `connection.ready` | 握手成功 | ❌ 被静默丢弃（见 §7.7） |
| `connection.ping` | 心跳 | ❌ 被静默丢弃 |

### 7.2 握手全流程（一次性 ticket 机制）

**核心问题**：浏览器的 WebSocket API **不允许自定义请求头**，所以没法带 `Authorization: Bearer xxx`。而把长期 JWT 放在 URL 查询串里，会被 nginx access log、浏览器历史、Referer 记录下来——等于泄露。

**解法**：先用普通 HTTP 请求（能带 Bearer 头）换一张**一次性、短命**的票，再用票建立连接。

```text
① 前端                              FE/services/RealtimeClient.ts:27
   const ticket = await getRealtimeTicket()
        │  FE/api/notifications.ts:19  →  POST /account/realtime/ticket   (带 Bearer)
        ▼
② 后端 RealtimeController.ticket()          BE/realtime/RealtimeController.java:23-29
   - 从 Authentication.getDetails() 取 sid（由 JwtAuthenticationFilter 写入）
   - sessions.find(sid) 校验会话仍存在
   - 校验会话不是开户态、且属于当前用户，否则 401
   - tickets.issue(userId, sid)
        ▼
③ RealtimeTicketService.issue()             BE/realtime/RealtimeTicketService.java:17-23
   - new byte[32] + SecureRandom  →  256 bit 熵
   - Base64 URL-safe 无填充  →  43 字符
   - Redis SET  realtime:ticket:{ticket}  =  "{userId}|{sid}"   TTL 60 秒
        ▼
④ 前端建立连接                       FE/services/RealtimeClient.ts:29-31
   base = new URL(VITE_API_BASE_URL || location.origin, location.origin)
   protocol = base.protocol === 'https:' ? 'wss:' : 'ws:'
   new WebSocket(`${protocol}//${base.host}/ws/realtime?ticket=${ticket}`)
        ▼
⑤ 握手拦截器                          BE/realtime/RealtimeWebSocketConfig.java:29-42
   - 取 query 参数 "ticket"
   - tickets.consume(ticket)   ← getAndDelete，读即删
   - 取不到 → return false，握手直接失败
   - 取到 → attributes.put("userId", ...) / attributes.put("sid", ...)
        ▼
⑥ 连接建立                            BE/realtime/RealtimeGateway.java:28-34
   - 校验 userId 非空，否则 close(NOT_ACCEPTABLE)
   - 包一层 ConcurrentWebSocketSessionDecorator(session, 5000ms, 128KB)
   - 登记进 connections[userId][sessionId]
   - 主动发一帧 {"type":"connection.ready"}
```

**精确参数**：

| 项 | 值 | 位置 |
|---|---|---|
| 升级路径 | `/ws/realtime` | `RealtimeWebSocketConfig.java:29` |
| 取票端点 | `POST /api/account/realtime/ticket` | `RealtimeController.java:16,23` |
| query 参数名 | `ticket` | `RealtimeWebSocketConfig.java:33` |
| 票据 TTL | **60 秒** | `RealtimeTicketService.java:21` |
| 票据熵 | 32 字节 / 256 bit | `:18` |
| Redis key | `realtime:ticket:{ticket}` | `:35` |
| 票据值 | `{userId}|{sid}` | `:21` |
| 一次性保证 | `getAndDelete`（Redis ≥ 6.2 命令） | `:27` |
| 通道本身是否鉴权 | ❌ `/ws/**` 是 `permitAll`（`WebSecurityConfig.java:43`），准入完全靠 ticket | |

> ⚠️ **`README.md` 在这里是错的**：`README.md:386` 和 `:400` 都写"30 秒 ticket"，实际代码是 **60 秒**。排查连接问题时按 30 秒推理会得出错误结论。
>
> ⚠️ **依赖 Redis ≥ 6.2**：`getAndDelete` 是 6.2 才有的命令。内网若部署 Redis 5.x/6.0，握手会直接异常失败。这是一个很容易被忽略的部署前置条件。

### 7.3 连接管理（本机内存表）

`BE/realtime/RealtimeGateway.java:20`：

```java
private final Map<Long, Map<String, WebSocketSession>> connections = new ConcurrentHashMap<>();
//                 ↑ userId      ↑ 原始 session.getId()   ↑ 装饰后的会话
```

| 机制 | 实现 | 说明 |
|---|---|---|
| 并发发送保护 | `ConcurrentWebSocketSessionDecorator(session, 5_000, 128 * 1024)`（`:31`） | 发送超时 **5 秒**，缓冲上限 **128 KiB**。默认 `OverflowStrategy.TERMINATE` → 超限会抛 `SessionLimitExceededException` 并断开该连接 |
| 心跳 | `@Scheduled(fixedDelay = 25_000)`（`:83-87`） | 每 25 秒向**本机**连接发 `connection.ping`。**刻意不走 Redis**——否则实例数越多心跳风暴越密（`:85` 注释明确说明） |
| 正常关闭清理 | `afterConnectionClosed`（`:37-45`） | 移除 socket；桶空了就 `connections.remove(userId, sessions)` |
| 发送前检查 | `:59` | `isOpen()` 为假就移除 |
| 发送异常清理 | `:60` | 移除并关闭 |
| 遍历安全 | `:57,:74` | 用 `Map.copyOf(sessions)` 做快照，避免并发修改异常 |

⚠️ **轻微内存泄漏**：`sendLocal` 在 `:59/:60` 移除死连接后**不检查桶是否为空**，只有 `afterConnectionClosed` 会清空桶。长期运行会残留空的 `ConcurrentHashMap`（每用户一条，影响很小，但确实是泄漏）。

⚠️ **没有读空闲检测**：心跳只单向写，没有 pong 判别。客户端崩溃（无 FIN）造成的半开连接，要等到下一次发送失败才会被清理。

⚠️ **心跳任务与订阅重试共用默认单线程调度器**。`sendLocal` 内单会话最长可阻塞 5 秒，会顺延其它 `@Scheduled` 任务。

### 7.4 跨实例广播（Redis Pub/Sub）

单实例时以上就够了；多实例时，用户 A 连在实例 1，但触发通知的业务发生在实例 2 —— 必须能把事件送到正确的实例。

**设计要点：`send()` 不直接发本机，而是统一走 Redis，让所有实例（含自己）从订阅端投递**，这样避免"本地发一次 + 订阅再发一次"的重复。

```text
业务代码 NotificationService.create()
   └─> RealtimeGateway.send(userId, type, data)          BE/realtime/RealtimeGateway.java:47-50
         └─> RealtimeEventPublisher.userEvent(...)        BE/realtime/RealtimeEventPublisher.java:21-23
               └─> redis.convertAndSend("kariya-admin:realtime:events", envelopeJson)   :31
                     │  （失败 → 返回 false → Gateway 降级为本机 sendLocal，:49）
                     ▼
              所有实例的 RealtimeEventSubscriber.onMessage()   BE/realtime/RealtimeEventSubscriber.java:24-33
                     ├─ action == "USER_EVENT"     → gateway.sendLocal(userId, type, data)
                     └─ action == "CLOSE_SESSION"  → gateway.closeSessionLocal(sid)
                           └─> 只发/关本实例 connections 里的连接
```

| 项 | 值 |
|---|---|
| 频道名 | **`kariya-admin:realtime:events`**（`RealtimeEventPublisher.java:11`） |
| 信封结构 | `RealtimeEnvelope(action, userId, sid, type, data)`（`RealtimeEnvelope.java:6`） |
| `action` 取值 | 仅两个：`USER_EVENT`、`CLOSE_SESSION`（`:7-8`） |
| USER_EVENT JSON | `{"action":"USER_EVENT","userId":123,"sid":null,"type":"notification.created","data":{...}}` |
| CLOSE_SESSION JSON | `{"action":"CLOSE_SESSION","userId":null,"sid":"...","type":null,"data":null}` |

**订阅是怎么启动的**（`RealtimeSubscriptionStarter.java`）——这段设计得很讲究：

```java
public RealtimeSubscriptionStarter(RedisConnectionFactory connections, RealtimeEventSubscriber subscriber) {
    // 不注册成 Spring Lifecycle Bean，避免 Redis 暂时不可用时上下文刷新阶段直接失败。
    this.container = new RedisMessageListenerContainer();
    container.setConnectionFactory(connections);
    container.addMessageListener(subscriber, new ChannelTopic(RealtimeEventPublisher.CHANNEL));
    container.afterPropertiesSet();
}

@Scheduled(initialDelay = 1_000, fixedDelay = 5_000)
public void ensureStarted() {
    if (container.isRunning()) return;
    try { container.start(); /* 恢复日志 */ }
    catch (RuntimeException ex) { /* 只告警一次，5 秒后重试 */ }
}
```

- **Redis 挂了不会阻止 Web 应用启动**（这是刻意设计，`:24` 注释说明）。首次重试在启动后 1 秒，之后每 5 秒一次。健康检查负责阻止流量进入（`/actuator/health/readiness` 包含 `db,redis`）。
- ⚠️ **告警可能永远不出现**：`log.warn` 只在 `start()` **抛异常**时打印。如果容器"已启动但底层订阅线程在后台重连失败"，`isRunning()` 为 true，`ensureStarted` 直接 return，恢复/失败日志都不会再有。

### 7.5 通知数据流（"事务提交后再推送"是真的）

这是很多项目会写错的地方：如果在事务里发通知，事务回滚后用户会收到一条"假通知"。

`BE/notification/NotificationService.java:41-46` 的实现：

```java
private void afterCommit(Runnable action) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) { action.run(); return; }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override public void afterCommit() { action.run(); }
    });
}
```

- 机制是**编程式** `TransactionSynchronizationManager`，**不是** `@TransactionalEventListener`（全仓库无此注解，也无 `ApplicationEventPublisher` 参与该链路）。
- 调用点确实都在 `@Transactional` 方法内（`DepartmentTransferApprovalService.java:47` 提交、`:100` 审批决策），所以行为正确。
- ⚠️ **但这个保障完全依赖调用方**：`NotificationService` 自身没有 `@Transactional`。当 `isSynchronizationActive()` 为 false（**没有事务**）时，`:42` 会**立即内联执行** —— 也就是"提交后发送"静默退化成"立刻发送"。README 和注释都没提这个回退分支。将来任何人在非事务上下文里调用 `create()`，就会拿到一个"可能回滚但已经推给用户"的通知。

**完整通知流程**：

```text
① NotificationService.create(recipientId, type, title, content, businessType, businessId)
   ├─ mapper.insert(...)                     → 落 MySQL（sys_notification）
   └─ afterCommit(() -> realtime.send(recipientId, "notification.created", Map.of(...)))
                                              ↑ 事务提交后才真正推送
② 前端 AppLayout 的 onEvent 收到 notification.created
   ├─ 重新拉 GET /api/account/notifications        （权威列表）
   └─ 重新拉 GET /api/account/notifications/unread-count
③ 通知页与顶栏共用 notificationSlice，不会出现"一边已读、一边红点还在"
```

⚠️ **潜在 NPE**：`NotificationService.java:23-25` 用 `Map.of(...)` 拼推送载荷，而 `Map.of` **不接受 null 值**。如果 `businessId` 为 null，`afterCommit` 回调会在**事务已经提交之后**抛 NPE 冒泡成 500 —— 此时数据已提交、无法回滚。当前所有调用点 `businessId` 都非空，所以尚未触发，但这属于脆弱写法。

### 7.6 强制下线（跨实例精确定位到"那一台设备"）

```java
// RealtimeGateway.java:71-80
void closeSessionLocal(String sid) {
    for (Map<String, WebSocketSession> sessions : connections.values()) {
        for (Map.Entry<String, WebSocketSession> entry : Map.copyOf(sessions).entrySet()) {
            if (!sid.equals(entry.getValue().getAttributes().get("sid"))) continue;
            sessions.remove(entry.getKey());
            entry.getValue().close(CloseStatus.POLICY_VIOLATION);
        }
    }
}
```

设计要点：**不按 userId 关闭，而是按 session attribute 里的 `sid` 精确匹配**。所以同一用户在多台设备登录时，管理员踢其中一台，另外几台不受影响。

调用方（`SessionService`）：
- `SessionService.java:166` `remove(sid)` → `realtime.closeSession(sid)`
- `SessionService.java:172` `removeAll(userId)` → 对该用户所有 sid 逐个 `closeSession`

### 7.7 为什么还需要 REST 补拉（以及一个真实缺口）

Redis Pub/Sub 和 WebSocket 都是**瞬时通道**：断网期间的事件不会补发，也不做离线重放。所以真正的权威数据必须在 MySQL，实时通道只是"提醒你去拉"。

前端在以下时机重新拉取：收到 `notification.created` / `approval.request.updated`、浏览器标签重新可见（`visibilitychange`）。

⚠️ **但"重连后补拉"实际上没有实现**（这是一个真实缺口，与 `RealtimeGateway.java:63` 的注释和 `README.md:413` 的说法都不符）：

- 后端在握手成功后**主动**发 `connection.ready`（`RealtimeGateway.java:33`），意图就是让前端知道"重连成功了，该补数据了"。
- 但前端 `AppLayout.tsx:111-116` 的 `onEvent` **只处理** `notification.created` 和 `approval.request.updated`；`RealtimeClient.ts` 也**没有把 `onopen` 暴露给调用者**（`:32-34` 只是把 retry 计数清零）。
- 结果：**断线期间丢失的事件，只有在"下一次业务事件发生"或"标签页重新可见"时才会被补拉。** 如果用户一直盯着页面（标签页始终可见）、而期间恰好没有新事件，那么断线期间的丢失就是永久的。

修法很简单：`RealtimeClient` 暴露一个 `onOpen` 回调，或让 `AppLayout` 处理 `connection.ready` 事件并触发一次补拉。

### 7.8 Realtime 问题清单

| # | 问题 | 严重度 | 位置 |
|---|---|---|---|
| 1 | 重连成功不触发 REST 补拉（与注释/README 承诺不符） | 🟠 | `RealtimeClient.ts:32-34`、`AppLayout.tsx:111-116` |
| 2 | README 写 ticket TTL 30 秒，实际 60 秒 | 🟡 文档 | `README.md:386,400` vs `RealtimeTicketService.java:21` |
| 3 | 未对前端声明 `connection.ready` / `connection.ping`，契约不完整 | 🟡 | `RealtimeClient.ts:3-6` |
| 4 | 生产环境 WebSocket **无 Origin 限制**（`allowedOrigins` 仅在配置非空时设置，而 prod 默认为空） | 🟠 | `RealtimeWebSocketConfig.java:43-44`、`application-prod.yml:15` |
| 5 | `sendLocal` 不清空空用户桶（轻微泄漏） | 🟢 | `RealtimeGateway.java:59-60` |
| 6 | 心跳与订阅重试共用单线程调度器，5 秒发送阻塞会顺延任务 | 🟢 | `RealtimeGateway.java:31,83` |
| 7 | 无读空闲/pong 检测，半开连接不会及时清理 | 🟢 | `RealtimeGateway.java:84-87` |
| 8 | `Map.of` 不接受 null → `businessId` 为 null 时提交后抛 NPE | 🟡 | `NotificationService.java:23-25` |
| 9 | 无事务时"提交后发送"退化为"立即发送"（未文档化） | 🟡 | `NotificationService.java:42` |
| 10 | ticket 端点无频率限制（仅靠 60 秒 TTL 限制键数量） | 🟢 | `RealtimeController.java:23-29` |

---

## 8. Agent 模块详解

### 8.1 先明确当前能力边界（非常重要）

**框架是完整的，业务是空的。**

- 全仓库 `implements AgentTool` 的实现类 = **0 个**（我实测 grep 全仓确认，命中全部落在框架自身的 `ToolRegistry`/`ToolExecutionService`/`AgentRunner`/`AgentTool` 接口上）。
- 因为 `ToolRegistry` 用 `List<AgentTool<?,?>>` 构造注入，而 **Spring 对集合类型依赖在无候选 Bean 时注入空集合（不报错）**，所以应用能正常启动、注册表为空。
- 结果：`AgentRunner.java:62` 产出的 `toolDefinitions` 为空 → `OpenAiCompatibleModelGateway` 因此不下发 `tools` 字段 → **模型永远只做纯文本问答**。

所以准确的说法是：**现在这个 AI 助手可以聊天，但不能查询或修改任何系统业务数据。** `README.md:419` 的自我描述是诚实的。

### 8.2 为什么 AI 用 SSE 而不是 WebSocket

| | SSE（Server-Sent Events） | WebSocket |
|---|---|---|
| 方向 | 服务端 → 客户端 单向 | 双向 |
| 协议 | 就是普通 HTTP，`Content-Type: text/event-stream` | 需要 Upgrade 握手 |
| 适用 | "一个请求产生一串响应" | "服务端在任意时刻主动推送" |
| 代理/超时 | 天然适配 HTTP 超时与代理 | 需要专门配置（nginx 要 `Upgrade` 头） |

AI 对话正好是第一种：用户发一句话，模型逐段吐字。通知是第二种（服务端任意时刻主动推）。**项目按场景各选了正确的工具**，这个取舍是对的。

### 8.3 一次对话的完整链路

```text
用户点发送
  │  FE/components/ai/AiAssistant.tsx:66
  ▼
useAgentChat.send()                          FE/components/ai/useAgentChat.ts:44-108
  │   用 authenticatedFetch 发 POST（要带 Bearer，所以不能用 EventSource——
  │   EventSource 无法自定义请求头，见 :26-30 的注释解释）
  │   带 AbortController 的 signal，支持取消
  ▼
AgentChatController.chat()                   BE/agent/api/AgentChatController.java:58-98
  │   @PreAuthorize("hasAuthority('agent:chat:use')")            :59
  │   response.setHeader("Cache-Control", "no-cache, no-transform")  :61
  │   response.setHeader("X-Accel-Buffering", "no")   ← 关键！禁止 nginx 缓冲 SSE
  │   runId = UUID.randomUUID()                                   :64
  │   SseEmitter(properties.sseTimeoutMillis())  = 120 秒          :65
  │   Actor 权限快照 = access.actor()                              :67-69
  │   CancellationToken + deadline = now + 110 秒                  :70-72
  │   注册 onTimeout / onError / onCompletion → cancel              :82-86
  │   executor.submit(...)  ← 交给专用线程池                        :89
  ▼
agentExecutor（core=2, max=8, queue=100, AbortPolicy）  BE/agent/config/AgentAsyncConfig.java:22-33
  │   ★ 为什么必须异步：SSE 的 send() 必须在控制器方法返回之后才能由后台线程调用，
  │     否则 Spring 会把事件缓冲到返回时一次性 flush，完全没有流式效果（:11-17 注释）
  ▼
AgentRunner.run()                            BE/agent/application/AgentRunner.java:58-123
  │   ① visibleTo(actor) 取当前用户可见的工具                     :61
  │   ② 过滤掉 ApprovalPolicy.REQUIRED 的工具（不下发给模型）      :64
  │   ③ messages = [system 指令] + history + [本轮 user 消息]      :69-72
  │   ④ 循环最多 maxSteps(=5) 次：                                 :75
  │        model.complete(...)  ← 一次同步 HTTP 调用                :77
  │        ├─ 无 tool_calls → 发 message 事件，返回                :80-86
  │        └─ 有 tool_calls → 逐个：
  │              checkpoint()                                      :90
  │              发 tool_call 事件                                  :93
  │              ToolExecutionService.execute(...)                  :95
  │              把 result.modelSummary() 作为 tool 消息回填        :97
  │              发 tool_result 事件（含 artifact）                 :98
  │        （业务拒绝/权限不足 → 把安全文案回填给模型并发 error）    :99-102
  │        （取消 → 直接抛出）                                      :103-104
  │        （未知异常 → 只回通用文案，日志留全栈）                  :105-110
  │   ⑤ 超过步数 → "处理步骤过多，已中止"                          :114-118
  ▼
每个 AgentEvent 经 emit() 写成 SSE 帧           BE/agent/api/AgentChatController.java:126-133
  │   emitter.send(SseEmitter.event().name("agent").data(event))
  │   ★ 发送失败返回 false → 上层取消整个运行（说明客户端断了）     :104-107
  ▼
正常结束 → 发 done 事件 → completeQuietly()                        :109-113
```

### 8.4 SSE 的线上格式（逐字段核对过）

**SSE 帧名恒为 `agent`**（`AgentChatController.java:128`），事件类型在 JSON 体的 `type` 字段里：

```
event:agent
data:{"type":"message","runId":"...","toolName":null,"text":"你好","summary":null,"artifact":null}

event:agent
data:{"type":"tool_call","runId":"...","toolName":"user_summary","text":null,"summary":null,"artifact":null}

event:agent
data:{"type":"tool_result","runId":"...","toolName":"user_summary","text":null,"summary":"共 12 个用户","artifact":{...}}

event:agent
data:{"type":"error","runId":"...","text":"工具执行失败，请稍后重试",...}

event:agent
data:{"type":"done","runId":"...",...}
```

**前后端字段对照**（实测完全匹配）：

| 后端 `AgentEvent`（`BE/agent/domain/AgentEvent.java:4-10`） | 前端 `AgentStreamEvent`（`FE/types/agent.ts:10-17`） |
|---|---|
| `type`（`message`/`tool_call`/`tool_result`/`error`/`done`） | `type` ✅ |
| `runId` | `runId?` ✅ |
| `toolName` | `toolName?` ✅ |
| `text` | `text?` ✅ |
| `summary` | `summary?` ✅ |
| `artifact`（`AgentArtifact<T>{type, schemaVersion, title, data}`） | `artifact?: AgentArtifact` ✅ |

⚠️ **唯一的契约缺口**：`done` 事件后端会发（`:110`）、前端类型里也声明了（`types/agent.ts:11`），但解析分支（`useAgentChat.ts:83-94`）**没有 `done` 分支，被静默丢弃**。后果是前端**无法区分"正常结束"和"连接中断"**——`reader.read()` 返回 done 就直接 `return text`（`:72,100`），一个被截断的回复会被当作完整回复展示。

**前端 SSE 解析实现**（`useAgentChat.ts:70-99`，自建解析而非用库）：

```ts
while (true) {
  const { done, value } = await reader.read();
  if (done) break;
  buffer += decoder.decode(value, { stream: true });
  const lines = buffer.split('\n');
  buffer = lines.pop() ?? '';            // ★ 保留最后一段不完整行
  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed.startsWith('data:')) continue;   // 只认 data: 行
    const payload = trimmed.slice(5).trim();
    if (!payload) continue;
    try {
      const event = JSON.parse(payload) as AgentStreamEvent;
      if (event.type === 'message' && event.text) { text += event.text; onDelta?.(text); }
      else if (event.type === 'tool_call')  { setToolStatus(event.toolName ?? null); }
      else if (event.type === 'tool_result'){ setToolStatus(null); if (event.artifact) onArtifact?.(event.artifact); }
      else if (event.type === 'error')      { text += `\n\n[处理出错] ${event.text ?? ''}`; onDelta?.(text); }
    } catch { /* 忽略不完整/非 JSON 的分块 */ }
  }
}
```

处理得比较正确：按 `\n` 切行、保留残行到下一次、只认 `data:` 前缀、`JSON.parse` 失败就跳过（容忍 TCP 分片）。

⚠️ 但**背压完全没有处理**：`SseEmitter.send` 是同步写，Spring MVC 不提供缓冲上限或丢弃策略。如果模型一次性返回长文本，这一帧会以大批次写出。这里没有 WebSocket 那边 `ConcurrentWebSocketSessionDecorator` 的保护。

### 8.5 `AgentRunner` 的循环逻辑（这是 ReAct 模式）

```java
for (int step = 0; step < properties.maxSteps(); step++) {   // maxSteps = 5
    context.checkpoint();                                     // 超时/取消检查点
    ModelResponse response = model.complete(new ModelRequest(messages, toolDefinitions), context);
    List<ModelToolCall> calls = assistant.toolCalls();
    if (calls.isEmpty()) {                                    // 模型决定直接回答
        sink.accept(AgentEvent.message(runId, text));
        return new AgentRun(runId, text, results);
    }
    messages.add(ModelMessage.assistant(assistant.content(), calls));
    for (ModelToolCall call : calls) {
        ToolResult<?> result = toolExecution.execute(call.name(), call.arguments(), context);
        results.add(result);
        messages.add(ModelMessage.tool(call.id(), result.modelSummary()));  // 回填模型
        sink.accept(AgentEvent.toolResult(runId, call.name(), result.modelSummary(), result.artifact()));
    }
}
// 循环用尽
sink.accept(AgentEvent.message(runId, "处理步骤过多，已中止。请把问题拆分后重试。"));
```

设计要点：

- **循环上界是"模型调用次数"**（`maxSteps=5`），不是"工具调用次数"。所以一次可以并发执行多个工具调用。
- **工具结果只把 `modelSummary` 回填给模型**，大对象放 `artifact` 给前端渲染 —— 避免把整个业务对象塞进上下文烧 token。
- **工具失败会把安全文案回填给模型**（不是直接终止），模型可以据此调整策略或向用户解释。
- **`AgentRunner` 不承载任何业务规则**，只是一个编排器（`:32-35` 注释明确声明）。
- ⚠️ **observer 机制是空转的**：`AgentRunObserver` 全仓库**零实现**，`notifyStarted/Completed/Failed` 遍历的是一个空列表。所以 `AgentRun`（含 runId、最终文本、所有工具结果）**每次运行结束就丢弃**，没有审计、没有计费、没有会话持久化。`README.md:476` 说 `AgentRun` "供测试、审计"属于**过度陈述**。

### 8.6 工具框架（4 个类讲清楚）

#### `ToolRegistry`（`BE/agent/tool/ToolRegistry.java`）— 发现与过滤

```java
public ToolRegistry(List<AgentTool<?, ?>> tools) {        // Spring 收集全部实现 Bean
    for (AgentTool<?, ?> tool : tools) {
        String name = tool.descriptor().name();
        if (!NAME.matcher(name).matches()) throw ...;      // ^[a-z][a-z0-9_]{0,63}$
        if (description 为空) throw ...;                    // description 必填
        if (index.putIfAbsent(name, tool) != null) throw ...;  // 名称唯一
    }
    this.tools = Map.copyOf(index);
}
public List<AgentTool<?, ?>> visibleTo(AgentActor actor) {
    return tools.values().stream()
        .filter(tool -> actor.hasAll(tool.descriptor().permissions()))   // 按权限过滤
        .sorted(by name).toList();
}
```

**这三条校验在任何不合规的工具上直接让应用启动失败** —— 属于"早失败"的好设计：不会出现"工具悄悄没注册"这种难查的问题。

#### `ToolDescriptor` — 工具元数据

包含：`name`、`description`、`permissions`（所需权限码集合）、`risk`（`ToolRisk`）、`approval`（`ApprovalPolicy`）、`inputSchema`（给模型的 JSON Schema）、`timeout`（默认 30 秒）。

#### `ToolArgumentBinder` — JSON → 强类型 + 校验

`bindJson(json, type)`（`:43-51`）：先 `mapper.readValue(json, Map.class)`（失败 → "工具参数不是有效的 JSON 对象"），再 `convertValue` 成输入类型（失败 → "工具参数格式不正确"），最后跑 Jakarta Validation 并汇总违规：

```
"工具参数校验失败：<path> <message>；<path> <message>"
```

⚠️ `ToolArgumentBinder.bind(Map)`（`:24`）全仓库**没有调用者**，是死代码。

#### `ToolExecutionService` — 执行前再鉴权（关键安全设计）

```java
public ToolResult<?> execute(String toolName, String argumentsJson, AgentExecutionContext context) {
    context.checkpoint();
    AgentTool<?, ?> rawTool = registry.find(toolName);
    if (rawTool == null) throw new BusinessException("不存在可执行的工具：" + toolName);
    ToolDescriptor descriptor = rawTool.descriptor();
    // ★ 不信任"已经展示给模型了"这个事实，每次执行都重新查权限
    if (!context.actor().hasAll(descriptor.permissions()))
        throw new AccessDeniedException("没有使用工具 " + toolName + " 的权限");
    if (descriptor.approval() == ApprovalPolicy.REQUIRED)
        throw new BusinessException("工具 " + toolName + " 需要用户确认，当前请求尚未获得批准");
    return executeTyped(rawTool, argumentsJson, context);
}
```

`executeTyped` 还会：建立**工具独立 deadline**（取"工具自身超时"与"整个运行 deadline"的较小值）、执行后再次 `checkpoint()`、并**强制要求 `modelSummary` 非空**（否则抛异常）。

⚠️ **"超时"实际上是"检查点"，不是"可强制中断"**：`ToolExecutionService.java:38-42` 只是把 deadline 传进去，然后执行前后各查一次 `checkpoint()`。**没有任何调度、隔离或中断机制**。一个内部阻塞的工具（无超时的 HTTP、JDBC、锁等待）会无限期占用 agent 线程，deadline 形同虚设。同理，`future.cancel(true)` 只能打断可中断的阻塞点。当前 0 个工具使这个问题还没暴露，但框架契约就是如此 —— 写工具时**必须自己给 IO 设超时**。

### 8.7 模型网关接线

配置前缀 `lbl.agent`（`BE/agent/config/AgentProperties.java:26-39`，全部有 `@DefaultValue`）：

| 配置键 | 默认值 | 说明 |
|---|---|---|
| `base-url` | `""` | OpenAI 兼容网关根地址（如 `https://llm-gw.intra`） |
| `api-key` | `""` | 密钥，走 `LLM_API_KEY` 环境变量 |
| `model` | `""` | 模型名 |
| `user-agent` | `KariyaAdmin-Agent/1.0` | ★ 某些内网 nginx 会拦截无 UA 的请求，所以必须显式设置 |
| `chat-path` | `/v1/chat/completions` | 若网关把版本号放进 base-url，则改成 `/chat/completions` |
| `max-steps` | `5` | ReAct 循环上限 |
| `temperature` | `0.1` | 采样温度；排查类任务建议 0~0.2 |
| `timeout-seconds` | `60` | 单次 LLM 请求的 connect/read 超时 |
| `run-timeout-seconds` | `110` | 一次完整运行的总时限 |
| `strict-tool-schema` | `false` | 网关是否支持 OpenAI strict schema，不确定时保持 false |
| `sse-timeout-millis` | `120000` | SSE 总超时（必须 > run-timeout） |

`local` profile 的显式绑定在 `RES/application-local.yml:156-168`（`base-url` 默认 `https://api.deepseek.com`、`model` 默认 `deepseek-flash`）。

**关键实现事实**：

| 项 | 事实 |
|---|---|
| 流式还是非流式 | **非流式**。请求体 `ChatRequest` 里**没有 `stream` 字段**，响应按一次性 JSON 解析。所以前端看到的"逐段吐出"其实来自 SSE 的逐事件推送，而不是模型逐 token 流 |
| `tool_calls` 解析 | `OpenAiCompatibleModelGateway.java:70-73`，过滤 `function != null` |
| `tool_choice` | **硬编码为 `"auto"`**，且仅在 `tools` 非空时发送（`:44`） |
| 超时 | `connect = read = 60_000 ms`（`:102-105`，`SimpleClientHttpRequestFactory`） |
| 出站代理 | **没有配置**。对比 `lbl.external-auth` 显式配了 `proxy-host/proxy-port`（默认 `127.0.0.1:7890`），说明该部署网络依赖出网代理 —— **LLM 出站没有代理兜底，这是一个实际部署风险点** |
| 未配置时的行为 | `ensureConfigured()` 抛 `ModelGatewayException("AI 助手尚未配置模型网关")` |

⚠️ **`application-prod.yml` 里完全没有 `lbl.agent` 段**。生产环境若不加 `LLM_*` 环境变量，AI 助手会以"尚未配置模型网关"失败。（这与 §12 的 prod 配置缺失是同一类问题。）

### 8.8 `ApprovalPolicy.REQUIRED` 的真相：声明即不可用

这是必须说清楚的：**`REQUIRED` 是一个"死枚举值"**。

全仓库只有两处引用 `REQUIRED`：

1. **静默排除**（`AgentRunner.java:62-67`）—— REQUIRED 的工具**不会被写进请求的 `tools`**，模型根本看不到它，不可能调用：
   ```java
   // 审批请求/恢复协议尚未实现前，不把无法执行的工具暴露给模型。
   .filter(tool -> tool.descriptor().approval() == ApprovalPolicy.NOT_REQUIRED)
   ```
2. **运行时硬拒绝**（`ToolExecutionService.java:29-31`）—— 即使模型"幻觉"出这个工具名，也被直接拒绝。

**不存在**任何 approve/pending/resume 载荷、事件类型、API 端点或前端确认 UI。`AgentEvent` 只有 5 种类型（`message`/`tool_call`/`tool_result`/`error`/`done`），没有 `approval_required`。

**结论**：写操作类工具在确认协议实现之前**无法通过配置绕过** —— 如果你想加一个会修改数据的工具，你必须先实现确认/恢复协议（见 §14）。

### 8.9 并发容量（实测配置推算）

`AgentAsyncConfig.java:25-30`：`corePoolSize=2`、`maxPoolSize=8`、`queueCapacity=100`、`AbortPolicy`、`waitForTasksToCompleteOnShutdown=false`。

- 每个 SSE 请求占一个线程直到结束（**最长 110 秒**）。
- 第 3~10 个并发请求：创建新线程（max 8）。
- 第 11~110 个：进队列排队。
- 第 111 个起：**被 `AbortPolicy` 拒绝** → 返回 **HTTP 200 + 一个 `error` 事件**"AI 助手当前请求较多，请稍后重试"（`AgentChatController.java:92-96`）。前端会把这段文案**拼进回复文本**（`useAgentChat.ts:91-93`）。
- **没有用户级配额，也没有全局运行数限制。**

### 8.10 Agent 问题清单

| # | 问题 | 严重度 | 位置 |
|---|---|---|---|
| 1 | `ApprovalPolicy.REQUIRED` 无确认/恢复协议，是不可用的死枚举 | 🟠 | `AgentRunner.java:64`、`ToolExecutionService.java:29-31` |
| 2 | "工具超时"只是 deadline 传递 + 检查点，**无法中断**阻塞工具 | 🟠 | `ToolExecutionService.java:38-42` |
| 3 | `future.cancel(true)` 无法终止工具内的非中断阻塞 | 🟠 | `AgentChatController.java:77-81` |
| 4 | 前端忽略 `done` 事件 → 无法区分"正常结束"与"连接中断"，截断回复当完整展示 | 🟠 | `useAgentChat.ts:83-94` |
| 5 | SSE 超时（120s）期间不发任何 error 事件，用户看到"回复突然结束且无提示" | 🟠 | `AgentChatController.java:114-115` |
| 6 | 异常分支里 `emit` 失败也会 `completedNormally.set(true)`，取消信号可能丢失 | 🟡 | `AgentChatController.java:118-122` |
| 7 | `AgentRunObserver` 零实现、`AgentRun` 无持久化 → 无审计/无计费/无回放 | 🟡 | `AgentRunner.java:45,133-147` |
| 8 | SSE 无背压控制（对比 WebSocket 有 128 KiB 上限） | 🟡 | `AgentChatController.java:126-133` |
| 9 | 前端无长度前置校验：超限时后端返回 400 JSON，前端把"请求失败（HTTP 400）"当作助手回复显示，错误语义丢失 | 🟡 | `AiAssistant.tsx:60`、`useAgentChat.ts:62-64` |
| 10 | `AiAssistant.tsx:61-63` 用 `Date.now()` / `Date.now()+1` 生成消息 id，同毫秒内可能撞 id | 🟢 | `AiAssistant.tsx:61-63` |
| 11 | `ToolArgumentBinder.bind(Map)` 死代码 | 🟢 | `ToolArgumentBinder.java:24` |
| 12 | 生产 profile 无 `lbl.agent` 配置 → AI 不可用 | 🟠 | `application-prod.yml` 全文 |
| 13 | LLM 出站无代理配置，而外部登录显式配了代理 → 部署网络不一致 | 🟡 | `OpenAiCompatibleModelGateway.java:102-105` |

### 8.11 新增第一个 Agent 工具的标准做法

推荐把实现放在**所属业务模块**下，例如 `BE/system/user/adapter/agent/UserSummaryTool.java`：

1. 定义一个带 Validation 注解的输入 `record`；
2. 实现 `AgentTool<Input, Output>` 并注册为 Spring `@Component`；
3. `descriptor()` 用稳定的 **snake_case** 名称，声明**最小权限**、`risk`、`approval`、`inputSchema`、`timeout`；
4. `execute()` **只调用已有的 Service**，绝不直接用 Mapper / Controller / `SecurityContextHolder`；
5. 用 `context.actor()` 传递用户身份，业务 Service 仍然要自己做数据范围校验；
6. 返回**简短**的 `modelSummary`，敏感或大量数据放进 `artifact`；
7. 为"不可见 / 无权限 / 参数非法 / 超时 / 取消 / 业务失败"分别写测试；
8. **给所有 IO 自己设超时**（框架的 deadline 只是检查点，不会中断你）；
9. 写操作工具在确认协议完成前**不要**设成可直接执行，也不要用 `NOT_REQUIRED` 绕过。

---

## 9. 前端详解

**实测依赖版本**（`package.json` 是 `^` 范围，以下是 `node_modules` 里实际装的）：

| 包 | 声明 | 实装 |
|---|---|---|
| react / react-dom | `^18.3.1` | 18.3.1 |
| react-router-dom | `^7.6.2` | **7.18.4** |
| antd | `^5.26.6` | 5.29.3 |
| vite | `^6.3.5` | 6.4.3 |
| typescript | `~5.8.3` | 5.8.3 |
| axios / @reduxjs/toolkit | `^1.10.0` / `^2.8.2` | 1.10.x / 2.x |
| echarts / @antv/g6 / monaco-editor | `^5.6.0` / `^5.0.49` / `^0.52.2` | — |

> 注意：装的是 **react-router-dom v7**，但代码用的是 v6.4 引入的 `createBrowserRouter` + `RouterProvider` 数据路由 API。这套 API 在 v7 里仍然可用，所以不算错误，但要知道版本与写法不完全对应。

### 9.1 启动与 Provider 嵌套

`FE/main.tsx:14-23`：

```text
StrictMode
 └─ Provider(store)            ← Redux
     └─ ThemeProvider          ← AntD ConfigProvider + cssVar key 'app' + zhCN
         └─ AntApp             ← AntD 的 App（提供 message/modal/notification 上下文）
             └─ RouterProvider(router)
```

`nprogress.css` 在 `global.css` **之前**引入（`main.tsx:11-13`），所以后者的覆盖生效。

### 9.2 路由体系：静态骨架 + 一个动态出口

`FE/router/index.tsx:35-72` 只登记 **7 条静态路由**（都不随业务变化）：

| 路径 | Element | 说明 |
|---|---|---|
| `/login` | `LoginPage` | 公开 |
| `/register` | `RegisterPage` | 公开 |
| `/auth/callback` | `CallbackPage` | 第三方登录回调 |
| `/account/setup` | `AuthGuard` 包 `AccountSetupPage` | 开户确认态唯一落点 |
| `/404` | `NotFound` | **刻意不套 AuthGuard**（`:48-51` 注释解释：这是个"地址不存在"，与登录状态无关；套了会让未登录用户先被送去登录页、登录完才看到 404） |
| `/` | `AuthGuard` 包 `AppLayout` | 工作台外壳 |
| `/` 的子路由 | `index` → `MenuHomeRedirect`；`403` → `Forbidden`；`account/security`、`account/notifications`、`account/approvals`；`*` → **`DynamicPage`** | |

关键点：**`*` 通配吃掉所有业务路径，统一交给 `DynamicPage` 解析**。所以新增业务页面**不需要改这个文件**。

`:30-33` 的注释还解释了一个细节：不需要再放一个顶层 `*`，因为 `/` 下的 `*` 已经能匹配任意路径，两个 splat 互相竞争反而会让 404 落到哪条变得不确定。

### 9.3 `componentRegistry` —— 整套动态路由唯一需要理解的一点

`FE/router/componentRegistry.tsx:19-22`：

```ts
const modules = import.meta.glob([
  '../pages/**/*.tsx',
  '!../pages/error/**',      // 错误页不是业务页面
  '!../pages/login/**',
  '!../pages/register/**',
  '!../pages/auth/**',
  '!../pages/account/**',
]) as Record<string, () => Promise<{ default: ComponentType }>>;
```

`import.meta.glob` 是 **Vite 的构建期能力**：`npm run dev` / `npm run build` 时扫描磁盘，生成 `{ '../pages/system/user/index.tsx': () => import(...) }` 这样的**懒加载映射**。

**实测：编译进 dist 的条目是 12 个**（7 个页面 `index.tsx` + 5 个 `*Dialog.tsx`）。负向模式确实生效了。

`resolveMenuComponent`（`:45-58`）把菜单里的 `component` 字符串（如 `system/user/index`）翻译成组件：

```ts
const key = `${PREFIX}${normalize(component)}${SUFFIX}`;   // '../pages/' + 'system/user/index' + '.tsx'
const cached = resolved.get(key);
if (cached) return cached;                                 // ★ 必须缓存
const loader = modules[key];
if (!loader) return null;                                  // 解析不到 → PageUnavailable 诊断页
const page = lazy(() => trackProgress(loader));            // 进度条反映"真的在下载这个页面"
resolved.set(key, page);
```

`:27-31` 的注释解释了为什么必须缓存：**`React.lazy()` 每次调用都返回一个全新的组件类型**，如果每次渲染都重新 `lazy` 一次，React 会认为是另一个组件从而卸载重建 → 表现为页面反复刷新、接口被重复请求。

三个导出函数：

| 函数 | 用途 |
|---|---|
| `resolveMenuComponent(component)` | 菜单 component → 组件（或 null） |
| `availableComponents()` | **当前产物里真实存在的页面清单**，菜单管理的"前端组件"下拉直接读它 → 不会配出渲染不出来的菜单 |
| `expectedPageFile(component)` | 翻译成开发者该创建的文件路径，供诊断页展示 |

⚠️ **一个真实 bug**：`availableComponents()` 会把 5 个 `*Dialog.tsx` 也当成合法页面 —— 它们只有具名导出、**没有 `default` 导出**。菜单管理下拉（`MenuDialog.tsx:52`）可以选中它们，然后 `lazy` 拿到的 `default` 是 `undefined`。而**全应用没有任何 ErrorBoundary**（grep 无 `componentDidCatch` / `getDerivedStateFromError`）→ **白屏**。
修法：glob 加 `'!../pages/**/*Dialog.tsx'`，或者在 `resolveMenuComponent` 里校验 `default` 存在，或加一个 ErrorBoundary。

### 9.4 `DynamicPage`：403 / 404 / 诊断页的三级判定

`FE/router/DynamicPage.tsx:29-81`：

```text
① pathname 在「我的菜单树」里
     ├─ component 能解析到 → 渲染页面
     └─ component 解析不到 → <PageUnavailable/>（提示应创建哪个文件）
② 不在我的菜单树、但 s.auth.routes（全站目录）里有 → <Forbidden/>（403）
③ 全站目录也没有 → <Navigate to='/404' replace/>
```

**为什么"在我的菜单树里"就等于"有权限访问"**（`:21-24` 注释）：`MenuMapper.selectByUserId` 已经用递归 CTE 算出了"这个用户能访问的菜单集合"。**这张菜单树本身就是授权结果**，前端不需要再维护一份"路径 → 权限码"的映射表。

⚠️ 注意 `visible=0`（隐藏）的菜单**仍然可以访问**，只是不出现在侧边栏 —— 这是"隐藏"应有的语义（详情页、带内部参数的页面常用这种做法），所以**不要在 `DynamicPage` 里过滤 `visible`**（`:26-27` 注释明确说明）。

`MenuHomeRedirect`（`:95-100`）——根路径落点，这段注释很值得看（`:83-93`）：

> 一个可见页面都没有时（例如账号只被授了按钮权限），以前会回退到写死的 `/home`，而那多半是个没配过或没授权的地址 —— 结果就是登录后立刻看到 403/404，并且因为 403/404 页上的出口按钮用同一个函数，**用户会被永久困在错误页里**。这里改为给出一个明确的"无可用页面"空状态，不再跳到任何算出来的地址，循环自然消失，用户也始终有"退出登录"这条路可走。

这是把"历史上踩过的坑"固化进代码和注释的典型案例。

### 9.5 `keepAlive` 的真实实现（以及它的 bug）

菜单可以配 `keep_alive=1`。`DynamicPage.tsx:35-48,54-75` 的实现方式是：

- 用一个 `cachedPaths: string[]` state 记录"需要保活的路径"；
- 把每个缓存的页面渲染成 `<div key={path} style={{display: path === pathname ? undefined : 'none'}}>`，**用 `display:none` 隐藏而不卸载** → 组件状态与滚动位置都保留（这是真正的 keepAlive）；
- 菜单变化时清理已失效的缓存路径（`:41-48`）。

⚠️ **真实 bug（影响所有 `keepAlive=1` 页面）**：首次进入时会**挂载两次**。

`cachedPaths` 初始为空 → 走 `:69-73` 渲染 `<Page/>`；紧接着 effect（`:36-40`）把 pathname 追加进 `cachedPaths` → 重新渲染时走 `:57-68` 的 `<div key={path}>` 分支。**React 认为这是不同位置的元素 → 卸载重挂**。后果：任何 keepAlive 页面**首次访问都会重复发起首屏请求、并丢弃第一份状态**。

⚠️ 另外这个"缓存"的寿命比想象中短：一旦路由走到 403/404（`:77-80`）或进入 `/account/*` 这类**静态路由**，`DynamicPage` 本身卸载 → 全部缓存销毁。而且**没有数量上限**，`keepAlive` 页面开得越多内存越高。

### 9.6 前端鉴权三件套

#### `AuthGuard`（`FE/components/common/AuthGuard.tsx`）

```tsx
useEffect(() => {
  if (token && profileReady) { setChecking(false); return; }   // 已就绪 → 不重复请求
  const restore = async () => {
    try {
      const accessToken = token ?? (await refreshAccessTokenOnce());   // 用 Cookie 换新 token
      const profile = await fetchMe();                                  // 拿身份+权限+菜单+全站目录
      dispatch(setSession({...}));
      dispatch(setProfile({ permissions, menus, routes }));              // routes 用于区分 403/404
      if (principalType === 'ONBOARDING' && location.pathname !== '/account/setup')
        navigate('/account/setup', { replace: true });
    } catch {
      dispatch(clearSession());
      navigate(`/login?redirect=${encodeURIComponent(location.pathname)}`, { replace: true });
    } finally { if (active) setChecking(false); }
  };
  void restore();
  return () => { active = false; };
}, [dispatch, navigate, profileReady, token]);
```

`profileReady` 这个标志的意义：**profile 已就绪后，普通页面切换不再重复请求 `/auth/me`**。

`:47-57` 有一段很精彩的注释，解释为什么开户态要在跳转生效前保持 Loading：

> effect 里已经会把人送到那里，但"检查完成 → children 可见"这一帧仍会先渲染一次被保护的页面（通常是 AppLayout 工作台），于是工作台会立刻发起一批对开户态**必然 403** 的请求：未读消息数、实时连接票据（还会因此建一次注定失败的 WebSocket 重连退避）。除了控制台噪音，也会让"这个临时身份到底能不能用系统"变得难以判断。

⚠️ **真实 bug：每次整页加载都会请求 `/auth/me` 两次**。原因：effect 的依赖数组里有 `token`，而 `refreshAccessTokenOnce()` 内部会 `dispatch(setAccessToken(...))` 让 `token` 从 `null` 变成字符串 → **effect 重跑** → 此时 `profileReady` 仍为 false → 再走一遍 → **第二次 `fetchMe()`**。（生产环境 2 次，StrictMode 开发环境 3 次。）
修法：把 `token` 从依赖数组移除（用 `useRef` 或 `store.getState()` 读），或加一个 `startedRef` 闸门。

#### `request.ts`（`FE/utils/request.ts`）

```ts
const request = axios.create({ baseURL: import.meta.env.VITE_API_BASE_URL, withCredentials: true, timeout: 12_000 });

// 请求拦截器：起进度条 + 注入 Bearer
// 响应拦截器：
//   成功 → doneProgress()
//   失败 → doneProgress()  ★ 必须放在所有早退分支之前
//         if (status !== 401 || _retry || AUTH_BOOTSTRAP.test(url)) → reject
//         _retry = true
//         token = await refreshAccessTokenOnce()
//         重放原请求（用裸 axios 避免递归）
//         失败 → resetProgress() + expireSession()
```

三个设计点值得单独说：

1. **`AUTH_BOOTSTRAP` 是精确白名单，不是前缀匹配**（`:18`）：
   ```ts
   const AUTH_BOOTSTRAP = /\/auth\/(refresh|me|touch|login|logout|register|captcha|external)\b/;
   ```
   `:13-16` 的注释解释了为什么不能用 `/\/auth\//` 前缀匹配：那会误命中 `/auth/onboarding/account/*` —— **这两个开户确认接口真正需要访问令牌与静默续期**。

2. **续期用裸 `axios` 而非 `request` 实例**（`:50`）：避免续期请求自身再被这个拦截器处理而形成递归。也刻意**不套 `trackProgress`** —— 静默续期不该被用户察觉。

3. **`doneProgress()` 的位置有注释专门保护**（`:41-44`）：
   > 这个拦截器有 4 条 return 路径（不重试 / 重试成功 / 重试失败 / 抛异常），任何一条漏掉 `doneProgress()` 都会让计数器永久 +1 —— 表现为进度条跑到一半不再消失。

#### `authSession.ts`（`FE/services/authSession.ts`）

- **全应用唯一的续期入口**：`refreshPromise ??= axios.post(...).finally(() => refreshPromise = null)` —— 并发 401 共享同一个 Promise。
- `authenticatedFetch`（`:40-57`）给**不能用 Axios 的请求**（SSE）提供同样的 Bearer + 续期。注意它的重试**只在响应体开始读取之前**发生。
- `expireSession`（`:29-37`）用 `expirationHandled` 标志去重，500ms 后复位以免永久锁死。
  ⚠️ 但这个 500ms 定时器因 `window.location.assign` 造成整页跳转，**实际上是死代码**。

### 9.7 `Permission` 与前端权限码

`FE/permission/Permission.tsx` 只有 9 行：

```tsx
export function usePermission() {
  const permissions = useAppSelector((s) => s.auth.permissions);
  return (code: string) => permissions.includes(code);
}
export function Permission({ code, children }: { code: string; children: ReactNode }) {
  return usePermission()(code) ? <>{children}</> : null;
}
```

**它只改善界面，不承担任何安全责任**（后端 `@PreAuthorize` 才是边界）。

**前端共使用 18 个权限码、19 处**（实测）：

| 权限码 | 位置 |
|---|---|
| `agent:chat:use` | `AppLayout.tsx:309` |
| `system:user:add` | `user/index.tsx:174` |
| `system:user:update` | `user/index.tsx:71`、`:78` |
| `system:user:delete` | `user/index.tsx:111` |
| `system:user:export` | `user/index.tsx:169` |
| `system:user:reset-password` | `user/index.tsx:85` |
| `system:role:add` | `role/index.tsx:108` |
| `system:role:update` / `:delete` / `:grant` | `role/index.tsx:56` / `:61` / `:51` |
| `system:menu:add` / `:update` / `:delete` | `menu/index.tsx:132` / `:88` / `:93` |
| `system:dept:add` / `:update` / `:delete` | `dept/index.tsx:126` / `:72` / `:77` |
| `system:loginlog:delete` | `loginlog/index.tsx:88` |
| `system:operatelog:delete` | `operatelog/index.tsx:96` |

**所有这 18 个码在后端 `init_data.sql` / `SystemPermissionInitializer` 中都存在**（已逐个核对）。`:list` 系列前端**从不校验** —— 页面可达性由菜单树决定，符合设计。

### 9.8 状态管理（Redux）

#### `authSlice`（40 行，7 个字段）

| 字段 | 类型 | 说明 |
|---|---|---|
| `accessToken` | `string \| null` | **只放内存，不落 localStorage** |
| `principalType` | `'MEMBER' \| 'ONBOARDING' \| null` | 区分正式用户与开户态 |
| `user` | `CurrentUser \| null` | 含 `passwordChangeRequired` |
| `onboarding` | `OnboardingProfile \| null` | 第三方身份快照 |
| `permissions` | `string[]` | 权限码 |
| `menus` | `MenuRoute[]` | **我的菜单树** → 侧边栏、面包屑、搜索、DynamicPage 都用它 |
| `routes` | `MenuRouteSummary[]` | **全站页面目录** → 只用于区分 403 与 404 |

Actions：`setSession`、`setAccessToken`、`setProfile`、`setPasswordChangeRequired`、`clearSession`（重置为 initialState）。

**`clearSession` 是"会话结束"的唯一收口** —— 退出登录、无可用页面、续期失败、AuthGuard 校验失败、开户确认页退出，全部走它。

#### `notificationSlice`（111 行）

这是**一个被修复过的真实 bug 的产物**，注释把问题写得很清楚（`:11-25`）：

> 之前两个组件各自持有一份 `useState`：铃铛拉 `getNotifications(6)` + 未读数，消息中心拉 `getNotifications(100)`。于是两边永远对不上 —— 在消息中心点"全部标为已读"，顶栏红点不会消失……用户看到的是"我明明都读过了，红点还在"。
>
> 不保留"顶栏 6 条 / 页面 100 条"两套列表：两个 fetch 谁后回来谁覆盖，会出现"顶栏只显示 2 条"这种随机的表现。

现在的做法：**统一拉 `NOTIFICATION_FETCH_LIMIT = 100` 条，顶栏下拉与消息中心页展示同一份列表 —— 取数一处、展示两处**；顶栏改用 CSS 高度上限 + 内部滚动代替截断。

另一个关键设计（`:97-107`）：**`clearSession` 时清空通知状态**。注释解释了原因：

> 状态上移到全局 store 之后，它不再随 `AppLayout` 卸载而消失。退出登录后换另一个账号登录（同一个 SPA 会话内，`/login` 是前端路由而不是整页刷新），在新账号的首次拉取返回之前，顶栏会显示**上一个账号**的通知与未读数 —— 属于串号泄露。

这是个很到位的安全意识。

### 9.9 实时通知前端

`FE/services/RealtimeClient.ts`（56 行，前面 §7.2 已详述握手）。要点：

- 构造时传入 `onEvent` 回调；`start()` / `stop()` 管生命周期；`stopped` 标志阻止停止后重连。
- 重连退避：`delay = Math.min(30_000, 1000 * 2 ** Math.min(this.retry++, 5)) + Math.floor(Math.random() * 500)`
  → 序列约 1s / 2s / 4s / 8s / 16s / 30s / 30s…，带 0~499ms 抖动；**`onopen` 时归零**。
- ⚠️ **没有最大重试次数**：永久失败（后端挂了）会一直重试到 30 秒一次，且**不区分 4xx（票无效，重试无意义）与网络故障**。

`AppLayout` 里的事件处理（`:111-116`）只认两个业务事件：

```tsx
if (type === 'notification.created' || type === 'approval.request.updated') {
  // 重新拉通知列表 + 未读数（权威数据）
}
```

两条事件名与后端 `NotificationService.java:23,38` **完全一致**。`connection.ready` / `connection.ping` 前端不处理（见 §7.7 的缺口）。

### 9.10 AI 前端

`FE/components/ai/useAgentChat.ts`（111 行）在 §8.4 已贴出解析实现。补充要点：

- **为什么不用 Axios / EventSource**（`:26-30` 注释）：Axios 拦截器按"一个完整 JSON 响应"设计，不适合逐块读流；原生 `EventSource` **不能自定义请求头**，无法带 `Authorization`（JWT 在内存里）。所以用 `fetch` + `ReadableStream` 自建解析，认证与 401 续期复用 `authenticatedFetch`。
- `cancel()` 用 `AbortController`；**组件卸载时自动取消**（`useEffect(() => cancel, [cancel])`，`:42`）。
- `toolStatus` 暴露"正在调用 xx…"给 UI。

⚠️ **真实 bug：并发发送会错乱 loading 状态**。`useAgentChat.ts:101-105` 的 `finally` 只保护 `controllerRef`，不保护 `loading`：`send2` 内部先 `cancel()` 掉 `send1`，随后 `send1` 的 `finally` 执行 `setLoading(false)`，**把 `send2` 的加载态清掉**。虽然 UI 上 `loading` 有守卫降低了触发概率，但逻辑是错的。

⚠️ `AiAssistant.tsx:61-63` 用 `Date.now()` / `Date.now() + 1` 生成消息 id，同毫秒内连续发送可能撞 id，导致 `setMessages` 的 `map`/`filter` 命中错误气泡。

### 9.11 页面清单

| 页面 | 路径 | 调用接口 | 权限点 |
|---|---|---|---|
| 登录 | `pages/login/` | `/auth/login`、`/auth/captcha/*`、`/auth/external/providers` | — |
| 注册 | `pages/register/` | `/auth/register`、验证码 | — |
| 回调 | `pages/auth/CallbackPage.tsx` | `/auth/refresh`、`/auth/me` | — |
| 开户 | `pages/account/setup/` | `/auth/onboarding/account/create|bind` | `onboarding:account:*` |
| 账号安全 | `pages/account/security/` | `/account/profile`、`/account/sessions`、`/account/identities`、`/account/department-change/*` | — |
| 消息中心 | `pages/account/notifications/` | `/account/notifications*`、`/account/department-change/{id}` | — |
| 待办中心 | `pages/account/approvals/` | `/account/approvals`、`/account/department-change/{id}/approve|reject` | — |
| 首页 | `pages/home/` | — | — |
| 用户管理 | `pages/system/user/` + `UserDialog` | `/system/users/**` | `system:user:*` |
| 角色管理 | `pages/system/role/` + `RoleDialog` + `RolePermissionDialog` | `/system/roles/**` | `system:role:*` |
| 菜单管理 | `pages/system/menu/` + `MenuDialog` | `/system/menus/**` | `system:menu:*` |
| 部门管理 | `pages/system/dept/` + `DeptDialog` | `/system/depts/**` | `system:dept:*` |
| 登录日志 | `pages/system/loginlog/` | `/system/login-logs` | `system:loginlog:*` |
| 操作日志 | `pages/system/operatelog/` | `/system/operation-logs` | `system:operatelog:*` |
| 错误页 | `pages/error/` | — | — |

⚠️ `pages/notifications` 用 `businessId` **直接查部门变更接口**（`:31-37`）而不管 `businessType` —— 目前只有一种业务所以不出错，但"统一待办"一旦接入第二种业务就会错。

### 9.12 内网 / 离线评估（结论：前端是干净的）

| 检查 | 结果 |
|---|---|
| `src` 里有 CDN / 外部域名吗 | ❌ **没有**。`https?://` 只命中注释与示例文本 |
| 字体 | 只用系统栈（Inter / JetBrains Mono 等），**无 `@font-face`、无 Google Fonts** |
| 图标 / ECharts / G6 | 全部打包进产物 |
| Monaco Editor | ✅ 同源。`components/CodeViewer/monaco.ts:11-16` 用 `new URL(`${import.meta.env.BASE_URL}monaco/vs`, window.location.origin)` 设 loader paths，`public/monaco/vs` 原样拷入 `dist/monaco/`（13.35 MB），**无 `MonacoEnvironment` / `getWorkerUrl` 覆写 → 离线可用** |
| 硬编码 IP / 地址 | ❌ 没有（只有 `home/index.tsx:121` 的 YAML 示例串含 `127.0.0.1:3306`） |
| `.env.production` | 全文一行：`VITE_API_BASE_URL=/api` → **要求前后端同源部署** |
| `.env.development` | `VITE_API_BASE_URL=http://localhost:8080/api`，**vite 没有配 proxy** → 开发态依赖后端 CORS |
| `vite.config.ts` | 3 行：只有 `plugins:[react()]` 和 `server.port=5173`。**没有 `base`、没有 `server.proxy`、没有 build 配置** |

⚠️ **子路径部署不支持**：`vite.config.ts` 没配 `base`（`BASE_URL` 恒为 `/`），但 `monaco.ts:10` 的注释宣称支持子路径部署。路由与资源引用全是绝对路径 → 部署在 `https://host/admin/` 下会 404。**要子路径部署必须显式配 `base`。**

**dist 体积拆解**（实测 136 文件 / 20.43 MB）：

| 部分 | 体积 |
|---|---|
| `monaco/` | 13.35 MB（最大单文件 `tsWorker.js` 5.8 MB、`editor.main.js` 3.77 MB） |
| `assets/` | 5.99 MB |
| 最大 JS chunk | `index-B9IMYueZ.js`（首页）**2.39 MB raw / 755 KB gzip** |
| 入口 chunk | `index-O5UBUTV5.js` **1.47 MB / 477 KB gzip** |
| `SmartTable` chunk | 0.11 MB / 38 KB |

⚠️ **dist 未被 git 跟踪**（`.gitignore` 里有 `dist/`）→ 它是你本机的构建残留，**新机器 clone 后必须自己 `npm run build`**。
⚠️ 注意 `public/favicon.ico` 是 **996 KB**，与 `src/assets/logo.png` 的 SHA-256 **完全相同** —— 它实际是个 PNG 被改了扩展名，还被 `LoadingScreen` 当加载图用。首页 chunk 2.39 MB 的主因是 `background.png`(997KB) + `logo.png`(996KB) 两张近 1MB 的图。

### 9.13 前端问题清单

| # | 问题 | 严重度 | 位置 |
|---|---|---|---|
| 1 | **无 ErrorBoundary** + `availableComponents()` 把 5 个无 `default` 导出的 `*Dialog.tsx` 当合法页面 → 菜单可选中 → 白屏 | 🔴 | `componentRegistry.tsx:64-68`、`MenuDialog.tsx:52` |
| 2 | **`keepAlive` 页面首次进入挂载两次**（重复请求 + 丢弃首份状态） | 🔴 | `DynamicPage.tsx:57-73` |
| 3 | **`AuthGuard` 每次整页加载请求 `/auth/me` 两次**（依赖数组含 `token`） | 🟠 | `AuthGuard.tsx:18-45` |
| 4 | **dayjs 未加载 `zh-cn` 语言包**（全项目无 `dayjs/locale/zh-cn`），RangePicker 面板周/月缩写可能仍是英文 | 🟠 | 全局；影响 `loginlog:83`、`operatelog:91` |
| 5 | **一键已读静默失败**：`dispatch(readAllNotifications())` 没 `.unwrap()`，thunk 不会抛 → 失败无提示（同文件另一处却用了 `unwrap`，不一致） | 🟠 | `notifications/index.tsx:48-50`、`AppLayout.tsx:137` |
| 6 | AI 并发发送错乱 `loading` 状态 | 🟡 | `useAgentChat.ts:101-105` |
| 7 | `G6Graph` 渲染失败即泄漏（`rendered=false` 时 cleanup 既不销毁也不移除节点） | 🟡 | `G6Graph.tsx:28-40` |
| 8 | `RealtimeClient` 无最大重试次数，且不区分 4xx 与网络故障 | 🟡 | `RealtimeClient.ts:48-55` |
| 9 | `stop()` 后再 `start()` 时旧 socket 的 `onclose` 可能触发一条多余连接 | 🟢 | `RealtimeClient.ts:19-24` |
| 10 | 跳登录页时丢弃 `search` / `hash`（只带 pathname） | 🟢 | `AuthGuard.tsx:36`、`authSession.ts:33` |
| 11 | 通知下拉 List 无 loading 态，加载中先显示"暂无消息" | 🟢 | `AppLayout.tsx:142-164` |
| 12 | 通知点击不判断 `businessType`，直接查部门变更接口 | 🟢 | `notifications/index.tsx:31-37` |
| 13 | **无 ESLint**（却留着 2 处 `eslint-disable` 注释）、无 lint 脚本、无测试框架 | 🟠 | `package.json:9-13` |
| 14 | tsconfig 只开了 `strict`，`noUnusedLocals` / `noUnusedParameters` / `noImplicitReturns` / `noUncheckedIndexedAccess` / `exactOptionalPropertyTypes` **全未开** | 🟡 | `tsconfig.app.json` |
| 15 | 子路径部署不支持（未配 `base`，但注释宣称支持） | 🟡 | `vite.config.ts:2`、`monaco.ts:10` |
| 16 | 首屏 chunk 2.39 MB（两张近 1MB 的图 + 无 manualChunks 拆分） | 🟡 | `dist/assets/` |
| 17 | `favicon.ico` 实为 996 KB 的 PNG，且与 `logo.png` 完全相同 | 🟢 | `public/favicon.ico` |

### 9.14 前端死代码与重复逻辑

**确认无引用的死代码**：

- `components/FileUpload/*` + `api/file.ts` + `types/file.ts` —— 整套文件上传前端**从未被任何页面使用**
- `registerArtifactRenderer`（`AgentArtifactView.tsx:17`）—— 全站从未调用 → **AI artifact 永远走 JSON fallback**，这个"可扩展渲染器"机制实际上是空的
- `SmartTable` 的 `searchConfig` / `SearchField` / `configuredSearch`（`:162-194`）
- `theme/index.ts`、`theme/types.ts`
- `types/table.ts` 的 `SmartTableSelection`
- `types/auth.ts` 的 `CurrentUser.superAdmin`（后端返回了但前端没用）
- `CodeViewer` 的 `monacoRef`（`:59,:68`）
- `menu/index.tsx:5` 未使用的 `MenuOutlined` 导入

**重复逻辑**：

- `loginlog/index.css` 与 `operatelog/index.css` **逐字节相同**
- 三个页面各自重复实现"部门变更 Steps 映射"（`notifications:149-163`、`security:197-215`、`approvals:193-209`）+ 重复的拒绝弹窗
- `toTree` 在 **6 处**重复实现、`blockedIds` 在 2 处重复

### 9.15 前端做得好的地方

- access token **只存 Redux 内存**，不落 localStorage
- **401 单飞续期**（`refreshPromise ??=`）+ 用裸 axios 避免递归 + `AUTH_BOOTSTRAP` 精确白名单且刻意排除 `/auth/onboarding/account/*`
- 进度条**引用计数 + 300ms 延迟显示 + 取消未显示计时器**（`progress.ts:33,53-74`）—— 避免"闪一下就消失"
- `notificationSlice` 与 `clearSession` 联动清空，解决了"串号泄露"和"读完红点不消失"两个真实问题
- `DynamicPage` 用菜单树做唯一授权依据，403/404/诊断页三级判定
- `resolveRedirect` 用 `URL` 解析 + origin 比对防开放重定向（`menu.ts:55-65`）
- 各 Dialog 用 `ref` 同步闸门防重复提交
- G6 / BaseChart / ECharts 的生命周期封装
- 主题"双投"（AntD token + CSS 变量）
- **图标白名单**避免全量打包
- 所有 `api/` 契约与后端 `@RequestParam`/`@GetMapping` **逐个核对一致**

---

## 10. 端到端用户旅程（从真实使用出发）

这一章用七个真实场景，把前面所有章节串起来。每一步都标出"谁做了什么、数据写到哪"。

### 场景一：管理员第一次登录（强制改密）

**前置**：数据库已执行 `init_schema.sql` + `init_data.sql`，然后启动后端（默认 `local` profile）。

1. 浏览器打开前端 → 根路由 `/` → `AuthGuard` 发现 Redux 里没有 token → 调 `POST /api/auth/refresh`（靠 HttpOnly Cookie，此时没有）→ 401 → 跳 `/login?redirect=%2F`。
2. 用户输入 `admin` / `Admin@123`（来自 `init_data.sql:14-15`）。
3. 前端 `POST /api/auth/login`。后端依次：限流检查（`auth:login:lock:*`）→ 查 `sys_user` + `sys_local_credential` → `BCrypt.matches` → 清失败计数 → `PasswordChangePolicy` 判定 **`password_change_required = 1`** → 写 `sys_login_log` → `SessionService.create` → **Redis 写入 `auth:session:{sid}`**（19 字段 JSON）、`auth:user:sessions:1`（sid 集合）→ 签发 JWT（15 分钟）→ `Set-Cookie: lbl_refresh=<sid>; HttpOnly; SameSite=Lax; Path=/api/auth`（因为"记住我"没勾 → **会话 Cookie，`maxAge=-1`**）。
4. 前端拿到 access token 存进 Redux（`setAccessToken`）→ 调 `GET /api/auth/me` → 后端 `JwtAuthenticationFilter` 解析 JWT → 查 Redis 会话 → 校验用户状态/`authVersion`/用户名 → **发现 `authMethod=PASSWORD` 且 `passwordChangeRequired=true` → 权限集合置为空**（`JwtAuthenticationFilter.java:63-66`）→ 返回身份、空权限、菜单树（但因为没有权限码，菜单树里只有那些"无权限码的目录"）、全站目录。
5. `AuthGuard` 写入 `setProfile` → 进入 `AppLayout`。此时 `permissions` 为空 → **所有按钮都不显示**，页面无权限码的菜单仍可访问但操作接口一律 403。
6. 前端弹出改密 Modal（`AppLayout` 里的逻辑）→ `PUT /api/system/users/me/password`。
7. 后端 `UserPasswordService.changeOwn`：校验旧密码（15 分钟窗口内错 5 次限流）→ 新旧不能相同 → 写 `sys_local_credential.password_hash`（BCrypt）、`password_change_required=0` → **`sys_user.auth_version + 1`** → **`sessions.removeAll(1)`：删除该用户全部 Redis 会话，并通过 `CLOSE_SESSION` 事件跨实例断开 WebSocket**。
8. 前端被踢回登录页（下一次请求 401 → 续期失败 → `clearSession` → `/login`）。用新密码重新登录，这次权限集合正常装载。

> **数据落点**：`sys_login_log`（2 条）、`sys_local_credential`（密码哈希 + 标志位）、`sys_user.auth_version`（+1）、Redis（会话先建后删）、`sys_operation_log`（改密动作）。

### 场景二：管理员创建用户并分配权限（你最关心的流程）

假设 `admin` 要新建一个"部门主管"，让他能管本部门及下级的用户，但不能碰菜单和密码重置。

**Step 1 — 建部门（如果还没有）**

1. 管理员进"部门管理"（菜单 `system:dept:list`）→ `GET /api/system/depts`。`DeptService` 先全表查出部门，再在 Java 里按 `actor.departments()` 过滤。
2. 点"新增部门" → `POST /api/system/depts`（要 `system:dept:add`）。后端：`DeptPaths` 计算 `ancestors`（如 `'0,1'`）、做环检测、`requireCreateDept` 校验数据范围 → 写 `sys_dept`（含 `created_by` 自动填充）。

**Step 2 — 建角色并设定数据范围**

3. 进"角色管理" → `POST /api/system/roles`（要 `system:role:add`）。
4. 请求体带 `dataScope`。这里选 **`DEPT_AND_CHILDREN`**。
5. 后端 `RoleService.create`：`access.requireScope(actor, "DEPT_AND_CHILDREN")`（非超管不得设 `ALL`、不得超出自身范围）→ 校验 `role_code` 唯一（**含已删除**口径）→ 写 `sys_role`（`builtin=0`、`data_scope='DEPT_AND_CHILDREN'`）。
   > 若选 `CUSTOM`，还要 `validateDepartments`：必须非空、必须存在、非 ALL 时必须是自身范围子集 → 写 `sys_role_dept`。

**Step 3 — 给角色勾菜单/按钮（"分配权限"的核心）**

6. 前端打开授权弹窗 → `GET /api/system/roles/grantable-menus`。后端 `RoleService.grantableMenus`：
   - 超管拿全量树；非超管**只拿 `menus.selectByUserId(自己)`**（不能授出自己没有的）；
   - 两种情况都调 `AccessPolicy.omitPlatformOnlyMenus` 剔除平台级权限，**并把"后代全被剔除的父节点"一起删掉** → 所以"菜单管理"整行都不会出现。
7. 管理员勾选"用户管理"页面 + `查询`/`新增`/`更新` 三个按钮（**不勾** `删除`、`重置密码`、`导出`）→ `PUT /api/system/roles/{id}/menu-ids`。
8. 后端 `RoleService.grantMenus` 走 §6.4 的 8 步校验，特别是：
   - `requireGrantableMenus`：确保每个 id 都在操作者自己的菜单树里；
   - `requireNotPlatformOnly`：**再拦一次平台级**（防绕过界面直接调接口）；
   - 非超管还要检查"授权内容落在自己的操作范围内"且"不能造出与自身同级的角色"（`grantedCodes.equals(actor.permissions())` 就拒绝）。
9. `sys_role_menu` 先按 `role_id` 全删、再逐条插入（`:192-194`）。
10. ⚠️ **注意**：这一步**不会**让任何在线用户的会话失效（§6.4 已纠正）。但因为是"每请求重算权限"，被影响用户**下一个请求就生效**。

**Step 4 — 建用户并挂角色**

11. 进"用户管理" → 打开新增弹窗 → `GET /api/system/users/form-options`（要 `add` 或 `update`）。
12. 填用户名/姓名/部门/角色 → `POST /api/system/users`（要 `system:user:add`）。
13. 后端 `UserService.create`：密码确认校验 → `registration_source='ADMIN'`、`auth_version=1`、`builtin=0` → 写 `sys_user` → `saveCredential` 写 `sys_local_credential`（BCrypt，`password_change_required=1`）→ `replaceRoles` 写 `sys_user_role`。
14. `@OperationLog` 切面在 `finally` 里用 **REQUIRES_NEW 独立事务**写一条 `sys_operation_log`（含操作人/目标/IP/UA/requestId/耗时）—— **即使业务事务回滚，这条审计也在**。
15. 管理员把初始密码告知该用户（或让他走"忘记密码"—— ⚠️ 项目**没有**这个功能，只能管理员重置）。

> **数据落点**：`sys_dept`、`sys_role`、`sys_role_menu`、`sys_user`、`sys_local_credential`、`sys_user_role`、`sys_operation_log`（每一步一条）。

### 场景三：新用户登录并使用（数据范围生效）

1. 新用户登录 → 会话建立 → `GET /api/auth/me` 返回**属于他的菜单树**（由 `MenuMapper.selectByUserId` 递归 CTE 算出）。
2. 前端 `AppLayout` 只用这棵树构建侧边栏；`MenuHomeRedirect` 跳到**他的第一个可见页面**（不是写死的 `/home`）。
3. 他打开"用户管理" → `GET /api/system/users?pageNum=1&pageSize=10&keyword=...`。
4. 后端：`@PreAuthorize("hasAuthority('system:user:list')")` 通过（他有这个权限码）→ `UserService.page` → **`access.applyUserScope` 把查询限制在他的数据范围内**：
   ```sql
   AND (dept_id IN (<他的部门 + 全部子孙部门>) OR id = <他自己>)
   ```
   → 他**看不到**其它部门的用户。
5. 列表返回后，后端还逐行算了"这一行我能不能改/能不能删"（`toListViews`），用 `system:user:update`/`delete` 对应的操作范围判断 → 前端据此显示/隐藏按钮。**所以同一个列表里，不同行的按钮可能是不同的**。
6. 他尝试直接调 `PUT /api/system/users/{其它部门的用户id}` → 即使他持有 `system:user:update`，`UserService.update` 里的 `access.requireManageUser` 会拒绝（数据范围外 / 同级权限 / 内置账号 / 超管目标）→ **403**。
7. 他尝试改自己的角色为"超级管理员" → `requireAssignableRoles` 拒绝（不能分配超出自己范围的角色，且 `super_admin` 是 `builtin` 角色，`canAssignRole` 直接 false）。
8. 他尝试把某个角色的数据范围改成 `ALL` → `requireScope` 拒绝。

> 这一整套"越权尝试都会被服务层拦住"正是 §5.3 第二层存在的意义。

### 场景四：退出登录

1. 点顶栏"退出登录" → `POST /api/auth/logout`。
2. 后端 `AuthService.logout`：查会话拿用户名 → **`sessions.remove(sid)`** —— 删 `auth:session:{sid}`、从 `auth:user:sessions:{userId}` 移除、**并通过 Redis 广播 `CLOSE_SESSION` 事件**让所有实例断开该 sid 的 WebSocket → 写一条成功 `sys_login_log`。
3. 响应带 `Set-Cookie: lbl_refresh=; Max-Age=0; HttpOnly; SameSite=Lax; Path=/api/auth`（**属性与签发时完全一致**，否则浏览器不会覆盖）。
4. 前端 `dispatch(clearSession())` —— 这是"会话结束"的唯一收口，它同时：
   - 清空 `accessToken` / `user` / `permissions` / `menus` / `routes` / `profileReady`
   - **清空通知状态**（防止换账号登录时串号泄露，`notificationSlice.ts:107`）
   - `navigate('/login', { replace: true })`（用 replace 避免把受保护页留在历史栈里被"后退"回来）

### 场景五：发送消息（AI 对话）

**这是当前最能体现"框架完整、业务为空"的场景。**

1. 用户点右下角 AI 悬浮按钮（只有持有 `agent:chat:use` 才渲染，`AppLayout.tsx:309`）。
2. 输入一句话点发送 → `useAgentChat.send()` 用 `authenticatedFetch` 发 `POST /api/agent/chat`（要带 Bearer，所以不能用 `EventSource`）。
3. 后端 `AgentChatController.chat`：校验 `agent:chat:use` → 设 `X-Accel-Buffering: no`（禁止 nginx 缓冲 SSE）→ 生成 `runId` → 建 `SseEmitter(120s)` → **快照当前用户的 `Actor`（用户 id + 用户名 + 超管标记 + 权限码集合）** → 建 `CancellationToken` 和 110 秒 deadline → **提交到专用线程池**（`core=2, max=8, queue=100`）。
4. `AgentRunner.run`：
   - `tools.visibleTo(actor)` —— **当前注册表为空**，所以返回空列表；
   - 过滤 `ApprovalPolicy.REQUIRED`（也是空）；
   - 组装 `system 指令 + 历史 + 本轮消息`；
   - 循环调 `ModelGateway.complete()` → `POST {LLM_BASE_URL}/v1/chat/completions`（**非流式**，60 秒超时）。
5. 模型返回纯文本 → `AgentEvent.message` 被 `emitter.send(SseEmitter.event().name("agent").data(event))` 写成 SSE 帧 → 前端按 `data:` 行解析、`text += event.text` → 打字机效果。
6. 正常结束发 `done` 事件 → `completeQuietly()`。
7. ⚠️ **边界**：因为没有任何 `AgentTool` 实现，模型**拿不到任何工具**，所以它只能回答通用问题，**不能查询或修改系统数据**。你问"这个月新增了几个用户"它只能瞎猜或拒答。

**如果要让它真的能查数据**，按 §8.11 写第一个 `AgentTool`，然后权限码会自动生效（工具声明所需权限 → `visibleTo` 过滤 → 执行时 `ToolExecutionService` 再校验一次）。

### 场景六：收到通知（实时推送）

1. 某个业务动作（如部门变更审批提交）在 **`@Transactional` 方法内**调 `NotificationService.create(...)`。
2. 先 `insert` 写 `sys_notification`；然后用 `TransactionSynchronizationManager.registerSynchronization(...).afterCommit()` 注册一个**提交后回调**。
3. 事务提交 → 回调执行 → `RealtimeGateway.send` → 发到 Redis 频道 `kariya-admin:realtime:events`。
4. **所有实例**（包括自己）的 `RealtimeEventSubscriber` 收到 → `sendLocal` → 只发给本实例上该用户的连接，JSON 形如 `{"type":"notification.created","data":{...}}`。
5. 前端 `AppLayout` 的 `onEvent` 收到 → **重新拉 REST**（`GET /account/notifications` + `/unread-count`）→ 顶栏红点与消息页同时更新（共用 `notificationSlice`）。
6. 如果这时用户断线了 → 事件丢失（Pub/Sub 不重放）→ 但他下次让标签页重新可见（`visibilitychange`）时会补拉。
   ⚠️ **缺口**：断线**重连成功**本身**不会**触发补拉（§7.7）。

### 场景七：管理员强制某人下线

1. 管理员进"用户管理" → 点某用户的"设备管理" → `GET /api/system/users/{id}/sessions`。
2. 后端列出该用户的 Redis 会话，但**对外只暴露 `SHA-256(sid)` 前 22 字符的引用**（`SessionService.java:212-219`）—— 即使这个列表泄露也无法直接冒用会话。
3. 点"踢出" → `DELETE /api/system/users/{id}/sessions/{reference}`。
4. 后端 `UserSessionAdministrationService` 先**再次校验目标用户的可管理性**（防越权）→ `SessionService.remove(sid)` → 删 Redis 会话 + 广播 `CLOSE_SESSION`。
5. 该 sid 对应的 WebSocket 被 `close(POLICY_VIOLATION)` 断开（**同用户其它设备不受影响**，因为按 sid 精确匹配）。
6. 那个用户下一个请求：JWT 依然有效（可能还没过期），但 `JwtAuthenticationFilter` 查不到 Redis 会话 → 不设置 Authentication → 401 → 前端尝试续期 → 续期也查不到会话 → 401 → `clearSession` → 跳登录页。

---

## 11. 数据是怎么保存的

系统用了**三个**存储，职责划分很清晰：

| 存储 | 存什么 | 是否可丢 | 是否持久 |
|---|---|---|---|
| **MySQL** | 所有业务数据（用户、角色、菜单、部门、审批、通知、日志） | ❌ 丢了就没了 | ✅ |
| **Redis** | 会话、验证码、限流计数、OAuth 事务、WebSocket ticket、跨实例事件 | ✅ 大部分可丢（代价是所有人重新登录） | ❌ 建议开 AOF/RDB |
| **本地磁盘** | 上传暂存文件（`./data/uploads/staging`） | ✅ 可丢 | ✅ 但多实例有问题 |

> ⚠️ **Redis 在这个项目里不是"缓存"，而是"会话存储"**。它挂了 → 所有人无法认证（`JwtAuthenticationFilter` 查不到会话 → 全部 401）、验证码不能用、限流失效、WebSocket 无法建立。**没有降级路径**。健康检查里 readiness 组包含 `redis` 就是为了这个。

### 11.1 MySQL：谁写、谁读

| 表 | 写入者 | 读取者 |
|---|---|---|
| `sys_user` | 注册、开户、`UserService.create/update`、审批完成时改 `dept_id` | 登录、每次请求（`authVersion`/`status`）、用户列表 |
| `sys_local_credential` | 注册、开户、创建用户、改密、重置密码 | 登录 |
| `sys_external_identity` | 第三方登录绑定 | 第三方登录 |
| `sys_role` / `sys_role_menu` / `sys_role_dept` | 角色 CRUD、`grantMenus` | **每个请求**（算权限码）、菜单树 |
| `sys_menu` | 菜单 CRUD、`SystemPermissionInitializer` 启动补种 | **每个请求**（`selectByUserId` 递归 CTE） |
| `sys_dept` | 部门 CRUD | **每个请求**（`AccessPolicy.actor()` 全表读一次） |
| `sys_user_role` | 用户创建/更新 | 每个请求（算权限） |
| `sys_department_change_request` / `_step` | 审批提交/决策 | 待办中心、详情 |
| `sys_notification` | `NotificationService.create` | 通知列表、未读数 |
| `sys_login_log` | 登录成功/失败、登出 | 登录日志页 |
| `sys_operation_log` | `@OperationLog` 切面 | 操作日志页 |

**一个值得注意的性能含义**：`auth:session` 只存会话，**权限码是每个请求都重查数据库的**（`JwtAuthenticationFilter.java:64`）。好处是权限变更立刻生效、无需踢会话；代价是每个请求至少 2 次数据库查询（菜单递归 CTE + 角色权限），而 `AccessPolicy.actor()` 又会额外查用户、角色、**全表部门**、角色权限。

### 11.2 Redis：完整 key 表

| Key 模式 | 内容 | TTL | 作用 |
|---|---|---|---|
| `auth:session:{sid}` | `LoginSession` JSON（19 字段） | 空闲 TTL（2h / 7d，随每次 touch 延长） | **会话主体** |
| `auth:session:absolute:{sid}` | `"1"` | 绝对上限（14d / 2h） | 绝对超时上限判断 |
| `auth:user:sessions:{userId}` | sid 集合 | 随会话延长 | 列出设备、踢全部设备 |
| `auth:captcha:{id}` | 验证码明文答案 | 2 分钟 | 注册验证码 |
| `auth:login:fail:account-source:{loginKey}:{srcHash}` | 失败计数 | 15 分钟 | 账号+来源限流（上限 5） |
| `auth:login:lock:account-source:{loginKey}:{srcHash}` | 锁定标记 | 15 分钟 | 同上 |
| `auth:login:fail:source:{srcHash}` / `auth:login:lock:source:{srcHash}` | 来源级计数/锁 | 15 分钟 | 来源限流（上限 20） |
| `auth:quota:{bucket}:{srcHash}` | 来源配额计数 | 各自窗口 | 验证码出题 60/min、外部登录 30/min |
| `auth:password-change:fail:{userId}` | 改密原密码错误计数 | 15 分钟 | 上限 5 |
| `auth:external:transaction:{state}` | `LoginTransaction` JSON（PKCE/returnTo） | 10 分钟 | OAuth/OIDC 事务 |
| `auth:uias:transaction:{state}` | 同上 | 10 分钟 | UIAS 事务 |
| `realtime:ticket:{ticket}` | `{userId}\|{sid}` | **60 秒** | WebSocket 一次性握手票 |
| `kariya-admin:realtime:events` | *（Pub/Sub 频道，非 key）* | — | 跨实例事件广播 |

**`srcHash` = `base64url(SHA-256(客户端IP))`** —— **不存明文 IP**。

**安全细节**：`sid` 是 48 字节 `SecureRandom`；对外只暴露 `SHA-256(sid)` 前 22 字符的引用。

### 11.3 会话数据的完整生命周期

```text
登录成功
  ├─ SET auth:session:{sid}          = LoginSession JSON   EX=空闲TTL
  ├─ SET auth:session:absolute:{sid} = "1"                 EX=绝对上限   （仅记住我/开户态）
  ├─ SADD auth:user:sessions:{userId} sid
  └─ 响应 Set-Cookie: lbl_refresh={sid}; HttpOnly; SameSite=Lax; Path=/api/auth
                                                        （非记住我 → maxAge=-1，会话 Cookie）

每次请求
  ├─ JwtAuthenticationFilter 读 Authorization → parse JWT → 取 sid
  ├─ GET auth:session:{sid}    ★ 查不到 → 匿名（受保护接口 401）
  └─ 校验：user.status=1 && user.authVersion==session.authVersion
            && session.username==JWT.sub && session.authVersion==JWT.authVersion

活跃续期（UserActivityManager 节流触发 POST /auth/touch）
  └─ EXPIRE auth:session:{sid} = min(空闲TTL, 绝对剩余时间)   ← 绝不突破绝对上限

静默续期（401 → POST /auth/refresh）
  └─ 重新校验 → 重签 JWT（15 分钟）→ 前端换掉 Redux 里的 token

退出 / 被踢 / 改密 / 停用 / 删用户
  ├─ DEL auth:session:{sid}
  ├─ DEL auth:session:absolute:{sid}
  ├─ SREM auth:user:sessions:{userId} sid
  ├─ 广播 CLOSE_SESSION → 断开该 sid 的 WebSocket
  └─ Set-Cookie: lbl_refresh=; Max-Age=0 （属性与签发时一致）
```

### 11.4 事务边界与一致性

| 场景 | 事务处理 |
|---|---|
| 业务写操作 | `@Transactional`（Service 层） |
| **审计日志** | `REQUIRES_NEW` **独立事务** → 业务回滚后审计仍在（已核实机制成立） |
| **登录日志** | 同上（`LoginLogService.record` 也是 REQUIRES_NEW） |
| **通知推送** | 先 insert，**事务提交后**才发实时事件（`TransactionSynchronization.afterCommit`） |
| 审批并发 | `SELECT ... FOR UPDATE` 行锁（主单 + 用户） |
| 缓存与 DB 一致性 | 无缓存层 → 不存在双写不一致问题；权限每请求重算 → 不存在脏权限 |

⚠️ **代价**：`REQUIRES_NEW` 会额外占一条数据库连接。`AuthService.login` 上挂了 `@Transactional` 却没有任何写操作，却因为审计日志是 REQUIRES_NEW 而**单次登录占 2 条连接** —— 而连接池上限是 10。这是内网高并发登录时需要留意的点。

⚠️ **没有分布式事务/最终一致性机制**，但也不需要：所有权威数据在 MySQL，Redis 只放可重建的会话类数据（代价是重新登录）。

### 11.5 备份与迁移要点

**必须备份**：MySQL（业务数据）。**建议备份**：Redis（避免所有人重新登录；但它不是权威数据）。

**数据库迁移的现状（重要）**：

- ❌ **没有 Flyway / Liquibase**，也没有 `spring.sql.init.*` 配置 → **两个 SQL 文件不会被应用自动执行**，纯手工。
- `init_schema.sql:2` 自己声明"仅面向空数据库；已有数据库请使用专用迁移工具，不要重复执行本文件"。
- ⚠️ `README.md:47-49` 提到的 `add_role_dept.sql`、`migrate_operation_log_audit.sql` **在仓库和 git 历史中都不存在**。
- 唯一"半自动"的是启动期补种：`SystemPermissionInitializer`（`@Order(100)` 的 `ApplicationRunner`）每次启动幂等补建 **2 个页面**（登录日志、操作日志）与 **18 个按钮权限**，并授予 super_admin。
  ⚠️ **它的清单里没有任何 `system:user:*`** → 老库若缺用户管理权限点，启动**不会**补建。
- `sys_role_dept` 表在 schema 里存在，但注释要求老库"手动执行"（`:131-132`）。

**迁移顺序建议**：`init_schema.sql` → `init_data.sql` → 手工补 `sys_role_dept` → 启动应用让 `SystemPermissionInitializer` 补页面/按钮 → **人工核对 `system:user:*` 六个权限点是否存在**。

---

## 12. 内网移植评估（针对你的核心诉求）

### 12.1 结论先行

> **当前状态：直接搬到内网跑不起来。**
> 不是架构问题，而是**配置问题** —— 而且是一个"两头都不通"的尴尬局面：
>
> | 路径 | 结果 |
> |---|---|
> | 用默认的 `local` profile | ❌ 连**公网** MySQL `124.222.152.55:3308`、公网 Redis `:6381`、公网 LLM `api.deepseek.com`。内网里三条全断，**连登录都做不了**（会话在 Redis） |
> | 用 `prod` profile | ❌ **启动就失败**：`application-prod.yml` 只有 18 行，`lbl.security.jwt-secret` 等 7 个必填项无来源，而 `SecurityProperties` 是无 `@DefaultValue` 的 record，`JwtService` 构造器直接解引用 `jwtSecret()` |
> | 用环境变量补 | ⚠️ **`JWT_SECRET` 这个变量在全仓库零引用** —— 没有任何 `application-*.yml` 写 `jwt-secret: ${JWT_SECRET}`，所以设了也没用 |
>
> **唯一可行的路径**：手动编辑 `application-local.yml`（它被 gitignore，是唯一含完整 `lbl.*` 配置的文件），把里面的地址改成内网地址。这能work，但它意味着**部署配置不存在于版本控制中**。

移植的工作量本身不大（大约半天到一天），但**必须先补配置**。好消息是：**前端运行时零外网依赖**，后端也**完全离线可编译**（我已实测）。

### 12.2 必须先提供的中间件（含版本硬要求）

| 组件 | 版本要求 | 为什么 | 验证方式 |
|---|---|---|---|
| **MySQL** | **8.0+** | ① `MenuMapper.selectByUserId` 用 `WITH RECURSIVE`（5.7 不支持）；② `LogRetentionJob` 用 `DELETE ... ORDER BY ... LIMIT`（8.0+） | `SELECT VERSION();` |
| **Redis** | **6.2+** | `RealtimeTicketService` 用 `GETDEL`（`getAndDelete`），6.2 才引入。5.x/6.0 会导致**WebSocket 握手直接失败** | `redis-cli INFO server \| grep redis_version` |
| JDK | 17 | `pom.xml:15` | `java -version` |
| Maven | 3.6+ | 无 wrapper，必须预装 | `mvn -v` |
| Node.js | **`>=20.19 <25`** | `package.json` 的 `engines` 字段会阻断不符的版本 | `node -v` |

**Redis 的两个额外要求**：

1. **必须允许 Pub/Sub**（跨实例实时广播走 `kariya-admin:realtime:events`）。
2. ⚠️ **`maxmemory-policy` 绝对不能用 `allkeys-lru` / `allkeys-random` 这类"主动淘汰任意 key"的策略** —— `auth:session:*` 被静默淘汰等于**用户随机掉线**，而且极难排查（因为 Redis 本身看起来工作正常）。用 `noeviction`。
3. Redis **不是缓存而是会话存储**，没有降级路径 → 建议开 AOF 持久化。

### 12.3 配置必须补/改的清单（逐项）

现状：**所有 `lbl.*` 配置只存在于 `application-local.yml`**（`application.yml` 已无 `lbl` 块，`application-prod.yml` / `application-dev.yml` 都没有）。

| 配置项 | 当前值（local） | 内网必须改成 | 位置 |
|---|---|---|---|
| `spring.datasource.url` | `jdbc:mysql://124.222.152.55:3308/kariya_admin?...` | 内网 MySQL 地址 | `application-local.yml:3` |
| `spring.datasource.username` | `root` | ⚠️ 别用 root，建专用账号 | `:4` |
| `spring.datasource.password` | `ENC(...)` | 明文或重新加密（见 §12.4） | `:5` |
| `spring.data.redis.host` / `port` | `124.222.152.55` / `6381` | 内网 Redis | `:25-26` |
| `spring.data.redis.password` | `ENC(...)` | 内网 Redis 密码。⚠️ **`application-prod.yml:8-9` 只有 host/port，没有 password 项** | `:27` |
| `lbl.security.jwt-secret` | `lbl-shit-development-secret-must-be-at-least-32-characters` | **必须换**（≥32 字节随机） | `:125` |
| `lbl.security.secure-cookie` | `${COOKIE_SECURE:false}` | http 内网保持 `false`；https 必须 `true` | `:133` |
| `lbl.security.cors-allowed-origins` | `http://localhost:5173,http://127.0.0.1:5173` | 同源部署 → **留空** | `:136` |
| `lbl.security.trusted-proxies` | 空 | ⚠️ **反代后必填**，否则见 §12.5 | `:140` |
| `lbl.external-auth.backend-base-url` | `http://localhost:8080` | 内网后端地址，否则 OAuth 回调地址是 localhost | `:44` |
| `lbl.external-auth.frontend-base-url` | `http://localhost:5173` | 内网前端地址 | `:45` |
| `lbl.external-auth.proxy-host` / `port` | `127.0.0.1` / `7890` | ⚠️ **必须清空**！否则所有 OAuth 出站请求会先撞一个不存在的本地代理并超时 | `:46-47` |
| `lbl.external-auth.providers.github.enabled` | **`true`** | 内网应设 `false` | `:50` |
| `lbl.external-auth.providers.google.enabled` | **`true`** | 内网应设 `false` | `:64` |
| `lbl.agent.base-url` | `https://api.deepseek.com` | **内网 LLM 网关地址**（或关闭 AI） | `:157` |
| `lbl.agent.api-key` | `ENC(...)` | 内网网关密钥 | `:158` |
| `lbl.agent.model` | `deepseek-flash` | 内网模型名 | `:159` |
| `lbl.file-upload.staging-directory` | `./data/uploads/staging` | 建议改绝对路径（如 `/data/kariya/uploads/staging`） | `:118` |
| `logging.level.org.kariya` | `DEBUG` | ⚠️ **这是无效配置**（包名是 `org.lbl`，不是 `org.kariya`），可删 | `:34` |
| Jasypt `password` | `kariya`（明文，**已提交入库**） | 见 §12.4 | `application.yml:15` |
| `spring.profiles.active` | `local` | 启动时用 `--spring.profiles.active=prod`，但**prod 目前缺 7 个键**（见 §12.1） | `application.yml:5` |

**如果要用 `prod` profile，必须先在 `application-prod.yml` 补上这 7 个键**（它们目前只存在于 `application-local.yml`）：

```yaml
lbl:
  security:
    jwt-secret: ${JWT_SECRET}                  # ← 当前全仓库零引用，必须新增这一行
    access-token-minutes: ${ACCESS_TOKEN_MINUTES:15}
    idle-hours: ${IDLE_HOURS:2}
    remembered-idle-days: ${REMEMBERED_IDLE_DAYS:7}
    remembered-absolute-days: ${REMEMBERED_ABSOLUTE_DAYS:14}
    password-max-age-days: ${PASSWORD_MAX_AGE_DAYS:0}
  file-upload:            # 有 @DefaultValue，可省，但显式更清楚
    staging-directory: ${FILE_STAGING_DIRECTORY:./data/uploads/staging}
  log:
    retention:            # 有 @DefaultValue，可省
      enabled: true
  agent:                  # 不加则 AI 助手报"尚未配置模型网关"
    base-url: ${LLM_BASE_URL}
    api-key: ${LLM_API_KEY}
    model: ${LLM_MODEL}
```

> 更省事的替代方案：**继续用 `local` profile，只把 `application-local.yml` 改成内网地址**。这样不用动 prod，但代价是部署配置不在版本控制里（新同事 clone 后无法启动）。

### 12.4 必须处理的安全问题（按优先级）

| # | 问题 | 处理方式 |
|---|---|---|
| 1 | **Jasypt 主口令 `kariya` 明文在 `application.yml:15`（已提交入库）** | 三选一：① **内网直接弃用 Jasypt**，用 `${DB_PASSWORD}` 环境变量（少一个依赖，推荐）；② 口令走 Jasypt 原生环境变量 `${JASYPT_ENCRYPTOR_PASSWORD}`；③ 换口令 + 重新加密所有 `ENC(...)` |
| 2 | **JWT 密钥是开发占位值** | `openssl rand -base64 48` 生成，写进服务器的 EnvironmentFile。**≥32 字节是硬要求**（`Keys.hmacShaKeyFor` 会拒绝短密钥，启动即暴露，这是好事） |
| 3 | **公网 MySQL/Redis 凭据在磁盘上** | 换掉。顺便注意 local 的 JDBC 参数带 `useSSL=false&allowPublicKeyRetrieval=true`，内网若走明文网段要评估 |
| 4 | **默认开启的 GitHub / Google 登录**（`enabled: true`） | 内网设 `false`。⚠️ 同时**务必清掉 `EXTERNAL_AUTH_PROXY_HOST=127.0.0.1` / `PORT=7890`** |
| 5 | **`banner.txt` 会在每次启动时打印 ASCII "SHIT" 图案 + 私人语录** | 🔴 **部署前一定要处理**。文件 `backend/src/main/resources/banner.txt`（973 B）第 20-30 行是 `${AnsiColor.BRIGHT_YELLOW}` + 由 `____ _ _ ___ _____` 拼成的 **"SHIT"** + `${AnsiColor.BRIGHT_RED}` + "你把核心系统交给劳务派遣开发 / 说明你也没把它当核心"。清空该文件即可回退到 Spring Boot 默认 banner；第 1-19 行的 ASCII 图案也一并删掉 |
| 6 | **前端 12 处产品名 `LBL SHIT` + 14 处私人文案** | 影响观感。产品名在 `index.html:7`（`<title>`）、`layouts/SiderBrand.tsx:9`、`pages/login/LoginBrand.tsx:9`、`pages/login/index.tsx:74`、`pages/register/index.tsx:117`、`LoadingScreen.tsx:11` 等；文案如 `全栈摸鱼基地`、`CRUD · BUG · CV`、`Kariya的个人开发工作台`、`少女祈祷中...`、`只要不报错，就是好系统`。建议新建 `src/config/brand.ts` 集中管理 |
| 7 | **三方登录的"登录 CSRF"仍未修** | `state` 存 Redis、一次性消费、与 provider 绑定，但**没有和"发起登录的那个浏览器"绑定**（`/start` 不种 Cookie）。攻击者可用自己的账号发起登录、截住 `code`+`state`，把 callback URL 给受害者 → 受害者被静默登录进**攻击者的账号**。内网不启用三方登录就不存在此问题 |
| 8 | **`/api/auth/external/*/callback` 无限流** | 任何失败都会往 `sys_login_log` 写 FAILURE，匿名脚本可刷大日志表。修法：加一句 `attempts.requireSourceQuota("external-callback", N, WINDOW)` |
| 9 | **`application-local.yml` 被 gitignore，导致新环境无法启动** | 建议改为提供一个 `application-local.yml.example` 模板入库（不含真实凭据） |

### 12.5 部署配置（反向代理是必须的）

**推荐拓扑（同源部署，最省事）**：

```text
内网用户 ──▶ Nginx (80/443) ──┬── /       → 前端静态文件（frontend/dist）
                              ├── /api/   → 后端 http://127.0.0.1:8080
                              └── /ws/    → 后端 WebSocket（需要 Upgrade 头）
```

**为什么强烈建议同源**：

1. 刷新 Cookie 是 `SameSite=Lax`（`RefreshCookieFactory.java:37`，**写死，无配置项**）+ `Path=/api/auth`。
2. 前端 `.env.production` 已经是 `VITE_API_BASE_URL=/api`（相对路径）—— **天生为同源部署准备**。
3. 同源就不需要 CORS，`cors-allowed-origins` 留空即可（**留空意为"不放行任何跨域"，这正是同源部署的正确默认值**）。

⚠️ **跨域部署会有一个隐蔽的坑**：如果你的前端和后端不同源，那么跨站 fetch **不会携带 `SameSite=Lax` 的 Cookie** → `/api/auth/refresh` 和 `/api/auth/me` 必然 401 → **静默续期完全失效**，而业务接口用 Bearer 却正常工作。表现是"页面刷新后就掉登录"。所以：**要么同源，要么把 SameSite 改成 None 并上 HTTPS —— 但后者会让 `/refresh`、`/logout` 变成可 CSRF 的端点。**

**Nginx 参考配置**（仓库里**没有任何** nginx/Dockerfile 配置，需要你自己写）：

```nginx
server {
    listen 80;
    server_name admin.内网域名;

    root /opt/kariya_admin/frontend/dist;
    index index.html;

    # SPA：所有非静态文件请求都交给 index.html
    location / {
        try_files $uri $uri/ /index.html;
    }

    # 后端 API
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        client_max_body_size 12m;      # 与 application.yml 的 max-request-size: 11MB 匹配
    }

    # AI 的 SSE：必须关闭响应缓冲，否则"流式"会变成"憋到最后一次性返回"
    # （后端已发 X-Accel-Buffering: no，这里再显式声明一次更稳）
    location /api/agent/chat {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 300s;       # > sse-timeout-millis(120s)
        proxy_set_header Host $host;
    }

    # WebSocket（实时通知）。缺 Upgrade/Connection 头会连不上并导致前端疯狂重连
    location /ws/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade    $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host       $host;
        proxy_read_timeout 3600s;      # 后端有 25 秒心跳，这里给足余量
    }

    # 静态资源长缓存（文件名带 hash）
    location /assets/ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }
}
```

**⚠️ `TRUSTED_PROXIES` 是最容易漏掉、后果最严重的一项**：

```bash
TRUSTED_PROXIES=127.0.0.1,::1        # Nginx 与后端同机
# 或 Nginx 在另一台机器：
TRUSTED_PROXIES=10.0.0.0/8,172.16.0.0/12
```

漏掉的后果（这是 `TrustedProxyResolver` 的设计目的）：
- 审计日志里所有 IP 都变成网关地址 → **审计失去意义**；
- **所有用户共享同一个限流 key → 一个人密码错 20 次，把整个内网的人锁 15 分钟**。

**systemd 服务单元**（别用 `nohup`）：

```ini
[Unit]
Description=Kariya Admin Backend
After=network.target mysql.service redis.service

[Service]
User=kariya
WorkingDirectory=/opt/kariya_admin
EnvironmentFile=/etc/kariya-admin/env
ExecStart=/usr/bin/java -Xms512m -Xmx1024m -jar /opt/kariya_admin/backend.jar --spring.profiles.active=prod
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

> ⚠️ **`--spring.profiles.active=prod` 必须显式传**（或写进 env），因为 `application.yml:5` 已写死 `active: local`。

**容器镜像必须装字体**：`CaptchaService` 启动时会做字体探测，失败就**拒绝启动**（这是好设计，避免"验证码永远是空白图"这种最难排查的故障）。但注意：**报错文案已经不再提供"设 `CAPTCHA_ENABLED=false` 关闭验证码"这条出路** —— 那个属性/环境变量**根本不存在**（`SecurityProperties` 里没有该字段，全仓库只有一句失效注释）。所以只能装字体：

```bash
# Debian/Ubuntu
apt-get install -y fontconfig fonts-dejavu-core
# Alpine
apk add fontconfig ttf-dejavu
```

### 12.6 离线构建（已实测可行 ✅）

**后端：完全离线可编译**（我实测 `mvn -o -B -DskipTests compile` → BUILD SUCCESS，182 个文件，10.7 秒）。

| 项 | 状态 |
|---|---|
| Maven Wrapper | ❌ **不存在**（无 `mvnw` / `.mvn`）→ 内网需预装 Maven |
| Maven 本地仓库 | 本机在 `D:\Repository\Maven`（由 `conf/settings.xml:55` 指定，**不是** `~/.m2/repository`） |
| 内网镜像 | 需要把 `maven.aliyun.com` 换成内网 Nexus/私服，或把 757 个 jar 整体拷进去 |
| 已知坑 | `pom.xml:85-89` **硬编码了 `aspectjweaver 1.9.23`**，注释说"本地仓库只有 1.9.22，能联网后删掉 version 交给 BOM"——该注释已过期（本地仓库现在 1.9.22 和 1.9.23 都有）。内网需自备 **1.9.23** |
| 非必需插件 | `mvn -o dependency:tree` 会失败（缺 `maven-dependency-plugin:3.8.1`），但**打包必需的插件版本都在**（compiler 3.13.0、surefire 3.5.2、jar 3.4.2、resources 3.3.1、spring-boot 3.4.4） |

**前端：类型检查已通过，但生产构建依赖本地 npm 生态**。

| 项 | 状态 |
|---|---|
| `tsc --noEmit` | ✅ 我实测 exit 0，零错误（`strict: true`，0 处 `any`） |
| `vite build` | ⚠️ 本会话因沙箱限制无法运行（esbuild 需要 spawn 常驻进程，被命名管道限制挡住）。**这是会话限制，不是项目问题** |
| `dist` 现状 | 存在，136 文件 / 20.43 MB，构建于今天，但**是改动前的版本** → 发布前必须重新 build |
| 运行时外网依赖 | ✅ **零**。无 CDN、无 `@font-face`、无 Google Fonts；Monaco 自托管在同源 `public/monaco/vs`（13.35 MB），离线可用 |
| 内网需自备 | `npm` registry 镜像 / 私服，或把 `node_modules` 整体拷进去（`package-lock.json` 已入库） |
| Node 版本 | 必须 `>=20.19 <25`（`engines` 字段） |

⚠️ **子路径部署不支持**：`vite.config.ts` 只有 3 行（无 `base`），`BASE_URL` 恒为 `/`，路由与资源全是绝对路径 → 部署在 `https://host/admin/` 下会 404。要子路径部署必须显式配 `base`（尽管 `monaco.ts:10` 的注释宣称支持）。

### 12.7 数据库与迁移

**建库方式：纯手工 SQL**（无 Flyway/Liquibase，无 `spring.sql.init`，两个 SQL 不会被自动执行）。

**路 A：全新建库（推荐）**

```bash
mysql -u root -p -e "CREATE DATABASE kariya_admin DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;"
mysql -u root -p kariya_admin < backend/src/main/resources/db/init_schema.sql
mysql -u root -p kariya_admin < backend/src/main/resources/db/init_data.sql
```

然后启动后端 → `SystemPermissionInitializer` 会自动补建缺失的内置页面/按钮并授予 `super_admin` → 用 `admin` / `Admin@123` 登录（**首次登录强制改密**）。

⚠️ **启动补种有一个已知缺口**：`SystemPermissionInitializer` 的清单里**没有任何 `system:user:*`**（那 6 个权限点只在 `init_data.sql` 里）。所以老库升级时**不会**自动补建用户管理权限 → 必须人工核对这 6 个权限点是否存在。

**路 B：搬迁现有公网库**

```bash
mysqldump -h 124.222.152.55 -P 3308 -u root -p --single-transaction \
  --default-character-set=utf8mb4 kariya_admin > kariya_admin_dump.sql
# 内网导入
mysql -u kariya -p kariya_admin < kariya_admin_dump.sql
```

搬完必须做两件事：
1. **清掉测试数据**（`sys_user` / `sys_local_credential` / `sys_external_identity` / 各日志表，除初始 `admin` 外都该删）；
2. **强制所有人重新登录**：`UPDATE sys_user SET auth_version = auth_version + 1;`（Redis 里的会话不会被 mysqldump 带走，但加上更稳）。

**⚠️ 一个必须知道的坏消息：旧的迁移脚本已经找不回来了。**

| 脚本 | 是否可从 git 恢复 |
|---|---|
| `db/upgrade__schema.sql` | ✅ 可以（`git show 9eb293d^:backend/src/main/resources/db/upgrade__schema.sql` 之类） |
| `db/migration/V1__schema.sql` ~ `V4__reset_builtin_admin_password.sql` | ✅ 可以（最早的 Flyway 风格目录，在 `64d5678` 一带） |
| `db/init__data.sql`、`db/init__schema.sql`（旧的双下划线命名） | ✅ 可以 |
| `upgrade__audit_fields.sql` | ❌ **从未进入 git，永久丢失** |
| `upgrade__basic_role.sql` | ❌ **从未进入 git，永久丢失** |
| `upgrade__department_workflow.sql` | ❌ **从未进入 git，永久丢失** |
| `upgrade__password_policy.sql` | ❌ **从未进入 git，永久丢失** |

同时 `backend/target/` 里**只有 `classes/` 和 `generated-sources/`，没有任何 `.jar`** → 之前文档里"从 jar 里捞回这 5 个脚本"的办法**已经失效**。那 4 个升级脚本的内容需要**重新编写**（不过 `init_schema.sql` 里已经包含了它们的最终效果，所以只有"在已有老库上升级"的场景才需要）。

**强烈建议现在就补一个迁移方案**：目录约定 `db/migration-manual/`，按日期命名（如 `20260101_add_role_dept.sql`），每个脚本头部写清"在什么状态的库上执行"和"执行后如何验证"。若要引入工具，**Flyway 改动量最小**（加依赖 + `spring.flyway.enabled=true` + 脚本放 `db/migration/V1__xxx.sql`），但对**已有且无 flyway 历史表的库**需要 `baseline-on-migrate=true`。

### 12.8 移植验收清单（部署后逐项过）

- [ ] 后端能启动，日志里**没有** ASCII "SHIT" banner
- [ ] `/actuator/health` 返回 UP；`/actuator/health/readiness` 包含 db + redis 都 UP
- [ ] 浏览器标签页标题是公司名（不是 `LBL SHIT`）
- [ ] `admin` 登录成功 → **立刻被强制改密**（这是设计行为）
- [ ] 改完密码 → 侧边栏菜单完整，能进"用户管理"
- [ ] 建新角色 + 新用户 + 授权 → 用新用户登录，**只看到授权的菜单**
- [ ] 新用户手敲未授权地址 → 403；手敲不存在的地址 → 404
- [ ] 新用户什么都不点，超过 15 分钟后继续操作 → **不跳登录页**（静默续期生效）
- [ ] 密码连错 5 次 → 429 提示；`sys_login_log` 里有 LOCKED 记录
- [ ] 从另一台机器登录同一账号 → "登录与安全"能看到两个会话；踢掉一个 → 那台机器的下一个操作跳登录页
- [ ] 经 Nginx 访问时，`sys_login_log.login_ip` 是**真实客户端 IP**（验证 `TRUSTED_PROXIES` 配对成功）
- [ ] 站内通知能在**不刷新页面**的情况下弹出来（验证 `/ws/` 代理配置）
- [ ] `sys_operation_log` 里有"新增用户""修改角色"等记录（验证 AOP 生效）
- [ ] 注册页能显示图形验证码（**永远是空白图 → 服务器缺字体**）
- [ ] 如果启用 AI：能收到流式回复（验证 `/api/agent/chat` 的 `proxy_buffering off`）
- [ ] `redis-cli CONFIG GET maxmemory-policy` **不是** `allkeys-*`
- [ ] `redis-cli INFO server | grep redis_version` ≥ 6.2
- [ ] `SELECT VERSION();` ≥ 8.0
- [ ] 暗色主题下：登录页、注册页、404 页、`/account/setup` 页可读（已知这几页有硬编码浅色）

### 12.9 内网移植就绪度评级

| 维度 | 评级 | 说明 |
|---|---|---|
| 运行时外网依赖（前端） | **A** | 零 CDN、零 webfont、Monaco 自托管 |
| 离线构建能力 | **A-** | 后端已实测离线可编译；前端需 npm 镜像或整体拷贝 `node_modules` |
| 配置可移植性 | **D** | prod 起不来、`JWT_SECRET` 未接线、唯一可用配置在 gitignore 文件里 |
| 中间件版本要求 | **B** | 要求明确（MySQL 8.0+ / Redis 6.2+）但没有任何地方写出来，容易踩 |
| 部署产物完备性 | **D** | 无 nginx 配置、无 Dockerfile、无 docker-compose、后端不托管前端 dist |
| 数据库迁移可控性 | **D** | 无迁移工具，4 个升级脚本永久丢失，启动补种有缺口 |
| 安全基线 | **C** | 会话/权限设计优秀，但 Jasypt 口令入库、JWT 密钥是占位值、默认连公网、banner 与前端品牌未清理 |
| **总评** | **C+（架构没问题，工程配套缺失）** | **补配置 + 写 nginx 配置，约 0.5~1 天可完成移植** |

---

## 13. 二次开发指南

### 13.1 加一个纯前端页面（最快，5 分钟）

1. 建文件 `FE/pages/<模块>/index.tsx`，**默认导出** React 组件：
   ```tsx
   export default function ProjectPage() { return <div>...</div>; }
   ```
   ⚠️ **必须是 `export default`**。只用具名导出会导致菜单能选中但渲染白屏（见 §9.3 的 bug）。
2. 浏览器里进"菜单管理"，新增一条：
   ```text
   菜单名称：项目管理
   菜单类型：MENU
   路由地址：/project
   前端组件：project/index        ← 下拉里能选到就说明产物里有这个文件
   ```
3. 给目标角色授权这个菜单。
4. 换目标账号登录（或刷新页面，因为菜单是登录时取的）。

**全程不需要改 `router/index.tsx`，也不需要改任何 map。**
⚠️ 生产环境新增 `.tsx` 后**必须重新 `npm run build`** —— 因为页面清单是 Vite 在**构建期**用 `import.meta.glob` 生成的，浏览器不可能在运行时执行一个从未编译过的 `.tsx`。

### 13.2 加一个完整业务模块（推荐顺序）

**后端 7 步**（照抄 `system/dept` 或 `system/role` 的结构）：

```text
org.lbl.project
├─ controller/ProjectController.java   # 只做 HTTP 编排，返回 Result/PageResult
├─ service/ProjectService.java         # 事务、状态机、数据范围校验
├─ mapper/ProjectMapper.java           # 只管 SQL
├─ entity/ProjectEntity.java           # 只映射表（@TableLogic + @TableField(fill=...)）
├─ request/ProjectRequest.java         # Validation 输入
└─ vo/ProjectVO.java                   # 输出契约
```

1. **Entity 只映射表**，需要逻辑删除就加 `@TableLogic` + `deletedTime`，审计列用 `@TableField(fill = ...)`（`MybatisConfig.auditHandler()` 会自动填充 `created_by/created_time/updated_by/updated_time`）。
2. **Request 加 Validation**，⚠️ **每个注解都必须手写 `message`** —— Bean Validation 的内置提示依赖本地化，`@Pattern` 会把**正则原样**插给用户看。
3. **Mapper 只写 SQL**，不要读 `SecurityContextHolder`。
4. **Service 写事务 + 数据范围**：列表查询用 `access.applyUserScope(actor, wrapper)` 把范围变成 SQL；对单个对象的操作用 `access.requireManageUser` / `requireManageDept` 之类校验。
5. **Controller 用 `@PreAuthorize`**，每个动作一个权限码，命名遵循现有风格 `project:list/add/update/delete`。
6. **写操作用 `@OperationLog`**，尽量填 `targetType` 和 `targetId`：
   ```java
   @OperationLog(module = "项目管理", action = "新增项目", targetType = "PROJECT", targetId = "#id")
   ```
7. **写单测**，至少覆盖：越权访问、边界状态、并发/重复提交。
   ⚠️ 这一步现在**没有任何基础设施**（只有 1 个假测试文件），你需要先决定测试策略。

**前端 5 步**：

1. 建 `FE/pages/project/index.tsx`（默认导出）。
2. 建 `FE/api/project.ts`（**一资源一文件**，返回业务 `data`）和 `FE/types/project.ts`（前后端契约）。
3. 列表页优先用 `SmartTable`（它已封装搜索区、工具栏、刷新、分页、加载、错误回调、请求竞态 `requestSequence`、输入与提交分离）。
4. 按钮用 `<Permission code='project:add'>...</Permission>` 控制显示。
5. 颜色/间距用主题变量与 Ant Design，不要另立一套（`theme/tokens.ts` 是默认外观的唯一定义处）。

**菜单与授权 3 步**：

1. 建 `MENU` 类型菜单（填 `routePath` + `component`）。
2. 建 `BUTTON` 子节点填 `permissionCode`（每个动作一个）。
3. 给角色授权，然后按 §12.8 的清单验证。

⚠️ **注意"看得见但 403"的陷阱**：`init_data.sql` 的种子数据只对**新库**生效。老库上新增权限点，必须手工插入 `sys_menu` 行（`SystemPermissionInitializer` 不会补业务模块的权限点）。

### 13.3 加一种数据范围

`AccessPolicy.roleScope`（`:110-134`）是一个 `switch`，`sys_role.data_scope` 的取值到 `Scope` 的映射就在这里。加新范围需要：

1. 在 `roleScope` 里加分支，返回合适的 `Scope(all, self, departments, branches)`；
2. 前端 `RoleDialog` 的数据范围下拉加选项（`FE/types/role.ts` 的联合类型也要放开）；
3. `AccessPolicy.requireScope`（`:232-237`）里考虑新范围是否允许非超管设置；
4. ⚠️ **`branches` 的语义要搞清楚**：它表示"动态子树授权"，目前**只有 `DEPT_AND_CHILDREN` 会填**它，而 `CUSTOM` 是精确集合、**不获得子树扩展能力**（`AccessPolicy.java:448` 注释明确说明）。这决定了用户能不能在新范围下创建子部门（`canCreateChildDept` 读的就是 `branches`）。

### 13.4 加一个 Agent 工具

见 §8.11 的 9 步。关键提醒：

- 工具**只调用已有 Service**，不要碰 Mapper / Controller / SecurityContext；
- **必须自己给所有 IO 设超时**（框架的 `deadline` 只是检查点，不会中断你）；
- 写操作工具在审批协议实现前**无法启用**（`ApprovalPolicy.REQUIRED` 是死枚举，`NOT_REQUIRED` 也不该被用来绕过）；
- 新增工具后**不需要改任何配置**，`ToolRegistry` 用 Spring 集合注入自动发现。

### 13.5 加一个实时事件类型

1. 后端在某处调 `realtime.send(userId, "your.event.name", payload)`（或注入 `RealtimeGateway`）。
   ⚠️ **必须在事务提交后发**：用 `TransactionSynchronizationManager` 注册 `afterCommit`（照抄 `NotificationService.afterCommit`），或直接在事务外调用。
   ⚠️ **载荷里不要放 null 值**（`Map.of` 会抛 NPE，而且是在事务提交后抛，无法回滚）。
2. 前端在 `AppLayout.tsx:112` 的 `onEvent` 里加一个分支。
   ⚠️ **不要用实时通道传业务数据**，只传"轻量信号"，然后前端用 REST 拉权威数据。因为 Pub/Sub 不重放，断线期间的事件永远丢失。
3. ⚠️ **顺手把 §7.7 的缺口修掉**：让 `RealtimeClient` 暴露 `onOpen`（或在 `AppLayout` 里处理 `connection.ready` 事件）→ 触发一次补拉。否则重连后丢失的数据要等到"下一个业务事件"或"标签页重新可见"才能补上。

### 13.6 复用清单（**不要重复造轮子**）

| 需求 | 用什么 | 位置 |
|---|---|---|
| 统一响应 | `Result<T>` / `PageResult<T>` | `BE/common/result/` |
| 分页列表页 | `SmartTable` | `FE/components/SmartTable/` |
| 按钮级权限 | `<Permission code=...>` | `FE/permission/Permission.tsx` |
| 数据范围过滤 | `AccessPolicy.applyUserScope` | `BE/security/context/AccessPolicy.java:136-145` |
| 功能权限 | `@PreAuthorize("hasAuthority('...')")` | Controller 上 |
| 操作审计 | `@OperationLog` | `BE/system/log/aspect/` |
| 业务异常 | `BusinessException` + `GlobalExceptionHandler` | `BE/common/exception/` |
| Excel 导出 | `ExcelExportUtil`（SXSSF 流式，已带公式注入防护） | `BE/common/util/` |
| 文件上传 | `FileUpload` 组件（⚠️ 当前未接线，多实例下有问题） | `FE/components/FileUpload/` |
| 菜单图标 | `IconPicker` + `iconRegistry` 白名单 | `FE/components/IconPicker/` |
| 图表生命周期 | `BaseChart`（ECharts）/ `G6Graph`（G6） | `FE/components/` |
| 代码查看 | `CodeViewer`（Monaco，已自托管可离线） | `FE/components/CodeViewer/` |
| 主题色值 | `useThemePreference()` / CSS 变量 | `FE/theme/` |
| 请求/续期 | `request`（Axios）/ `authenticatedFetch`（SSE） | `FE/utils/request.ts`、`FE/services/authSession.ts` |
| 实时推送 | `RealtimeGateway.send` / `RealtimeClient` | `BE/realtime/`、`FE/services/` |
| 部门路径 | `DeptPaths`（**唯一口径**，别自己拼字符串） | `BE/system/dept/support/` |

### 13.7 十条禁忌（都有血泪注释背书）

1. **不要把 refresh sid、JWT、密码、模型 API Key 写进日志。**
2. **不要把 refresh token 放 localStorage。**（当前是 HttpOnly Cookie）
3. **不要因为前端隐藏了按钮就省略后端鉴权。**
4. **不要用角色名判断权限**，用权限码 + `AccessPolicy`。
5. **不要让 Service 返回 Entity 给复杂页面**，用 VO 稳定契约。
6. **不要在事务提交前发"成功"通知**（回滚后会产生假事件）。
7. **不要把大业务数据通过 WebSocket 推**，推轻量事件再 REST 补拉。
8. **不要在 WebSocket URL 传长期 JWT**，用一次性 ticket。
9. **不要让 Agent 工具直接调 Mapper。**
10. **不要信任模型选择的工具名、参数或"用户已经同意"的文本** —— Registry、Validation、权限、审批门禁必须由代码执行。
11. **`@Transactional` 里绝不做网络 IO**（连接池只有 10）。
12. **带唯一索引 + 逻辑删除的字段，查重必须用"含已删除"口径**（否则"预检通过、插入失败"）。
13. **部门树 `ancestors` 匹配必须带逗号边界**，统一走 `DeptPaths`（`LIKE '0,1,2%'` 会把部门 20 当成部门 2 的后代，静默算错）。
14. **改密/停用/改角色/重置密码后必须 `authVersion++` + `sessions.removeAll()`**（双保险）。
15. **每个 Validation 注解都要手写 `message`。**
16. **401 与 403 必须严格区分**（前端逻辑依赖它：401 触发静默续期，403 直接展示）。

---

## 14. 问题与风险清单（汇总，按优先级）

### P0 —— 上线/交付前必须处理

| # | 问题 | 位置 | 后果 |
|---|---|---|---|
| 1 | **prod profile 缺 `lbl.security.*`（含 `jwt-secret`）→ 启动失败** | `application-prod.yml` 全文；`SecurityProperties.java:27-30` 无 `@DefaultValue`；`JwtService.java:23` 直接解引用 | 用 prod 启动直接崩 |
| 2 | **`JWT_SECRET` 环境变量全仓库零引用** | 无任何 yml 写 `jwt-secret: ${JWT_SECRET}` | 以为设了环境变量就能上线，实际无效 |
| 3 | **默认 profile 连公网 MySQL / Redis / LLM** | `application.yml:5`（默认 `local`）+ `application-local.yml:3,25,157` | 内网/断网环境完全不可用（**实测这三个端点当前可达**） |
| 4 | **`banner.txt` 每次启动打印 ASCII "SHIT" + 私人语录** | `backend/src/main/resources/banner.txt:20-30` | 内网部署后开机横幅即社死 |
| 5 | **Jasypt 主口令 `kariya` 明文已提交入库** | `application.yml:15` | `ENC(...)` 加密形同虚设 |
| 6 | **96 个改动未提交；`README.md` 未跟踪；`docs/` 14 份文档被删** | `git status` | 没有任何已知良好的还原点；一次 `git clean -fd` 就丢 README 和 20 个新源文件 |
| 7 | **测试覆盖 ≈ 0**（1 个文件 53 行，且断言恒真）；3 个 Agent 测试类已被暂存删除 | `backend/src/test/` | 任何重构都没有安全网 —— 这是"不敢改"的根因 |

### P1 —— 高优先级（影响正确性/安全）

| # | 问题 | 位置 |
|---|---|---|
| 8 | **前端无 ErrorBoundary + `availableComponents()` 把 5 个无 `default` 导出的 Dialog 当合法页面** → 菜单可选中 → 白屏 | `componentRegistry.tsx:19-22,64-68`、`MenuDialog.tsx:52` |
| 9 | **`keepAlive` 页面首次进入挂载两次**（重复请求 + 丢弃状态） | `DynamicPage.tsx:57-73` |
| 10 | **`AuthGuard` 每次整页加载请求 `/auth/me` 两次** | `AuthGuard.tsx:18-45` |
| 11 | **三方登录"登录 CSRF"**：`state` 未与发起浏览器绑定 | `ExternalLoginService.begin/complete`（grep `ext_state` 零命中） |
| 12 | **`/api/auth/external/*/callback` 无限流**，任何失败都写 `sys_login_log` | `ExternalLoginService.java:169-170,177` |
| 13 | **`JwtAuthenticationFilter` 开户态分支在 try 内调 `chain.doFilter`** → 异常被吞后 `:80` 会**再次调用**，下游可能执行两次 | `JwtAuthenticationFilter.java:57,75,80` |
| 14 | **`UserController` 用 `@CookieValue(lbl_refresh)` 读刷新 Cookie**，但 Cookie `Path=/api/auth` → 永远读不到，"当前会话"标记恒为 false | `UserController.java:116` vs `RefreshCookieFactory.java:38` |
| 15 | **重连成功不触发 REST 补拉**，与代码注释和 README 的承诺都不符 | `RealtimeClient.ts:32-34`、`AppLayout.tsx:111-116` |
| 16 | **`notification.created` 载荷用 `Map.of` 且含可空 `businessId`** → 提交后抛 NPE（无法回滚） | `NotificationService.java:23-25` |
| 17 | **ApprovalPolicy.REQUIRED 是死枚举**，无确认/恢复协议 | `AgentRunner.java:64`、`ToolExecutionService.java:29-31` |
| 18 | **"工具超时"只是检查点，无法中断阻塞工具** | `ToolExecutionService.java:38-42` |
| 19 | **`AuthService.login` 挂 `@Transactional` 却无写操作**，叠加 REQUIRES_NEW 审计 → 单次登录占 2 条连接（池上限 10） | `AuthService.java:44`、`LogRetentionProperties` 同族配置 |
| 20 | **登录限流计时非原子**（先 `increment` 再 `expire`，无 Lua/MULTI）→ EXPIRE 丢失则永久锁定 | `LoginAttemptGuard.java:122-135` |
| 21 | **dayjs 未加载 `zh-cn` 语言包** → RangePicker 面板周/月缩写可能仍是英文 | 全局；`loginlog:83`、`operatelog:91` |
| 22 | **一键已读静默失败**（`dispatch` 无 `.unwrap()`） | `notifications/index.tsx:48-50`、`AppLayout.tsx:137` |
| 23 | **`sys_operation_log` 的 `targetId` 关键写操作缺失**（菜单、日志删除等），审计无法定位对象 | `MenuController.java:44,51`、`LoginLogController.java:41`、`OperationLogController.java:38` |
| 24 | **审批完成/取消时步骤状态不复位** | `DepartmentTransferApprovalService.java:106,126-129` |
| 25 | **`sys_department_change_request.version` 不是乐观锁**（无 `@Version`，只 +1 不校验） | `DepartmentTransferRequestEntity.java:17` |
| 26 | **文件上传暂存是进程内内存 Map** → 多实例必然失败 | `LocalStagedFileStorage.java:27,59` |
| 27 | **`/api/files/**` 无权限码**（只靠 `authenticated()`） | `FileUploadController.java:19-28` |
| 28 | **`SystemPermissionInitializer` 清单不含 `system:user:*`** → 老库升级不补用户模块权限 | `SystemPermissionInitializer.java:57-76` |
| 29 | **`GET /api/auth/captcha` 的开关属性不存在**（`lbl.security.captcha-enabled` 无定义）→ 验证码无法关闭；容器缺字体时只能装字体 | `RegistrationRequest.java:13`、`SecurityProperties.java` |
| 30 | **生产 WebSocket 无 Origin 限制**（`allowedOrigins` 仅在 CORS 配置非空时设置，prod 默认为空） | `RealtimeWebSocketConfig.java:43-44` |
| 31 | **前端 12 处产品名 `LBL SHIT` + 14 处私人文案** | 见 §12.4 |
| 32 | **子路径部署不支持**（`vite.config.ts` 无 `base`，但注释宣称支持） | `vite.config.ts:2`、`monaco.ts:10` |

### P2 —— 中优先级（可维护性/体验）

| # | 问题 |
|---|---|
| 33 | 会话失效策略不一致：管理员改用户**任何字段**都踢全部设备，用户自助改联系方式不失效（`UserService.java:204-206` vs `:142-148`） |
| 34 | 无集中的 `bumpAuthVersion()`，内联在 3 处 |
| 35 | 密码重置不清登录锁定 → 被锁用户重置密码后仍需等最多 15 分钟（`UserPasswordService.reset`） |
| 36 | `RoleController.grant` 缺 `@Valid` → `@NotNull` 对容器元素不生效（`RoleController.java:81`） |
| 37 | 上帝类：`ExternalLoginService` 493、`AccessPolicy` 475、`UserService` 386 行；`AccessPolicy` 单文件 8 个职责 |
| 38 | 死代码：UIAS 两个端口零实现、`principalType` JWT claim 只写不读、`SessionService.deserialize` 旧格式分支、`ToolArgumentBinder.bind(Map)` 无调用者、`DepartmentTransferRequestMapper.byRequester` 无调用者、前端 `FileUpload` 整套 + `registerArtifactRenderer` + `SmartTable.searchConfig` + `theme/index.ts` 等 |
| 39 | 前端重复逻辑：两个逐字节相同的 CSS、3 处重复的审批 Steps 映射、`toTree` 重复 6 次 |
| 40 | 前端无 ESLint/Prettier（`.prettierrc.json` 存在但无依赖无脚本），却留着 2 处无效的 `eslint-disable` |
| 41 | tsconfig 未开 `noUnusedLocals`/`noUnusedParameters`/`noImplicitReturns`/`noUncheckedIndexedAccess` |
| 42 | `AccessPolicy.Scope.contains` 表达式无括号，依赖 `&&` 优先级 → 重构时会静默改变权限语义 |
| 43 | 部门列表在 Java 里过滤（非 SQL），且 `actor()` 每请求全表读 `sys_dept` |
| 44 | 部门子树移动逐行 UPDATE（k 个节点 k 次） |
| 45 | 审批详情 N+1（每步 3 次 `selectById`；`participants()` 被重复调用） |
| 46 | 日志保留任务无分布式锁 → 多实例重复清理 |
| 47 | 审计注解在 `finally` 中，若自身抛异常会**覆盖**业务异常/返回值 |
| 48 | 前端首屏 chunk 2.39 MB（两张近 1MB 的图 + 无 `manualChunks`）；`favicon.ico` 实为 996 KB 的 PNG 且与 `logo.png` 完全相同 |
| 49 | `dist/` 未被 git 跟踪且比源码旧（17:01 vs 19:29）→ 发布前必须重新构建 |
| 50 | 前端 `G6Graph` 渲染失败即泄漏；`RealtimeClient` 重连无上限且不区分 4xx；`useAgentChat` 并发发送错乱 loading；AI 消息 id 可能碰撞 |
| 51 | 前端可访问性：可点击 `List.Item` 不可键盘操作、表格 `<a onClick>` 无 `href`、`FloatButton` 无 `aria-label`、无 `prefers-reduced-motion` |
| 52 | `.gitignore` 是 GBK 编码（乱码）；`application-local.yml:34` 的 `org.kariya: DEBUG` 是无效配置 |
| 53 | 4 个升级 SQL 脚本（audit_fields / basic_role / department_workflow / password_policy）**从未进入 git，永久丢失**；`backend/target/` 无 jar |
| 54 | `README.md` 多处与代码不符：ticket TTL（30s vs 60s）、角色/菜单变更"使会话失效"（实际不会）、`AgentRun` "供审计"（零持久化）、`FileUpload` 列为可复用（无调用点）、artifact 按类型渲染（永远 JSON 兜底）、引用的迁移脚本不存在、`docs/AI-AGENT.md` 已被删但仍被引用 |

### 14.1 修复优先级建议（按投入产出比）

**第一批（半天，风险最低、收益最高）**
1. `git checkout -- docs/` 恢复文档 → 然后**立即提交一次**，固化还原点。
2. 清空 `banner.txt`（或换成公司 banner）。
3. 补 `application-prod.yml` 的 7 个 `lbl.security.*` 键，其中 `jwt-secret: ${JWT_SECRET}`。
4. 把 `application-local.yml` 里的公网地址改成内网地址；清空 `EXTERNAL_AUTH_PROXY_*`；关闭 GitHub/Google 登录。
5. 把 `.gitignore` 转成 UTF-8；`git add README.md`。

**第二批（1-2 天，修真实缺陷）**
6. 给前端加一个顶层 ErrorBoundary，并让 `componentRegistry` 的 glob 排除 `*Dialog.tsx`。
7. 修 `AuthGuard` 的重复 `/auth/me`（把 `token` 从依赖数组移出）。
8. 修 `keepAlive` 的双挂载。
9. 修 `JwtAuthenticationFilter` 的双 `chain.doFilter`。
10. 修 `UserController` 的 `@CookieValue` 路径不匹配。
11. `RealtimeClient` 暴露 `onOpen` → 重连后补拉。
12. `NotificationService` 的 `Map.of` 改成可空安全（`HashMap` 或过滤 null）。
13. 前端补 `dayjs/locale/zh-cn`。
14. `readAllNotifications` 加 `.unwrap()`。

**第三批（3-5 天，补工程能力）**
15. **建立测试基线**：至少给 `AccessPolicy`、`AuthService`、`RoleService.grantMenus`、`LoginAttemptGuard` 写单测（可以先从"越权必须被拒"这类高价值用例开始）。这是解锁"快速迭代"的前提。
16. 引入 Flyway（`baseline-on-migrate=true`）或建立 `db/migration-manual/` 约定。
17. 加 ESLint + Prettier 并接进 `npm run lint`。
18. 写 nginx 配置 + Dockerfile 入库。
19. 前端图片压缩（三张约 1MB 的图）+ 配 `manualChunks`。

---

## 15. 附录

### 15.1 权限码总表（24 个业务码 + 4 个开户态码）

| 权限码 | 后端校验位置 | 前端 `<Permission>` | 种子 ID |
|---|---|---|---|
| `system:user:list` | `UserController.java:50` | — | 7 |
| `system:user:add` | `:62,72,85` | `user/index.tsx:174` | 8 |
| `system:user:update` | `:56,62,92,114,121,129` | `user/index.tsx:71,78` | 9 |
| `system:user:delete` | `:99` | `user/index.tsx:111` | 10 |
| `system:user:reset-password` | `:107` | `user/index.tsx:85` | 11 |
| `system:user:export` | `:78` | `user/index.tsx:169` | 12 |
| `system:role:list` | `RoleController.java:27` | — | 19 |
| `system:role:add` | `:33,45` | `role/index.tsx:108` | 20 |
| `system:role:update` | `:33,39,52` | `role/index.tsx:56` | 21 |
| `system:role:delete` | `:59` | `role/index.tsx:61` | 22 |
| `system:role:grant` | `:67,73,79` | `role/index.tsx:51` | 23 |
| `system:menu:list` | `MenuController.java:24` | — | 28 |
| `system:menu:add` | `:36` | `menu/index.tsx:132` | 29 |
| `system:menu:update` | `:30,43` | `menu/index.tsx:88` | 30 |
| `system:menu:delete` | `:50` | `menu/index.tsx:93` | 31 |
| `system:dept:list` | `DeptController.java:25` | — | 24 |
| `system:dept:add` | `:37,43` | `dept/index.tsx:126` | 25 |
| `system:dept:update` | `:31,37,50` | `dept/index.tsx:72` | 26 |
| `system:dept:delete` | `:57` | `dept/index.tsx:77` | 27 |
| `system:loginlog:list` | `LoginLogController.java:29` | — | 15 |
| `system:loginlog:delete` | `:40` | `loginlog/index.tsx:88` | 16 |
| `system:operatelog:list` | `OperationLogController.java:25` | — | 17 |
| `system:operatelog:delete` | `:37` | `operatelog/index.tsx:96` | 18 |
| `agent:chat:use` | `AgentChatController.java:59` | `AppLayout.tsx:309` | 32 |
| `onboarding:access` | 8 处 `!hasAuthority(...)` 排除式 | — | 无（固定授予） |
| `onboarding:account:create` | `OnboardingAccountController.java:34` | — | 无（固定授予） |
| `onboarding:account:bind` | `:45` | — | 无（固定授予） |

**平台级权限**（不允许下放）：`system:menu:list/add/update/delete` + `system:user:reset-password` → `AccessPolicy.java:265-271`。

### 15.2 Redis key 总表

见 §11.2（13 个 key 模式 + 1 个 Pub/Sub 频道）。

### 15.3 REST 接口总表

见 §6.11。

### 15.4 配置项总表（谁在哪定义、默认值）

| 前缀 | Properties 类 | 是否有 `@DefaultValue` | 定义位置 |
|---|---|---|---|
| `lbl.security` | `SecurityProperties` | ❌ **一个都没有** | 仅 `application-local.yml:124-142`；`application-prod.yml:10-18` 只覆盖 3 项 |
| `lbl.file-upload` | `FileUploadProperties` | ✅ 全部有 | 仅 `application-local.yml:117-123` |
| `lbl.log.retention` | `LogRetentionProperties` | ✅ 全部有 | 仅 `application-local.yml:143-152` |
| `lbl.external-auth` | `ExternalAuthProperties` | 部分（Java 字段默认） | 仅 `application-local.yml:42-116` |
| `lbl.agent` | `AgentProperties` | ✅ 全部有 | 仅 `application-local.yml:156-168` |
| `spring.datasource` / `spring.data.redis` | —— | —— | `application-local.yml`（公网）/ `application-prod.yml`（`${ENV}`，**Redis 无 password 项**） |
| `management.*` | —— | —— | `application.yml:27-39` |
| `jasypt.encryptor` | —— | —— | `application.yml:13-20`，**口令 `kariya` 明文** |

**⚠️ 关键结论：所有 `lbl.*` 配置只存在于被 gitignore 的 `application-local.yml`。** `application.yml` 已完全没有 `lbl` 块。

### 15.5 命令速查

```bash
# ── 后端 ────────────────────────────────────────────────
cd backend
mvn -o -B -DskipTests compile          # 离线编译（已验证可行）
mvn -B -DskipTests package             # 打包（无 mvnw，需预装 Maven）
mvn spring-boot:run                    # 默认 local profile
java -jar target/lbl-shit-1.0.0.jar --spring.profiles.active=prod

# ── 前端 ────────────────────────────────────────────────
cd frontend
npm ci
npm run dev                            # http://localhost:5173
npm run build                          # = tsc --noEmit && vite build
npx tsc --noEmit -p tsconfig.app.json  # 只做类型检查（已验证 exit 0）

# ── 恢复被删的文档（只读操作，不覆盖现有文件）────────────
cd D:\kariya_admin
git checkout -- docs/

# ── 恢复可从 git 找回的 SQL ─────────────────────────────
git show 9eb293d^:backend/src/main/resources/db/upgrade__schema.sql
git show 64d5678:backend/src/main/resources/db/migration/V1__schema.sql

# ── 数据库 ──────────────────────────────────────────────
mysql -u root -p -e "CREATE DATABASE kariya_admin DEFAULT CHARSET utf8mb4;"
mysql -u root -p kariya_admin < backend/src/main/resources/db/init_schema.sql
mysql -u root -p kariya_admin < backend/src/main/resources/db/init_data.sql

# ── Redis 排查 ──────────────────────────────────────────
redis-cli INFO server | grep redis_version          # 必须 >= 6.2（GETDEL）
redis-cli CONFIG GET maxmemory-policy               # 不能是 allkeys-*
redis-cli KEYS 'auth:session:*'                     # 在线会话
redis-cli KEYS 'realtime:ticket:*'                  # 未消费的握手票
redis-cli GET "auth:session:<sid>" | jq             # 查看会话内容

# ── 强制所有人重新登录（应急）──────────────────────────
mysql -u kariya -p kariya_admin -e "UPDATE sys_user SET auth_version = auth_version + 1;"

# ── 健康检查 ────────────────────────────────────────────
curl http://localhost:8080/actuator/health           # 聚合状态（详情对匿名隐藏）
curl http://localhost:8080/actuator/health/readiness # 含 db + redis
```

### 15.6 本文档的核查方法与已知限制

**核查方法**：

- 逐文件阅读了后端 `org/lbl` 下全部 182 个 Java 文件、4 个 yml、2 个 SQL、`pom.xml`；前端 `src` 下全部 100 个 ts/tsx 文件（含 CSS 共 127 个）、5 个配置文件。
- **实测验证**：`mvn -o -B -DskipTests compile` → BUILD SUCCESS（182 文件）；`npx tsc --noEmit` → exit 0；Maven/npm/Java/Node 版本；`frontend/dist` 体积与文件数；TCP 连通性（3308 / 6381 / 443）；种子数据的权限码数量（实为 **24**）；大类的准确行数；git 跟踪状态与历史（含凭据泄露扫描）。
- 对 `README.md` 与 `docs/` 的**每一项关键论断都回到代码核对**，并在正文标出所有不一致处。
- 交叉验证了前后端的事件名字面量、SSE 字段名、18 个前端权限码与后端权限码全集、`api/` 契约与 Controller 注解。

**已知限制（明确标注"未验证"的事项）**：

1. **`application-prod.yml` 启动失败的确切异常类型未实测** —— 结论基于静态推导（record 无 `@DefaultValue` + `JwtService` 构造器直接解引用）。
2. **前端生产构建（`vite build`）未能在本会话验证** —— 沙箱的命名管道限制导致 esbuild 无法 spawn service 进程（`spawn EPERM`）。类型检查已通过，且 `dist` 是今天构建的。
3. **后端未实际启动、未连接数据库** —— 没有执行任何 SQL、未验证 `init_data.sql` 的 BCrypt 哈希确实对应 `Admin@123`。
4. **`JasyptEncryptorTest` 在无 DB/Redis 环境下能否通过未验证**（它是 `@SpringBootTest`）。
5. **DatePicker 的中文面板未在浏览器实测** —— 只核实了"dayjs 语言包未被打包"这一机制链。
6. **暗色主题下各页面的实际视觉效果未实测**（只核实了 CSS 中无相关兜底规则）。
7. **`RedisMessageListenerContainer` 在"已 start 但底层订阅后台重连失败"时的 `isRunning()` 行为未验证**（本机无 Spring 构件可反编译）。
8. **前端与后端权限码的逐端点全量比对未做**（只比对了前端实际出现的 18 个码与页面的增删改端点）。
9. `backend/src/main/java/org/lbl/approval/**` 的分支细节以静态阅读为准，未做运行时验证。

**最后一句建议**：这个项目的**代码质量足以支撑二次开发**，你"看不懂"的感觉主要来自三件事 —— 历史被 96 个未提交改动和无语义 commit 信息冲掉了、文档与代码已经漂移、以及没有任何测试能告诉你"改了会不会坏"。**先做 §14.1 的第一批（半天），掌控力就会有明显改善。**

<!-- END OF README_DS -->
