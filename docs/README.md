# Kariya Admin —— 项目总览与上手指南

> 面向"刚接手这个项目的新人"写的说明书。目标：读完这一篇，你能说清楚**这个系统是什么、已经做到哪、代码在哪、怎么跑起来、怎么加一个新功能**。
>
> 配套文档（建议按顺序读）：
>
> | 文档 | 内容 |
> | --- | --- |
> | `README.md`（本篇） | 全景、技术栈、目录结构、功能清单、完成度、快速启动 |
> | `ARCHITECTURE.md` | 前后端代码逻辑：一次请求的完整生命周期、动态路由原理、顶部进度条、分层约定 |
> | `AUTH.md` | 登录 / 鉴权 / 会话全链路，数据表清单，Redis key 清单 |
> | `THEME.md` | **主题与外观系统**：怎么用、怎么改默认值、CSS 变量速查、为什么以前改不动 |
> | `DEVELOPMENT.md` | 手把手：如何新增一个业务页面 / 一个完整模块 |
> | `INTRANET-MIGRATION.md` | 移到公司内网：改名、品牌清理、配置与安全清单 |
> | `DATA-SCOPE-ROLE-DEPT.md` | `sys_role_dept`（自定义数据范围）从零落地方案 |
> | `THIRD-PARTY-LOGIN.md` | GitHub / 微信 / Google / UIAS 三方登录的真实可用性与启用步骤 |
> | `UIAS-INTEGRATION.md` | UIAS SDK 与 ESF 人员目录的接入边界、配置和账号匹配策略 |
> | `ASSESSMENT.md` | 代码质量评估、冗余清单、漏洞与不足、未完成项与路线图 |
> | `ARCHIVE-removed-code.md` | 本次清理中删除的代码原文（其中 3 个后端文件**从未进过 git**，只能从这里找回） |

---

## 1. 这是什么

一个**个人用途的、前后端分离的后台管理系统基座**。

它的定位很明确：**不是 demo，也不是成品业务系统，而是"业务开发基座"**——登录、权限、菜单、日志、会话、通知这些"每个后台都要有、写起来又很烦"的部分已经做完并打磨过；业务功能（你真正想写的东西）还基本是空白，留好了标准插槽。

所以它的价值在于：

- **下班后写兴趣功能**：直接建页面 + 建菜单即可开始写业务，不用再碰权限和路由。
- **搬到公司内网做工作系统**：改掉命名与品牌（见 `INTRANET-MIGRATION.md`），再补上公司需要的组织/权限细节，就能当真实系统用。

### 它已经具备的能力（一句话版）

| 能力 | 状态 |
| --- | --- |
| 账号密码登录 | ✅ 完成，含失败锁定、图形验证码、首登强制改密 |
| 三方登录（GitHub / 微信 / Google） | ⚠️ 代码完整，**从未端到端验证过**，见 `THIRD-PARTY-LOGIN.md` |
| 内部统一认证 UIAS | ⚠️ 流程骨架已完成；需在内网实现 UIAS SDK 与 ESF 适配器，见 `UIAS-INTEGRATION.md` |
| 会话管理（多设备、踢下线、记住我） | ✅ 完成，基于 Redis 服务端会话 + 短寿命 JWT |
| 用户 / 角色 / 部门 / 菜单 四大管理页 | ✅ 完成，含数据范围、越权防护、导出 |
| RBAC 权限（菜单 + 按钮 + 后端 `@PreAuthorize`） | ✅ 完成，前后端同一份口径 |
| 数据范围（全部 / 本部门及下级 / 本部门 / 本人） | ✅ 已实现；**"自定义指定部门"未实现**（见 `DATA-SCOPE-ROLE-DEPT.md`） |
| 操作日志 / 登录日志 + 自动清理 | ✅ 完成 |
| 站内通知 + WebSocket 实时推送 | ✅ 完成 |
| 部门变更审批流（样例业务模块） | ✅ 完成，可作为业务开发参考模板 |
| 文件暂存上传 | ⚠️ 后端完成，**前端组件 `FileUpload` 是死代码**，没有任何页面在用 |
| 个人中心（联系方式 / 登录与安全 / 消息中心） | ✅ 完成 |
| 首页数据看板 | ❌ **全是写死的假数据**，未接后端 |

---

## 2. 技术栈

### 后端 `backend/`

| 项 | 值 | 出处 |
| --- | --- | --- |
| 语言 / 运行时 | Java 17 | `backend/pom.xml:15` |
| 框架 | Spring Boot 3.4.4 | `backend/pom.xml:8` |
| 安全 | Spring Security 6（无状态 + JWT） | `backend/pom.xml:28-31` |
| ORM | MyBatis-Plus 3.5.12（**全部注解式 SQL，没有 mapper XML**） | `backend/pom.xml:36-45` |
| 数据库 | MySQL 8.0+ | `backend/src/main/resources/db/init_schema.sql` |
| 缓存 / 会话 | Redis（`StringRedisTemplate`） | `backend/pom.xml:51-54` |
| JWT | jjwt 0.12.6（HMAC-SHA） | `backend/pom.xml:55-71` |
| 配置加密 | Jasypt 3.0.5（`ENC(...)` 语法） | `backend/pom.xml:72-76` |
| Excel 导出 | Apache POI 5.3.0（SXSSF 流式写） | `backend/pom.xml:86-90` |
| WebSocket | spring-boot-starter-websocket（原生 `TextWebSocketHandler`） | `backend/pom.xml:32-35` |
| AOP | AspectJ weaver（操作日志切面） | `backend/pom.xml:81-85` |
| 代码量 | 129 个 Java 文件，约 7,300 行主代码 + 约 440 行测试 | — |

**重要事实：项目没有 Mapper XML，也没有 Flyway / Liquibase 之类的迁移工具。** 所有 SQL 都写在 Mapper 接口的 `@Select` 注解里；建表脚本只有 `db/init_schema.sql` + `db/init_data.sql` 两个"只面向空库"的文件。（历史上曾有 `db/upgrade__schema.sql`，现已被删除——这意味着**对已有数据库没有升级脚本**，见 `ASSESSMENT.md`。）

### 前端 `frontend/`

| 项 | 值 | 出处 |
| --- | --- | --- |
| 框架 | React 18.3.1（注意：不是 19） | `frontend/package.json:29-30` |
| 语言 | TypeScript 5.8（`tsc --noEmit` 参与 build） | `frontend/package.json:38` |
| 构建 | Vite 6 | `frontend/package.json:39` |
| UI 库 | Ant Design 5.26 | `frontend/package.json:20` |
| 路由 | react-router-dom 7（`createBrowserRouter`） | `frontend/package.json:32` |
| 状态管理 | Redux Toolkit 2.8 + react-redux 9 | `frontend/package.json:19,31` |
| HTTP | axios 1.10，单实例 + 拦截器 | `frontend/src/utils/request.ts` |
| 图表 | ECharts 5.6 | `frontend/package.json:25` |
| 样式 | 纯 CSS + CSS 变量（**没有 Less / CSS Modules / Tailwind**） | `frontend/src/styles/*.css` |
| 代码量 | 89 个 ts/tsx 文件，含 CSS 共约 6,000 行 | — |

**前端没有 ESLint。** `frontend/.prettierrc.json` 存在，但 `package.json` 里既没有 `prettier` 也没有 `eslint` 依赖——它目前是一份"没人执行"的配置。

### 几个"装了但没直接用"的依赖

| 依赖 | 状态 |
| --- | --- |
| `nprogress` | ✅ **已真正接入** —— 顶部进度条，见 `ARCHITECTURE.md`「顶部进度条」 |
| `monaco-editor` / `@monaco-editor/react` | ⚠️ 只被**预留组件** `CodeViewer` 用到（该组件按使用者要求保留，计划后续接线）。**注意**：它让 `node_modules` 多了 **94 MB**，且因为 `tsc` 会类型检查这个文件，monaco 升级时可能让 build 失败 |
| `@antv/g6`（35.9 MB） | ⚠️ 零引用。有图可视化计划就留，否则可删 |
| `html2canvas`（3.2 MB） | ⚠️ 零引用。有"导出为图片"计划就留，否则可删 |
| `classnames` | ⚠️ 零引用（使用者计划自行删除） |

未使用依赖的取舍与成本分析见 `ASSESSMENT.md` 第 2 节。

---

## 3. 目录结构

```
kariya_admin/
├── backend/                          # Spring Boot 后端
│   ├── pom.xml
│   └── src/main/
│       ├── java/org/lbl/
│       │   ├── LblShitApplication.java        # 启动类
│       │   ├── auth/                          # 【认证域】登录/注册/会话/三方登录
│       │   │   ├── controller/                #   AuthController、ExternalAuthController…
│       │   │   ├── service/                   #   AuthService、RegistrationService、CaptchaService…
│       │   │   ├── session/                   #   SessionService（Redis 会话）、LoginSession
│       │   │   ├── identity/                  #   本地密码凭据 + 外部身份绑定
│       │   │   ├── external/                  #   三方登录协议实现（OAuth2/OIDC）
│       │   │   └── model/                     #   请求/响应 record
│       │   ├── security/                      # 【安全域】过滤器、JWT、权限判定
│       │   │   ├── filter/JwtAuthenticationFilter.java   # 每个请求的入口
│       │   │   ├── jwt/JwtService.java
│       │   │   ├── context/AccessPolicy.java             # 权限/数据范围唯一判定处（387 行，核心）
│       │   │   └── proxy/TrustedProxyResolver.java        # 真实 IP 解析（防伪造）
│       │   ├── system/                        # 【系统管理域】
│       │   │   ├── user/       # 用户 CRUD、导出、改密、会话管理
│       │   │   ├── role/       # 角色 CRUD、菜单授权
│       │   │   ├── dept/       # 部门树（物化路径 ancestors）
│       │   │   ├── menu/       # 菜单/按钮权限
│       │   │   └── log/        # 登录日志、操作日志、保留策略
│       │   ├── notification/                  # 站内通知
│       │   ├── realtime/                      # WebSocket 推送（票据握手）
│       │   ├── file/                          # 文件暂存上传
│       │   ├── departmentchange/              # 【样例业务模块】部门变更审批流
│       │   ├── common/                        # 统一响应、全局异常、Excel 工具、请求日志
│       │   └── config/                        # 安全配置、MyBatis 配置、内置权限初始化
│       └── resources/
│           ├── application.yml                # 主配置（含 lbl.* 自定义配置）
│           ├── application-local.yml          # 本地开发配置（被 .gitignore 忽略，含真实库密码）
│           ├── application-dev.yml            # 空模板
│           ├── application-prod.yml           # 生产：全部走环境变量
│           ├── banner.txt                     # 启动横幅（含需要改掉的语录）
│           └── db/init_schema.sql, init_data.sql
│
└── frontend/                         # React 前端
    ├── index.html                    # 入口 HTML（含首屏 loading 动画；乱码问题已修复）
    ├── vite.config.ts
    ├── .env.development              # VITE_API_BASE_URL=http://localhost:8080/api
    ├── .env.production               # VITE_API_BASE_URL=/api
    ├── public/
    │   ├── favicon.ico               # 995KB，实际是 logo 图（首屏 loading 也在用）
    │   └── astronaut-404.png         # 404 页插图
    └── src/
        ├── main.tsx                  # Provider 树：Redux → Theme → AntD App → Router
        ├── api/                      # 按模块拆的接口封装（薄封装，只做 request + 解包）
        ├── components/               # 通用组件（见第 5 节）
        ├── hooks/                     # 3 个 hook（其中 2 个只服务死代码组件）
        ├── layouts/AppLayout.tsx     # 主框架：侧边栏 + 顶栏 + 通知 + 改密弹窗（264 行）
        ├── pages/                    # 页面（目录结构 = 菜单里的 component 值）
        ├── permission/Permission.tsx # 按钮级权限组件
        ├── router/                   # 动态路由核心（见 ARCHITECTURE.md）
        ├── services/                 # RealtimeClient（WebSocket）、UserActivityManager（空闲续期）、progress（顶部进度条）
        ├── store/                    # Redux：authSlice、notificationSlice
        ├── styles/                   # global.css / reset.css / theme.css
        ├── theme/ThemeProvider.tsx   # 主题/暗色模式/紧凑模式
        ├── types/                    # 与后端 DTO 对应的 TS 类型
        └── utils/                    # request.ts（axios 实例）、passwordPolicy、apiError…
```

---

## 4. 快速启动

### 4.1 准备数据库

```bash
mysql -u root -p -e "CREATE DATABASE kariya_admin DEFAULT CHARSET utf8mb4;"
mysql -u root -p kariya_admin < backend/src/main/resources/db/init_schema.sql
mysql -u root -p kariya_admin < backend/src/main/resources/db/init_data.sql
```

初始账号：**`admin` / `Admin@123`**，首次登录会强制改密（`init_data.sql:14-20`）。

### 4.2 后端

`application-local.yml` 被 `.gitignore` 忽略（`backend/src/main/resources/application-local.yml` 里有真实的公网库地址和 Jasypt 加密后的密码）。**换一台机器跑，你需要自己新建这个文件**，最小内容：

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/kariya_admin?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: root
    password: 你的明文密码        # 不想用 Jasypt 就直接写明文
    driver-class-name: com.mysql.cj.jdbc.Driver
  data:
    redis:
      host: 127.0.0.1
      port: 6379
      # password: 有密码就写
server:
  port: 8080
```

三方登录的提供方目录已在受版本控制的 `application.yml` 中；本地或生产环境只需设置对应的 `*_LOGIN_ENABLED`、`*_CLIENT_ID` / `*_CLIENT_SECRET` 与 `BACKEND_BASE_URL`、`FRONTEND_BASE_URL` 环境变量，切勿把它们写入 `application-local.yml`。

```bash
cd backend
mvn spring-boot:run          # 或 IDE 里直接跑 LblShitApplication
```

启动时 `SystemPermissionInitializer`（`@Order(100)` 的 `ApplicationRunner`）会**幂等补建**内置菜单与按钮权限，并自动授予 `super_admin`。所以老库升级不需要手工补菜单。

### 4.3 前端

```bash
cd frontend
npm install
npm run dev                  # http://localhost:5173
npm run build                # tsc --noEmit + vite build
```

前端默认请求 `http://localhost:8080/api`（`frontend/.env.development`）。**用 `localhost` 而不是 `127.0.0.1` 访问前端**——刷新 Cookie 的 `SameSite=Lax`（`AuthController.java:40`）在 `127.0.0.1` 前端 + `localhost` 后端这种混合写法下会被浏览器判为跨站，表现为"能登录、一刷新就掉登录态"。

### 4.4 依赖的外部服务

| 服务 | 必需 | 说明 |
| --- | --- | --- |
| MySQL 8 | ✅ | 业务数据 |
| Redis | ✅ | **会话、限流、验证码、三方登录事务、WebSocket 票据全靠它**，没有 Redis 整个登录链路不可用 |
| 系统字体 | ✅（当 `captcha-enabled=true`） | 图形验证码用 Java2D 画字。精简容器没字体时 `CaptchaService` 会**拒绝启动**并给出处理方式（`CaptchaService.java:229-237`） |

---

## 5. 已封装的通用组件与钩子

这是"统一 UI 风格"的落点。**新建业务页面时优先复用这些，不要每个页面重写一遍表格。**

| 组件 | 路径 | 作用 | 被谁用 |
| --- | --- | --- | --- |
| **`SmartTable`** | `components/SmartTable/SmartTable.tsx` | ⭐ 最核心的封装。一个组件吃掉"查询表单 + 工具栏 + 分页表格 + 请求竞态 + 排序/筛选 + 导出" | 用户、角色、部门、登录日志、操作日志 5 个页面，以及它的 Demo |
| `FieldLabel` | `components/FieldLabel/` | 表单字段标签 + 问号 Tooltip 提示，统一"这个字段是什么意思"的表达 | 4 个 Dialog 表单 |
| `StatusTag` | `components/StatusTag/` | 状态色 Tag（success / warning / error / default） | 用户列表、首页 |
| `BaseChart` | `components/BaseChart/` | ECharts 容器（自动 resize、主题跟随） | 首页 |
| `SystemSettings` | `components/SystemSettings/` | **主题设置抽屉**（外观 / 品牌色含任意取色器 / 字体 / 字号 / 圆角 / 密度）—— 详见 `THEME.md` | `AppLayout` |
| `AiAssistant` | `components/ai/AiAssistant.tsx` | 右下角悬浮"AI 助手"面板 | `AppLayout`（**纯 UI 壳，没有接任何模型接口**） |
| `LoadingScreen` | `components/common/LoadingScreen.tsx` | 全屏加载态（与 `index.html` 首屏 loading 视觉一致） | `AuthGuard`、`DynamicPage`、`CallbackPage` |
| `AuthGuard` | `components/common/AuthGuard.tsx` | 受保护路由守卫：静默续期 → 拉 `/auth/me` → 写入 store | 路由表 |
| `Permission` / `usePermission` | `permission/Permission.tsx` | 按钮级权限：`<Permission code='system:user:add'>` 包裹即可 | 6 个系统管理页 |
| `useClipboard` / `useFullscreen` / `useResizeObserver` | `hooks/` | 剪贴板 / 全屏 / 元素尺寸 | 前两个只被**死代码** `CodeViewer` 用；`useResizeObserver` 被 `BaseChart` 用 |

### 已经是死代码的组件（没有任何页面在用）

`CodeViewer`（Monaco 代码查看器）、`CopyButton`、`FileUpload`、`SmartTableDemo`。
它们的 barrel 文件（`index.ts`）互相引用，所以 grep 会显示"有 import"，但**从路由出发走不到它们**。详见 `ASSESSMENT.md`。

---

## 6. 功能清单（按模块）

### 6.1 认证与账号

| 功能 | 前端 | 后端 |
| --- | --- | --- |
| 账号密码登录（记住我） | `pages/login/index.tsx` | `AuthController#login` → `AuthService#login` |
| 自助注册（图形验证码） | `pages/register/index.tsx` | `AuthController#register` → `RegistrationService#register` |
| 三方登录 | `pages/login` 社交按钮 + `pages/auth/CallbackPage.tsx` | `ExternalAuthController` → `ExternalLoginService` |
| 三方首次登录后"创建账号 / 绑定已有账号" | `pages/account/setup/index.tsx` | `OnboardingAccountController` |
| 静默续期（15 分钟令牌自动刷新） | `utils/request.ts` 拦截器 | `AuthController#refresh` |
| 退出登录 | `AppLayout` 下拉 | `AuthController#logout` |
| 首登 / 被重置后强制改密 | `AppLayout` 不可关闭的改密弹窗 | `PasswordChangePolicy` |
| 个人资料（手机/邮箱） | `pages/account/security` | `AccountProfileController` |
| 登录设备管理、踢下线 | `pages/account/security`、用户管理里"会话" | `AccountSessionController`、`UserController` |
| 第三方身份绑定 / 解绑 | `pages/account/security` | `AccountIdentityController` |
| 站内消息中心 | `pages/account/notifications`、顶栏铃铛 | `NotificationController` |
| 部门变更申请（样例业务流） | `pages/account/security` 内嵌 | `DepartmentChangeController` |

### 6.2 系统管理

| 功能 | 前端 | 后端 | 权限码 |
| --- | --- | --- | --- |
| 用户管理（增删改查、导出、重置密码、踢会话） | `pages/system/user/` | `UserController` / `UserService` | `system:user:{list,add,update,delete,export,reset-password}` |
| 角色管理（增删改查、菜单授权） | `pages/system/role/` | `RoleController` / `RoleService` | `system:role:{list,add,update,delete,grant}` |
| 部门管理（树形、移动、负责人） | `pages/system/dept/` | `DeptController` / `DeptService` | `system:dept:{list,add,update,delete}` |
| 菜单管理（目录/菜单/按钮、图标、隐藏） | `pages/system/menu/` | `MenuController` / `MenuService` | `system:menu:{list,add,update,delete}` |
| 登录日志（查询、删除） | `pages/system/loginlog/` | `LoginLogController` | `system:loginlog:{list,delete}` |
| 操作日志（查询、删除，AOP 自动记录） | `pages/system/operatelog/` | `OperationLogController` + `OperationLogAspect` | `system:operatelog:{list,delete}` |

**菜单管理 + 重置密码是"平台级权限"**（`AccessPolicy.PLATFORM_ONLY_PERMISSION_CODES`，`AccessPolicy.java:191-197`）。它们不会出现在任何角色的授权树里，也授不出去——因为它们的后端还有一层 `requireSuperAdmin` 硬校验，授出去只会造出"侧边栏有入口、点进去全是 403"的死页面。

---

## 7. 完成度评估（快速版）

| 维度 | 评分 | 说明 |
| --- | --- | --- |
| 认证与鉴权 | **A** | 设计相当扎实：服务端会话 + 短寿命 JWT、双层登录限流、来源限流、可信代理 IP、一次性验证码、强制改密、越权防护完整 |
| 权限模型 | **A-** | RBAC + 数据范围 + "不能管理同级/更高权限"规则清晰且集中；缺"自定义部门范围" |
| 后端代码质量 | **B+** | 分层清晰、注释极其详尽（几乎每处取舍都写了原因）、边界情况处理到位；但注释量过大、单类偏大、缺集成测试、测试还被删了一批 |
| 前端代码质量 | **B-** | 动态路由设计优雅、`SmartTable` 封装到位；但首页是假数据、有死代码组件、`index.html` 有乱码、无 ESLint、巨型单文件 |
| 数据库 | **B** | 表结构规范（逻辑删除 + 审计字段 + 索引齐全）；但没有迁移工具、没有升级脚本 |
| 业务功能 | **D** | 只有"部门变更审批流"一个样例模块。这正是留给你的空间 |
| 可移植性（搬去内网） | **B+** | 配置全部外置到 `lbl.*` + 环境变量，同源部署开箱可用；品牌硬编码需要批量清理（清单已给） |
| 测试 | **D+** | 7 个测试类 / 约 440 行；**且 `AccessPolicyTest`、`TrustedProxyResolverTest`、`UserServiceSecurityTest` 三个共 507 行测试已被删除**（见 `ASSESSMENT.md`） |

**一句话结论**：**"骨架"已经很硬，值得继续在这个地基上盖楼；但"装修"和"验收"没做完**——首页假数据、死代码、无迁移脚本、测试倒退，这几项要在搬去内网前处理掉。

详细的问题清单、漏洞分析、未完成项和优先级路线图，见 `ASSESSMENT.md`。
