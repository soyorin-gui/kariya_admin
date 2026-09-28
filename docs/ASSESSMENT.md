# 现状评估：代码质量、冗余、漏洞、不合理之处、未完成项与路线图

> 这一篇回答："**这个系统现在开发到什么程度、代码质量如何、有没有冗余代码、有什么漏洞、还有什么没做完。**"
>
> 结论全部基于对源码的逐文件阅读，每条都给了**文件:行号**，可以自己核对。
> 阅读顺序建议：先看第 0 节的总体判断，再看第 4 节的漏洞（**最需要你行动**），最后看第 7 节的路线图。

---

## 0. 总体判断

### 一句话总结

> **"地基打得很硬，但装修和验收没做完。"**
>
> 认证/鉴权/权限这一层是我见过在个人项目里少见的扎实程度（服务端会话 + 短寿命 JWT、双层登录限流、可信代理 IP、越权防护体系完整、几乎所有取舍都写了原因）。
> 但**首页是假数据、有一批死代码、没有数据库迁移方案、测试还被人删掉了 507 行**，而业务功能几乎为零。
> **搬去内网之前，必须处理掉第 4 节的安全项和第 3 节的冗余。**

### 分维度评分

| 维度 | 评分 | 依据 |
| --- | --- | --- |
| **认证与鉴权设计** | **A** | 服务端会话 + 短寿命 JWT 的组合用对了；双层登录限流（账号+来源 5 次 / 来源 20 次）；限流 key 忽略大小写且不存 IP 原文；可信代理 IP 解析是对的算法（从右往左找第一个不可信地址）；验证码服务端出题 + 一次性消费；敏感操作后 `authVersion++` + 清会话；401/403 语义严格区分 |
| **权限模型** | **A-** | `AccessPolicy` 集中了唯一一套判定，含"不能管理同级/更高权限""不能授予自己没有的菜单""不能创建与自身同级的角色""平台级权限不可授出"；数据范围 4 档 + `maxScopeRank` 比较。**扣分**：缺"自定义部门范围"（`sys_role_dept` 不存在，不是闲置）；`Permission` 组件能力单薄 |
| **后端代码质量** | **B+** | 分层清晰（Controller 只接参数 › Service 管业务+事务 › Mapper 管 SQL）；注释极其详尽；事务边界有意设计（审计 `REQUIRES_NEW`、网络 IO 不进事务、成功日志在提交后）；N+1 已被刻意消除。**扣分**：注释量约为代码量的一半，单类偏大（`UserService` 517 行 / `ExternalLoginService` 432 行 / `AccessPolicy` 387 行）；测试只剩 441 行且被删过 507 行 |
| **前端代码质量** | **B-** | 动态路由设计优雅（`import.meta.glob` 构建期扫描 + 组件缓存避免 remount）；`SmartTable` 封装到位（含请求竞态处理）；`tsc --noEmit` 纳入 build；**全项目零 `any`、零 `@ts-ignore`、零 TODO**。**扣分**：首页全是假数据；一批死代码组件；5 个 Dialog 是同一模式的 5 份拷贝；两个日志页 80% 重复；`index.html` 中文乱码；没有 ESLint；巨型单文件 `AppLayout.tsx` 264 行承载 6 个关注点 |
| **数据库设计** | **B** | 13 张表结构规范（逻辑删除 + 审计四件套 + `builtin` + `status` + 索引齐全）；大量查询用递归 CTE / 物化路径；唯一索引与逻辑删除的冲突被明确识别并统一处理。**扣分**：**没有迁移工具、没有升级脚本**（4 个旧脚本只留在 `target/` 里）；三处唯一约束与逻辑删除的交互靠"每个 Service 自己记得用含已删除口径"来保证 |
| **业务功能** | **D** | 只有"部门变更审批流"一个样例模块（`departmentchange/`，4 张表，两步审批状态机，乐观锁）。**这正是留给你的空间，不是缺陷** |
| **可移植性（搬去内网）** | **B+** | 配置全部外置到 `lbl.*` + 环境变量；`application-prod.yml` 全走 `${ENV}`；前端 `VITE_API_BASE_URL=/api` 天生适合同源部署。**扣分**：品牌硬编码散落 30+ 处（清单已给）；`jwt-secret` 硬编码且生产未覆盖 |
| **测试** | **D+** | 7 个测试类 / 约 441 行。`AccessPolicy`（唯一权限判定处）、`TrustedProxyResolver`、`UserService` 的安全测试共 507 行**已被删除** |
| **运维友好度** | **B** | 有日志保留策略（分批物理删除、有上限、低峰执行）、有 `SystemPermissionInitializer` 幂等补建菜单、有健康降级（`TrustedProxyResolver` 配错只告警不阻断启动）、启动时报处配置问题。**扣分**：没有任何健康检查端点（`/actuator/health` 未引入） |

### 与"我想拿它开业务"的关系

| 你想要的 | 现在能不能做 | 需要做什么 |
| --- | --- | --- |
| 下班后加兴趣功能 | ✅ **完全可以** | 按 `DEVELOPMENT.md` 路径 A，5 分钟加一个页面 |
| 加带权限的完整业务模块 | ✅ **可以** | 按 `DEVELOPMENT.md` 路径 B，模板代码都给了 |
| 搬去公司内网当工作系统 | ⚠️ **可以，但必须先做安全整改** | 见 `INTRANET-MIGRATION.md` 第 ① 节（4 项，都在"一改就完"级别） |
| 内网免密登录 | ⚠️ 接入骨架已完成 | 仍需公司 UIAS SDK 与 ESF 适配器；见 `UIAS-INTEGRATION.md` |
| 按角色指定部门范围 | ❌ 没有 | 完整方案见 `DATA-SCOPE-ROLE-DEPT.md` |

---

## 1. 代码质量详评

### 1.1 做得好的地方（**这些是真正的资产，别改坏**）

#### ① 把"权限判定"收敛成唯一一处

`security/context/AccessPolicy.java` 是全项目**唯一**回答"这个人能不能干这件事"的地方。这带来的直接好处是：不可能出现"列表页认为能删、详情页认为不能删"这类分裂。

而且它把"数据范围"抽象成了一个 `Set<Long> departments`，所以 `sys_role_dept` 那种扩展**只需改一处**（见 `DATA-SCOPE-ROLE-DEPT.md` 第 3.4 节）。

同类做法还有：
- `DeptPaths` —— 部门物化路径匹配的唯一口径（三处用它：数据范围展开、移动时重写路径、环检测）；
- `PasswordRules` —— 新密码规则的唯一来源（连正则都是从常量拼出来的，`PasswordRulesTest` 用边界密码钉住了"常量与正则一致"）；
- `AccessPolicy.PLATFORM_ONLY_PERMISSION_CODES` —— 平台级权限码的唯一定义处；
- `LoginAttemptGuard` —— 所有认证入口滥用控制的唯一实现处。

**这个项目最值得学的一点就是"把知识集中到一处"。** 注释里甚至明确记录了"`system:user:export` 的补建因为没有单一真相来源，在三份种子逻辑之间漂移过"。

#### ② 事务边界是"想过的"，不是随手加的

三处刻意设计，都写在类注释里：

| 设计 | 位置 | 原因 |
| --- | --- | --- |
| 审计日志用 `REQUIRES_NEW` 且内部吞异常 | `LoginLogService.java:46`、`OperationLogService.java:45` | 业务失败会回滚，但"谁尝试做了什么"必须留下；审计失败不能让用户的操作跟着失败 |
| 网络 IO **绝不**在事务里 | `ExternalLoginPersistence` 类注释 | 两次外部请求各 15 秒超时，包在事务里会占死 Hikari 连接（池只有 10），几个并发就能让**全站**请求集体失败。为此把它拆成"网络阶段"和"落库阶段"两个类，并且让落库类**刻意不持有 HttpClient**——把约束变成结构上的事实 |
| 成功日志写在事务提交之后 | `ExternalLoginService.java:180-184` | ① 避免一个请求同时占两条连接；② 避免成功日志被 catch 误判成失败 |

#### ③ 安全细节处理到了"安静失败"层面

这类细节最能反映作者踩过坑：

| 处理 | 位置 | 不这么做会怎样 |
| --- | --- | --- |
| 限流 key 忽略大小写 + 截断到 64 字符 | `LoginAttemptGuard.java:64-68` | MySQL 用户名比较不区分大小写，key 区分大小写则交替用 `Admin`/`admin` 能把计数拆成两份；不截断则可用超长用户名撑大 Redis key |
| 成功登录要清**来源级**计数 | `LoginAttemptGuard.java:102-112` | 办公网共享出口 NAT，一个同事输错 20 次就把整个出口锁 15 分钟 |
| CIDR 用字节前缀匹配而不是字符串前缀 | `TrustedProxyResolver.java:164-168` | `203.0.113.7` 与 `203.0.113.70` 字符串前缀相同但网络不同 |
| IPv4-mapped IPv6 要还原成 4 字节 | `TrustedProxyResolver.java:214-228` | 双栈主机上配置的 `10.0.0.1` 规则会因"16 字节 vs 4 字节"匹配失败，表现为"可信代理配了却完全不生效" |
| 验证码失败后无论对错都 `getAndDelete` | `CaptchaService.java:127` | 允许对同一 id 反复提交 → 答案空间只有 30⁴，脚本几分钟撞完 |
| 验证码字符表去掉 `M`/`W` | `CaptchaService.java:58-70` | 字符逐个随机旋转会撑宽包围盒，连续四个 `W` 在像素上粘连成一块（实测 blocks=2，应为 4），用户根本数不出来 |
| 启动时探测"真的能把字画出来" | `CaptchaService.java:229-237` | 精简容器无字体 → 生成纯色空白图 → 注册永远失败而**后端不报任何错**。所以主动探测并拒绝启动，且 catch `Throwable`（无图形环境下会抛 `AWTError`，属于 `Error` 不是 `Exception`） |
| 校验提示优先用手写的 `message`，绝不无条件采用框架解析的 | `GlobalExceptionHandler.java:160-232` | Bean Validation 的**内置**提示会按 JVM 语言本地化，而 `@Pattern` 的内置提示会把**正则原样**插值进去——中文环境下会看到「需要匹配 ^(?=.*[a-z])…」 |
| GET 换 token 时日志里不打完整 URI | `ExternalLoginService.java:248-249` | 微信的 `client_secret` 就在查询串里，URI 落盘等于把密钥写进日志 |
| 内部异常不写进 302 的 Location | `ExternalAuthController.java:88-95` | 表名/列名/SQL 片段会出现在浏览器地址栏、Referer 头、网关访问日志三处我们控制不了的地方 |
| 401 与 403 严格分开 | `WebSecurityConfig` + `GlobalExceptionHandler` | 前端逻辑依赖它：401 触发静默续期，403 直接展示。混了会导致"权限不足被反复续期"或"登录失效却被当权限问题" |

#### ④ N+1 被刻意消除

至少 4 处：

| 位置 | 做法 |
| --- | --- |
| `UserService.toListViews` | 批量取角色（`selectAssignedByUserIds`）、批量取部门名（`selectBatchIds`）、批量取权限码（`selectPermissionCodesByUserIds`） |
| `MenuMapper.selectPermissionCodesByUserIds` | 一次递归 CTE 算一批用户的有效权限 |
| `DeptService.leaderNames` | 批量取负责人姓名（注释明确写了"以前是每条部门各查一次用户表（N+1）"） |
| `RoleService.list` | 批量取角色权限与用户数 |

**⚠️ 唯一的缺口**：`UserService.create/update` 里对 `request.roleIds()` 是**循环单查**（`UserService.java:413-422` 的 `for (Long roleId : request.roleIds()) { roles.selectById(roleId); ... }`）。角色数通常个位数，影响很小，但它是这个文件里唯一没做批量装配的地方。

#### ⑤ 唯一索引 × 逻辑删除的冲突被系统性处理

这是"静默失败"的经典来源。项目识别了它并建立了统一约定：**带唯一索引 + 逻辑删除的字段，查重必须用"含已删除"口径**。有 5 个专用 Mapper 方法，且每处都写了为什么：

| 方法 | 表 |
| --- | --- |
| `UserMapper.countIncludingDeletedByUsername` | `sys_user.username` |
| `RoleMapper.countIncludingDeletedByRoleCode` | `sys_role.role_code` |
| `DeptMapper.countIncludingDeletedByDeptCode` | `sys_dept.dept_code` |
| `MenuMapper.countIncludingDeletedByRoutePath` | `sys_menu.route_path` |
| `MenuMapper.countIncludingDeletedByPermissionCode` | `sys_menu.permission_code` |

**这是新增业务表时必须照抄的约定。**

#### ⑥ 前端的动态路由

`router/componentRegistry.tsx` 用 Vite 的 `import.meta.glob` 在**构建期**扫描 `src/pages/**`，把菜单里的 `component` 字符串解析成懒加载组件。三个细节都做对了：

1. **解析结果必须缓存**（`resolved` Map）——`React.lazy()` 每次调用返回**全新**组件类型，每次渲染重新 lazy 会让 React 卸载重建，表现为"页面反复刷新、接口被重复请求"；
2. **解析失败渲染诊断页而不是 404**（`PageUnavailable` + `expectedPageFile`）——直接告诉你该建哪个文件；
3. **`availableComponents()` 喂给菜单表单的下拉** —— 所以**配不出一个渲染不出来的菜单**。

配套的 `router/menu.ts` 里，`firstAvailablePath` 返回 `null` 而不回退 `/home` 的决定也很关键（否则"没被授予首页的账号"会被困在 403 页循环里出不来），`resolveRedirect` 用 `URL` 解析而不是正则来防开放重定向。

### 1.2 做得不好的地方

#### ① 注释量失衡 —— 最突出的可维护性问题

后端 7,286 行代码里，**注释大约占了三分之一到一半**。而且很多是"事故复盘"式的长段落，例如：

- `AccessPolicy.java:210-226` —— 17 行注释解释"为什么空壳判定不能引入递归条件"；
- `SessionService.java:63-70` —— 8 行注释解释"为什么开户态 TTL 不随 rememberMe 变化"；
- `CaptchaService.java:58-70` —— 13 行注释解释"为什么去掉 M 和 W 这两个字形"；
- `GlobalExceptionHandler.java:160-180` —— 21 行注释解释"为什么不能无条件采用框架解析的提示"。

**这些内容本身非常有价值**（它们记录了真实踩过的坑），但**放错了地方**：
- 它们让代码文件的信噪比变差，读一个 30 行的方法要滑过 60 行注释；
- 同一个知识点在多个文件里重复（例如"逻辑删除与唯一索引"的说明在 5 个 Mapper 里各写了一遍；"平台级权限"在 `AccessPolicy`、`RoleService`、`UserService` 里各解释一遍）；
- 有些注释已经**和代码脱节**：`types/log.ts:1` 写的是 `org.LBL.system.log.LogResult`，而实际包名是 `org.lbl`（大小写错了）；`application-local.yml:34` 的 `org.kariya: DEBUG` 指向一个**不存在的包**。

**建议**：把"设计决策与事故复盘"移到 `docs/decisions/` 下（本文档集就是第一步），代码里只留一两行指向它的引用。**这件事不急，但如果你要让别人读这份代码，它比补功能更值钱。**

#### ② 单类偏大

| 文件 | 行数 | 承载的关注点 |
| --- | --- | --- |
| `system/user/service/UserService.java` | 517 | CRUD + 导出 + 改密 + 会话管理 + 表单候选 + 权限校验 + 装配 |
| `auth/external/ExternalLoginService.java` | 432 | 发起授权 + 回调 + 换 token + userinfo + 错误识别 + 日志脱敏 + 状态管理 |
| `security/context/AccessPolicy.java` | 387 | 操作者画像 + 用户/角色/部门三类判定 + 平台级权限过滤 + 权限档位 |
| `layouts/AppLayout.tsx`（前端） | 264 | 侧边栏构建 + 顶栏 + 面包屑 + 通知下拉 + 改密弹窗 + 用户菜单 + 会话活动监控 + WebSocket + 主题抽屉触发 |

`AppLayout.tsx` 是最该拆的：264 行里塞了 6 个可以独立成组件的关注点。

#### ③ 前端有 4 处"模板代码复制"

| 重复 | 规模 | 位置 |
| --- | --- | --- |
| **5 个 Dialog 是同一模式的 5 份拷贝** | `UserDialog`(219) / `MenuDialog`(176) / `DeptDialog`(155) / `RoleDialog`(117) / `RolePermissionDialog`(67) | 每个都重复：`submittingRef`+`setSubmitting` 双重防重复提交（**连解释注释都一字不差地复制了**：`RoleDialog.tsx:54-56`、`DeptDialog.tsx:71-73`、`MenuDialog.tsx:68-70`）、`useLayoutEffect` 的 reset-and-load 块、14 个 Modal 属性的相同组合、`dialog-loading` 的相同 JSX、相同的"是否为表单校验错误"判断 |
| **两个日志页 80% 相同** | `loginlog/index.tsx` 和 `operatelog/index.tsx` 各 97 行 | `remove()` 函数除调用的 API 外**逐行等价**；`TIME_FORMAT`、`currentRecordCount`、`selectedIds`、`rowSelection`、删除 `Popconfirm` 文案、`onDataLoaded` 全部相同。**它们的 CSS 文件字节完全相同** |
| **`toTree` 实现了 5 次** | — | `dept/index.tsx:16-23`、`menu/index.tsx:20-27`、`DeptDialog.tsx:16-24`、`RolePermissionDialog.tsx:18-24`、`UserDialog.tsx:20-34`。`blockedIds`（收集自身+后代）也重复了 2 次 |
| **时间格式化内联了 9 次** | — | `value?.replace('T', ' ')` 出现在 `user/index.tsx:58,182`、`loginlog:56`、`operatelog:55`、`security:145`、`notifications:59`、`AppLayout:120`、`role:44`、`dept:63` |

**最值得做的抽取**：`useFormDialog`（一个 hook 吃掉防重复提交 + 加载 + 错误处理 + Modal 属性）和 `utils/tree.ts`。这两个改动能消掉 300+ 行重复代码，且风险很低。

#### ④ 判空/兜底的写法不统一

前端有 4 种错误处理风格并存：
1. `message.error(getApiErrorMessage(error, '具体文案'))` —— 主流
2. `getApiErrorMessage(error)` 无兜底 —— 用户看到通用文案（`user/index.tsx:94,115`、`role:73`、`dept:87`）
3. 静默吞掉 —— `login:34`（provider 列表失败→空数组，表现为"这个部署没有 SSO"）、`notifications:31`、`UserActivityManager:9`、`RealtimeClient:27,31`
4. 只有一处在用 thunk 的 `unwrap()` 传播错误 —— `notifications/index.tsx:37`

而且判断"是不是表单校验错误"有两个相反的写法：`if (!(error as {...}).errorFields)` 和 `if ((error as {...}).errorFields) return;`。

**没有全局 ErrorBoundary**（全项目搜不到 `componentDidCatch`）。页面渲染期抛异常会**整树白屏**，没有任何恢复 UI。

#### ⑤ `AppLayout` 里有一个"看起来能搜但什么都没做"的输入框

`AppLayout.tsx:204`：
```tsx
<Input className='quick-search' prefix={<SearchOutlined />} placeholder='搜索功能、文档或快捷操作...' />
```
**没有 `onChange`、没有 `onPressEnter`、没有状态。** 用户会在里面输入然后困惑。**要么实现，要么删掉。**

#### ⑥ ~~`index.html` 的中文是乱码~~ —— **已修复 ✅**

> **状态更新**：这一项在本次审计后已被修复。当时的症状是 `frontend/index.html` 里两处中文显示为乱码
> （`aria-label="搴旂敤鍔犺浇涓?` 本应是「应用加载中」，`灏戝コ绁堢シ涓?..` 本应是「少女祈祷中...」）。
> 现已核对**字节级**确认干净：`aria-label` 的字节是 `E5 BA 94 E7 94 A8 E5 8A A0 E8 BD BD E4 B8 AD`，
> 即合法的 UTF-8「应用加载中」。全仓 264 个文本文件用"半角片假名 + 锟/拷/鈥"双判据扫描，**零残留**。

**⚠️ 但 `frontend/dist/` 里仍是修复前的旧产物**（`dist/index.html` 里还能看到乱码、`dist/assets/` 里还有
重命名前的 `backgroud-*.png`）。**源码修好 ≠ 产物修好，记得重新 `npm run build`。**

**成因记录（避免再犯）**：把一个 UTF-8 文件用 GBK（cp936）解码再存回，就会产生这种乱码 ——
UTF-8 的「应用加载中」是 15 个字节，按 GBK 双字节切分后变成「搴旂敤鍔犺浇涓」+ 1 个落单字节，
落单字节无法映射就变成 `?`（所以乱码末尾总有个 `?`）。
**判别特征**：GBK 会把这些字节解成**半角片假名**（`シ` `コ` 等），而正常中文文本里绝不会有半角片假名 —— 这是最快的乱码识别法。
**预防**：编辑器统一保存为 UTF-8（不要 UTF-8 with BOM / GBK），`.editorconfig` 里显式声明 `charset = utf-8`。

#### ⑦ 数据库没有迁移方案（**这是最严重的工程问题**）

- `pom.xml` 里**没有 Flyway / Liquibase**；
- `db/` 目录下只有 `init_schema.sql` + `init_data.sql`，两者头部都写着"仅面向空数据库"；
- **源码里原本有的升级脚本已被删除**（`git status` 显示 `D backend/src/main/resources/db/upgrade__schema.sql`）；
- 它们现在只藏在**已经打好的 jar** 里：`backend/target/lbl-shit-1.0.0.jar` → `BOOT-INF/classes/db/`（5 个文件，UTF-8 编码，内容完好）。
  取回方式见 `INTRANET-MIGRATION.md` 第 5.2 节。
- ⚠️ 另有一个历史线索：**最早的提交 `64d5678` 用的是 Flyway 风格的目录**（`db/migration/V1__schema.sql` ~ `V4__reset_builtin_admin_password.sql`），
  后来被改成了 `init__*.sql` + `upgrade__*.sql` 的手工方案。也就是说**这个项目曾经有过迁移体系，后来被拆掉了**。

**后果非常具体**：你现在想加 `sys_role_dept`（`DATA-SCOPE-ROLE-DEPT.md`），**没有任何"老库怎么升级"的现成机制**。你只能手写 SQL、手动在每个环境执行、并且在某一天发现"开发库改了但测试库忘了"。

**这是我认为最该优先补的工程债**（优先级 P1）。具体建议见 `INTRANET-MIGRATION.md` 第 5.4 节。

#### ⑧ 测试倒退（**第二严重的工程问题**）

`git status` 显示三个测试被**删除**（`D ` 状态，即已暂存的删除）：

| 文件 | 行数 | 覆盖什么 |
| --- | --- | --- |
| `security/context/AccessPolicyTest.java` | **246** | ⚠️ `AccessPolicy` —— **全项目唯一的权限判定处** |
| `security/proxy/TrustedProxyResolverTest.java` | **123** | ⚠️ 真实 IP 解析 —— 审计证据 + 限流 key 的来源 |
| `system/user/UserServiceSecurityTest.java` | **138** | ⚠️ 用户管理的越权防护 |

**共 507 行，而且删掉的恰好是三个"出错等于被入侵"的模块。** 剩下的 441 行测试覆盖的是：密码规则、密码策略、校验文案、文件暂存、Jasypt、批量 SQL 的 `foreach` 展开。

**恢复成本极低**（它们是纯 Mockito 单测，不依赖数据库）：
```bash
git checkout HEAD -- backend/src/test/java/org/lbl/security/context/AccessPolicyTest.java
git checkout HEAD -- backend/src/test/java/org/lbl/security/proxy/TrustedProxyResolverTest.java
git checkout HEAD -- backend/src/test/java/org/lbl/system/user/UserServiceSecurityTest.java
```
**而且你接下来要改 `AccessPolicy`（加 CUSTOM）** —— 这是恢复它们的最佳时机（`DATA-SCOPE-ROLE-DEPT.md` 第 3.9 节给了 3 个新增用例）。

##### ⭐ 补充发现：这三个测试**此前即使恢复也跑不起来**（已在本次修复 ✅）

清理过程中跑了一次完整测试，发现 `mvn test` 一直是 **BUILD FAILURE**，4 个错误集中在两个测试类上：

```
Could not initialize plugin: interface org.mockito.plugins.MockMaker (alternate: null)
```

**根因与业务代码毫无关系**：Mockito 5 默认使用 inline mock maker，它要通过 byte-buddy-agent 把自身作为 Java agent 附加到当前 JVM；而 **JDK 从 9 起把 `jdk.attach.allowAttachSelf` 的默认值改成了 `false`**，受限环境（沙箱/容器/部分 CI）里这一步会失败。

**症状极具误导性**：**所有用到 Mockito 的测试类整体报 Errors，而不用的照常通过** —— 很容易被误判成"测试写错了"。

**这不是本次改动引入的**：`backend/target/surefire-reports/*.dumpstream` 里从 **2026-09-24 一直到 09-27 16:36** 全是同一条报错，而本次改动发生在 21:5x。

**修法**（已写进 `backend/pom.xml`，并附了完整注释）：
```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-surefire-plugin</artifactId>
  <configuration>
    <argLine>-Djdk.attach.allowAttachSelf=true</argLine>
  </configuration>
</plugin>
```

修完的效果：**`Tests run: 25, Failures: 0, Errors: 0` / BUILD SUCCESS**（修之前是 21 过 4 错）。
**并且现在恢复上面那三个测试类它们才真的能跑** —— 这也是为什么这条修复是恢复测试的前置条件。

#### ⑨ 前后端密码规则有字面量副本

`frontend/src/utils/passwordPolicy.ts` 是 `backend/.../PasswordRules.java` 的手抄副本（前端无法 import Java）。文件顶部有提醒注释，但**没有任何机制保证两边一致**——改一边忘一边，用户会看到"前端说通过、后端说格式错误"。

**可行的缓解**：后端加一个 `GET /api/auth/password-policy` 返回正则和提示文案，前端启动时拉取（或者干脆在构建时从一个 JSON 生成两边）。当前规模下"加一个测试断言两边字面量一致"也行，但前端没有测试框架。

### 1.3 一些"轻微但不该忽略"的不一致

| 问题 | 位置 | 影响 |
| --- | --- | --- |
| ~~**`--text-strong` CSS 变量从未定义**~~ **已修 ✅** | 原用于 `pages/account/security/security.css:18` | 已改为 `var(--ink)`（原本想表达的"强调文字色"）。同时把该文件的 `#edf4ff` 图标底色改成 `color-mix(in srgb, var(--brand) 12%, var(--surface))`，让它在换品牌色与深色主题下都正确 |
| **`.form-grid` 是死 CSS** | `pages/system/shared.css:19-23,207-209` | 文件里自己注明了"当前四个弹窗都改成了…所以暂时没有使用者" |
| ~~**`#nprogress .bar` 是死 CSS**~~ **已修 ✅** | `styles/global.css` | `nprogress` 已真正接入（见 `ARCHITECTURE.md` "顶部进度条"），并从只覆盖 `.bar` 扩展到同时覆盖 `.peg`（末端光晕），否则会出现"品牌色的线 + 蓝紫色拖尾" |
| `.password-note` 系列是死 CSS | `pages/system/user/index.css:4-18` | 全项目搜不到 `password-note` 类名 |
| `menuBreadcrumb` 一行写了两条语句 | `router/menu.ts:87` | 纯格式问题（Prettier `printWidth: 200` 的副作用） |
| ~~首页说"React 19"~~ **已修 ✅** | `pages/home/index.tsx` | 已改为"React 18"（与 `package.json` 一致），并加了注释提醒升级时同步 |
| `MenuOutlined` 未使用的 import | `pages/system/menu/index.tsx:4` | `noUnusedLocals` 未开启，所以没被拦住 |
| 一条孤立的 eslint 指令 | `pages/register/index.tsx:89` 的 `// eslint-disable-next-line react-hooks/exhaustive-deps` | **项目里没装 ESLint**，这条指令是惰性的 |
| ~~43 处写死字号 / 99 处写死颜色~~ **主体已修 ✅** | 全项目 CSS | 字号已全部换成 `--fs-*` 阶梯；品牌色/语义色已换成变量。**有意保留不跟主题的**：登录页那套浅色设计、404 页的视口驱动大字、第三方平台品牌色。详见 `THEME.md` 第七节 |
| `.prettierrc.json` 是孤儿配置 | `frontend/.prettierrc.json`（`printWidth: 200`） | `package.json` 里既没有 `prettier` 也没有 `eslint` 依赖，`scripts` 里也没有 `format`/`lint`。**这份配置解释了为什么好几个文件有 200 字符的超长单行** |
| `frontend/dist/` 提交进了工作区 | — | 容易把旧产物发上线。`.gitignore` 里有 `dist/`，但工作区里真实存在 |
| `backend/target/lbl-shit-1.0.0.jar` 是唯一的升级脚本副本 | — | ⚠️ **别删这个 jar**。源码树里的 `upgrade__*.sql` 已被删除，`target/classes/db/` 的解压副本也已被清理，只剩 jar 里这一份。捞回命令见 `INTRANET-MIGRATION.md` 5.2 |

---

## 2. 后端冗余清单（可安全删除）

每条都验证过引用数（`grep` 全仓库，含测试）。

### 2.1 完全没用到的生产代码 —— **已在本次清理中全部处理 ✅**

处理结果（**预设接口已删除、纯残留已删除**；删除的源码原文归档在 `ARCHIVE-removed-code.md`，因为其中 3 个文件从未进过 git）：

| # | 位置 | 引用数 | 处理 |
| --- | --- | --- | --- |
| 1 | `auth/external/SamlIdentityVerifier.java` | **1**（只有自己的声明） | ❌ **已删除**（按使用者要求清除预留接口） |
| 2 | `auth/external/EnterpriseDirectoryPort.java` | **2**（接口 + 自己的空实现） | ❌ **已删除** |
| 3 | `auth/external/NoopEnterpriseDirectoryAdapter.java` | 同上 | ❌ **已删除** |
| 4 | `SessionService.refreshToken()` | **1**（只有声明） | ❌ **已删除** |
| 5 | `SessionService.create(...)` 四参重载 | 调用者 0 | ❌ **已删除** |
| 6 | `UserRoleMapper.selectRoleIds(userId)` | **1**（只有声明） | ❌ **已删除** |
| 7 | `LoginAttemptGuard.requireSourceQuota(bucket, source, …)` 四参重载 | 只有内部调用 | ❌ **已删除**（合并进三参方法） |
| 8 | `PasswordChangePolicy.appliesToPasswordLogin()` | **1**（只有声明） | ❌ **已删除**，并把它背后的知识写进了 `requiresChange` 的 javadoc |

> **关于 #8 的追加结论**：我原先建议"接上它"或"删掉它"二选一。实际动手时确认了**它无法接上** ——
> 4 个判定点里有 2 个（`/auth/me` 与 `JwtAuthenticationFilter`）必须读**会话快照**
> `LoginSession.passwordChangeRequired()` 而不是重新查凭据（否则用户操作到一半会突然掉权限，
> 而且过滤器在每个请求上跑、读库会拖慢整条链路）。所以这个方法**永远不可能成为单一入口**，
> 保留它只会给人"改这里就能改全局"的错误印象。现在规则与 4 个调用点都写在 `requiresChange` 的 javadoc 里。

> **保留未删的 SAML 相关代码**：`ExternalLoginService` 里仍有两处对 `protocol: SAML` 的运行时拦截
> （`isProviderUsable` 返回 false、`begin` 抛"系统不支持 SAML 协议登录"）。它们判定的是**配置字符串**，
> 与那个被删的接口无关，是"即使有人把某个 provider 改成 SAML 并启用，也不会走进没有实现的路径"的安全网。
> `application-local.yml` 里 `enabled: false` 的 `uias` provider 也保留了，它让登录页的提供方列表稳定。

### 2.2 前端死代码 —— **使用者决定全部保留 ✅**

| 位置 | 结论 |
| --- | --- |
| `components/CodeViewer/`（Monaco 代码查看器） | **保留** —— 是使用者主动封装的预留组件，计划后续使用 |
| `components/CopyButton/` | **保留**（被 CodeViewer 使用） |
| `components/FileUpload/` + `api/file.ts` + `types/file.ts` | **保留** —— 后端 `/api/files/staged` 是完整可用的，这是一个"没接完的功能"而不是死代码 |
| `hooks/useClipboard.ts` / `hooks/useFullscreen.ts` | **保留**（被上面两个组件使用） |
| `components/SmartTable/SmartTableDemo.tsx` | **保留**（但它有 2 个真实缺陷：位置在 `components/` 下所以 `import.meta.glob('../pages/**')` 扫不到；且是命名导出而 `lazy()` 要 default 导出 —— 想让它变成真能打开的演示页，需要挪到 `src/pages/` 并改成默认导出） |
| `permission/Permission.tsx` 里的 `usePermission` | **保留**（导出了但只在本文件内部使用） |
| `theme/index.ts` / `theme/types.ts` | **保留并顺手修好了**：`theme/index.ts` 原先 re-export 一个已不存在的 `themeTokens`，现已更新为导出新的令牌；`theme/types.ts` 的 `ThemeTokens` 也已改名并对齐 |
| 未使用的 npm 依赖 | `classnames` **使用者会自行删除**；`@antv/g6` / `html2canvas` / `nprogress` 见下 |

> **`nprogress` 已从"零引用依赖"变成"真正在用" ✅** —— 使用者要求引入并使用，本次实现了
> `services/progress.ts`（延迟 300ms + 并发计数）+ axios 拦截器接入 + 懒加载 chunk 接入 + 品牌色适配。
> 详见 `ARCHITECTURE.md` 的"顶部进度条"一节。

### 2.3 ⚠️ 一个"单一真相来源没被使用"的问题（**比死代码更值得处理**）

`PasswordChangePolicy.appliesToPasswordLogin()`（`PasswordChangePolicy.java:64-66`）声明了"这次会话是否该被拦在系统外直到改完密码"，而且类注释里明确写了：

> *"调用方必须自己再与'登录方式'求与，见 `appliesToPasswordLogin`。分开写是刻意的。"*

**但它从来没被调用过。** 4 个调用点全部**自己内联了同样的判断**：

| 位置 | 代码 |
| --- | --- |
| `AuthService.java:111`（refresh 里） | `if ("PASSWORD".equals(s.authMethod()))` |
| `AuthService.java:132`（`/auth/me` 里） | `boolean pendingPasswordChange = "PASSWORD".equals(s.authMethod()) && s.passwordChangeRequired();` |
| `JwtAuthenticationFilter.java:63` | `boolean pendingPasswordChange = "PASSWORD".equals(s.authMethod()) && s.passwordChangeRequired();` |
| `RegistrationService.java:196` | `boolean mustChange = "PASSWORD".equals(method) && passwordChangePolicy.requiresChange(credential);` |

**这正是"知识集中在一处"原则被违反的情形**：方法存在，注释指向它，但没人用。如果将来要改规则（例如"管理员重置过的密码，无论怎么登录都强制改"），改 `appliesToPasswordLogin` 不会生效——**必须记住要改 4 个地方**。

**修法**（低成本、低风险）：把这 4 处改成调用 `passwordChangePolicy.appliesToPasswordLogin(authMethod, credential)`。
⚠️ 注意 `AuthService.java:111` 那一处拿的是**会话快照**（`s.passwordChangeRequired()`）而不是重新查凭据，这是刻意的（注释说"避免业务请求途中突然丢权限"）。所以要么给 `appliesToPasswordLogin` 加一个接受 `boolean` 快照的重载，要么那一处保持内联并加注释说明为什么。

### 2.4 未使用的 npm 依赖

| 依赖 | 版本 | 状态 |
| --- | --- | --- |
| `@antv/g6` | `^5.0.49` | **`src` 里零引用**（这是个图可视化库，体积不小） |
| `classnames` | `^2.5.1` | **零引用**（项目用模板字符串拼 className） |
| `html2canvas` | `^1.4.1` | **零引用** |
| `nprogress` | `^0.2.0` | **从未 import**（只有 `global.css:3-5` 的一段死 CSS 提到它） |
| `@types/nprogress` | `^0.2.3` | 上一条的 types |
| `monaco-editor` | `^0.52.2` | 只被死代码 `CodeViewer` 的**类型** import |
| `@monaco-editor/react` | `^4.7.0` | 只被死代码 `CodeViewer` 用 |

**删掉这 7 个依赖能显著减少 `node_modules` 体积和 CI 时间。** 但要注意：`monaco-editor` 是一个很有用的东西，如果你打算做"操作日志的请求/响应报文查看器"（这是个很自然的补功能，而且 `CodeViewer` 已经写好了），那就**别删，改成用它**。

### 2.5 后端正确的做法：不要删的东西

| 东西 | 为什么保留 |
| --- | --- |
| `MybatisConfig.auditHandler` 里的 `updatedBy` 填充 | 虽然业务表都靠数据库的 `ON UPDATE CURRENT_TIMESTAMP`，但应用层填充让逻辑删除时也能记录操作人 |
| `Result` / `PageResult` 两个类 | 统一响应契约，虽然小但必须存在 |
| `StagedUpload` / `StagedFile` / `StagedFileStorage` 三个接口/record | 文件上传后端**是能用的**（`FileUploadController` + `LocalStagedFileStorage` + 有测试），只是前端组件没接上。**这是"半成品功能"而不是死代码** —— 保留它，哪天要导入 Excel 就能直接用 |
| `application-dev.yml` | 空模板。留着比删掉好（同事可以拷去填自己的环境） |

---

## 3. 漏洞与安全隐患

> 按严重程度排序。**P0 是"搬到任何真实环境前必须修"的。**

### 🔴 P0-1 JWT 签名密钥硬编码，且生产配置没有覆盖它

**位置**：`backend/src/main/resources/application.yml:34`
```yaml
lbl:
  security:
    jwt-secret: lbl-shit-development-secret-must-be-at-least-32-characters
```
而 `application-prod.yml`（共 18 行）**完全没有** `jwt-secret` 这一项。

**影响**：**任何人拿到这份代码就能伪造任意用户的访问令牌。** JWT 只靠这个密钥验签，而密钥在仓库里。攻击者可以：
- 伪造一个 `admin` 的令牌；
- 伪造任意 `sid`（虽然 `JwtAuthenticationFilter` 会校验 Redis 会话，所以还需要一个有效的 sid —— 但 15 分钟的令牌 + 合法会话的组合已经足够）；
- 至少能做到"无痕访问"（审计日志里记的是被伪造的身份）。

**这一条是整份评估里最严重的问题。**

**修法**：见 `INTRANET-MIGRATION.md` 第 1.1 节（3 行改动）。
**注意**：`Keys.hmacShaKeyFor` 对短于 32 字节的密钥会直接抛异常，所以改的时候不用担心配太短。

### 🔴 P0-2 Jasypt 口令硬编码，而它保护的凭据也在仓库里

**位置**：`application.yml:13-21`（口令 `kariya`）+ `application-local.yml:5,27`（一个**公网 MySQL（`124.222.152.55:3308`，root 用户）** 和 Redis 的 `ENC(...)` 密文）

**影响**：`application-local.yml` 确实被 `.gitignore` 忽略了（已验证），但**加密口令在仓库里公开 → 这份"加密"提供的保护为零**。文件一旦泄露（截图、备份、误提交、同事拷贝），凭据立即可解。

**顺带风险**：那台公网 MySQL 的 `ENC(...)` 密文和口令都已存在你的本地磁盘上，而 `application.yml:12` 的注释自己就写了"该值仅供开发环境使用"——**但生产配置从没覆盖它**，所以如果哪天有人用 `--spring.profiles.active=prod` 但忘了配 `DB_PASSWORD`，会直接崩（这是好事）；真正的风险是**有人继续用 local profile 部署**。

**修法**：见 `INTRANET-MIGRATION.md` 第 1.2 节。

### 🟠 P1-3 三方登录的登录-CSRF（`state` 未绑定浏览器）

**位置**：`ExternalLoginService.begin()`（`:102-137`）和 `complete()`（`:150-186`）

`state` 生成、存 Redis（10 分钟 TTL）、一次性消费（`getAndDelete`）、与 provider 绑定 —— **这些都做对了**。但 `/start` **不种任何 Cookie**，`LoginTransaction` 也不记录浏览器指纹。

**攻击方式**：攻击者用自己的账号发起登录 → 截住回调 URL（自己的浏览器，很容易）→ 发给受害者 → **受害者被静默登录进攻击者的账号**，拿到 14 天 Cookie（`rememberMe` 也由攻击者控制）→ 受害者以为是自己账号，录入的数据全进了攻击者账号。

**当前危害有限**（GitHub/微信路径上受害者还会看到开户确认页，可能察觉），**但一旦接入 UIAS 自动建号，就是真实的账号接管。**

**注意：绑定流程是安全的** —— 它把 `initiatingSid` 钉在 Cookie 上并在两处重新校验。

**修法**：见 `THIRD-PARTY-LOGIN.md` 第 7.1 节（几十行）。
**如果你不启用三方登录**：把 `lbl.external-auth.providers` 全设 `enabled: false`，这个问题自然不存在。

### 🟠 P1-4 三方登录的 callback 完全没有限流 → 审计表可被匿名放大

**位置**：`/api/auth/**` 全放行（`WebSecurityConfig.java:56`）+ `JwtAuthenticationFilter.shouldNotFilter` 跳过（`:84-88`）+ `ExternalLoginService.complete()` 在任何失败路径都写 `sys_login_log` FAILURE（`:169-170`、`:177`）

**影响**：匿名脚本可以用伪造的 `code`/`state` 持续往审计表灌垃圾行。日志保留策略每天凌晨才跑一次（`LogRetentionJob`），所以一天内可以灌很多。这会让"翻某天的登录日志"变成全表扫描。

**修法**：`complete()` 开头加一句 `attempts.requireSourceQuota("external-callback", 60, Duration.ofMinutes(1));`（`/start` 本来就有 30 次/分钟的配额，callback 漏了）。

### 🟡 P2-5 三方登录网络层异常被静默吞掉

**位置**：`ExternalLoginService.java:257-259` 和 `:279-282`
```java
} catch (Exception ex) {
    throw new BusinessException("无法连接外部认证服务");   // ← 原始异常既没记录也没作为 cause 传递
}
```
DNS 失败 / TLS 握手失败 / 超时 **完全不留痕迹**。而 `complete()` 的 catch 只把业务消息写进登录日志。结果是"用户说三方登不上，而日志里干干净净"——**正是项目其他地方极力避免的那类问题**。

**修法**：加 `log.warn(..., ex)`。

### 🟡 P2-6 `returnTo` 之外，`/start` 的匿名入口有配额但没有日志

`/api/auth/external/{provider}/start` 有 30 次/分钟的来源配额（`:42-43`、`:90`），但被限流时只返回 429，**不写任何审计记录**（对比登录接口被限流时会写 `LOCKED`）。所以"有人一直在刷我的三方登录入口"这件事在日志里看不到。

**修法**：可选。低优先级。

### 🟡 P2-7 文件暂存功能是"单实例 + 内存索引"，重启即失效

**位置**：`file/LocalStagedFileStorage.java:27`
```java
private final Map<String, Entry> entries = new ConcurrentHashMap<>();   // ← 纯内存
```

`stage()` 把元数据写进这个 Map，`require(token, ownerId)` 和 `delete(token, ownerId)` 都要先查它：

```java
private Entry ownedEntry(String token, Long ownerId) {
    Entry entry = entries.get(token);
    if (entry == null || !entry.ownerId().equals(ownerId)) throw new BusinessException("上传文件不存在或无权访问");
    return entry;
}
```

**两个具体后果**：

1. **应用重启后，之前暂存的文件全部不可用。** 磁盘上的文件还在（`cleanup()` 会按 TTL 清掉），但内存 Map 空了 → `require()` 抛"上传文件不存在或无权访问"。用户会看到"上传明明成功了，点提交却说文件不存在"。
2. **多实例部署下功能不可用。** 如果 `/api/files/staged` 打到实例 A，而消费这个 token 的业务请求打到实例 B，B 的内存里没有它 → 同样报"文件不存在或无权访问"。**在负载均衡后面这是必然发生的。**

**当前影响有限**：因为这个功能**前端根本没接上**（`FileUpload` 组件是死代码，`api/file.ts` 没有任何页面在用），所以没有真实用户会遇到。**但你一旦开始用它（例如做 Excel 导入），这就是一个必须先修的问题。**

**修法**（按成本从低到高）：
- **最低成本**：把 `entries` 的索引也放进 Redis（`file:staged:<token>` → `ownerId|path|originalName|expiresAt`，TTL 与文件一致）。这样重启和多实例都解决了，改动约 30 行。
- **更彻底**：元数据落库（一张 `sys_staged_file` 表），文件放对象存储。但当前规模下不值得。

**顺带一提**：`LocalStagedFileStorage` 的其余部分写得很扎实 —— 有 SHA-256 摘要、`ATOMIC_MOVE`（不支持时回退）、文件名净化（去掉路径部分、拒 `\0`、限长 180）、扩展名与 Content-Type 双重白名单、按 ownerId 隔离、定时清理磁盘上超 TTL 的孤儿文件（覆盖"应用异常重启遗留"）。**唯一的问题就是索引在内存里。**

### 🟡 P2-8 前端没有 ErrorBoundary

全项目搜不到 `componentDidCatch` / `ErrorBoundary`。任何页面渲染期抛异常会**整树白屏**，用户只能手动刷新，而且看不到任何提示。

**修法**：加一个顶层 ErrorBoundary（约 30 行），显示"页面出错了 + 重新加载 + 返回首页"。这是低成本的健壮性提升。

### 🟢 P3-9 前端首屏体积偏大

| 项 | 大小 | 位置 |
| --- | --- | --- |
| `public/favicon.ico` | **995 KB** | ⚠️ 它和 `src/assets/logo.png` **字节数完全相同** —— 就是把 logo 改名成 `.ico`。它在 `index.html:6`（标签页图标）和 `index.html:80`+`LoadingScreen.tsx:11`（loading 图）被引用 |
| `src/assets/logo.png` | **995 KB** | 侧边栏 + 登录页 logo |
| `src/assets/background.png` | **974 KB**（1916×821 px） | 登录页背景（文件名已从拼错的 `backgroud.png` 改正 ✅） |

**登录页首屏要多下载约 2.3 MB 的图片。** 压缩后应该能到 100KB 以内。
而且 `dist/` 里的入口 chunk 是 **1.27 MB**（AntD + ECharts 打在一起，`vite.config.ts` 里没有 `manualChunks`）。

**修法（性价比最高的一步优化）**：压缩这 3 张图。其次可以在 `vite.config.ts` 加 `build.rollupOptions.output.manualChunks` 把 antd / echarts 拆开。

### 🟢 P3-10 首页"系统基础信息"写了一句假话

`pages/home/index.tsx:128`：`<dd>Spring Boot 3 · React 19</dd>` —— 实际是 React 18.3.1。

### 🟢 P3-11 登录接口没有验证码，注册接口有

后端 `CaptchaService` 完整实现且只被 `/api/auth/register` 使用（`RegistrationService.java:63`），登录接口不用。

**这不是漏洞**（登录有两层限流 + 15 分钟锁定），但**登录是更敏感、更常被攻击的入口**。如果内网要暴露一点，可以考虑给登录也加上（但要注意：登录加验证码会显著影响体验，且会破坏前端"失败后自动重试"的流程，**需要产品决定**）。

### 🟢 P3-12 已知但被明确接受的设计取舍（**不是漏洞，列出来避免重复讨论**）

| 取舍 | 位置 | 说明 |
| --- | --- | --- |
| 强制改密只对"密码登录"生效 | `PasswordChangePolicy` 类注释 | 三方登录的用户可以绕过密码过期策略。**明确接受**，注释里写了"不要把 `authMethod` 判断当成 bug 去掉" |
| 密码过期策略对"有第三方登录方式"的账号没有强制力 | 同上 | 直接后果，明确接受 |
| 解锁绑定时"该账号未设置登录密码"对外显示为"用户名或密码错误" | `RegistrationService.java:135-140` | 防账号枚举 |
| `actor()` 不看部门 `status` | `AccessPolicy.java:56-62` | "停用部门"= 不能往里面放人，不是整条分支下线。注释明确写了"改之前先确认需求" |
| 路由目录（`routes`）随 `/auth/me` 公开给所有登录用户 | `AuthController.routeCatalog()` | 传统静态路由方案里整张路由表本来就随 JS 包公开。注释里论证过 |
| 限流是"来源级"而不是"分布式" | `LoginAttemptGuard` 类注释 | 攻击者手握大量 IP 时仍需网关/WAF 解决。明确写了边界 |

---

## 4. 业务/设计上不合理或值得商榷的地方

### 4.1 首页是 100% 假数据（**这是"用户第一眼看到的东西"**）

`pages/home/index.tsx`：

| 行 | 内容 |
| --- | --- |
| 9-14 | 4 个统计卡：`1,286` 用户 / `18` 角色 / `12` 部门 / `46` 菜单 |
| 21-53 | 两张图的数据：`['4月'…'9月']`、`[980,1038,1062,1119,1184,1286]`、部门饼图 `486/314/260/226` |
| 72 | 硬编码日期 `2026 年 9 月 21 日　星期一` |
| 106-118 | 假的"最近登录"：`admin` / `zhangsan` / `lisi` / `wangwu` |
| 128 | `React 19`（假） |

而首页菜单是 `init_data.sql:27` 的**第 1 条**（`'首页'` → `/home`），所以**每个用户登录后第一眼看到的就是这个假看板**。

**这是我认为最该先补的功能**（优先级 P1）。而且成本很低：后端已经有 `sys_user` / `sys_role` / `sys_dept` / `sys_menu` / `sys_login_log` 五张表，加一个 `GET /api/home/overview` 返回这几个 count 就能让 4 个卡片变成真的。

### 4.2 两个"看起来能用的空壳"比没有更糟

| 位置 | 问题 |
| --- | --- |
| `AppLayout.tsx:204` | 顶栏搜索框，`placeholder='搜索功能、文档或快捷操作...'`，**完全没有逻辑** |
| `components/ai/AiAssistant.tsx:21-24` | "AI 助手"面板，回复是 `setTimeout` 硬编码的"智能助手服务接口已预留，接入模型后即可为你处理这类请求。"。**没有任何 API 模块** |

用户会去用，然后困惑。**要么实现，要么删掉。**

### 4.3 "忘记密码"是死路

`pages/login/index.tsx:110-112`：点击只弹 `message.info('忘记密码后请联系管理员重置')`。

而**没有任何自助重置页**（全项目搜不到 password-reset 路由）。改密码只有两条路：
- 已登录 → 顶栏"修改密码"（`PUT /system/users/me/password`）
- 管理员重置 → 生成临时密码（`system:user:reset-password` 权限，**仅超管**）

对个人项目这没问题；**但搬到公司内网、有几十个用户之后，这会变成一个持续的支持负担**（每次都有人忘了密码来找你）。而且"重置密码"权限被限制为超管独占（`AccessPolicy.PLATFORM_ONLY_PERMISSION_CODES`），意味着**只有你一个人能帮人重置密码**。

**建议**：内网部署前想清楚——要么加一个邮件/短信自助重置，要么把"重置密码"权限开放给系统管理员（需要同时改 `PLATFORM_ONLY_PERMISSION_CODES` 和 `UserService.resetPassword` 里的 `requireSuperAdmin` 硬校验）。

### 4.4 部门变更审批没有"待办入口"

审批人只能通过**站内消息中心**看到待审批申请（`account/notifications/index.tsx:62-79`）。而且：
- **没有"我有几个待审批"的角标**（顶栏铃铛只显示"未读通知数"）；
- **没有管理侧的申请列表**（只能一条条从消息点进去）。

**后果**：一个不常看消息中心的审批人，永远不会处理任何申请。

这是 `departmentchange` 这个样例模块最明显的功能缺口。

### 4.5 通知的"点击跳转"只支持一种业务类型

`account/notifications/index.tsx:38` 只处理 `businessType === 'DEPARTMENT_CHANGE'`；其它类型点了没反应。（`AppLayout.tsx:114` 会跳到消息中心并带 `businessId`，但页面不认识它。）现在是可接受的（只有一个业务类型），**但你加第二个业务模块时会立刻遇到**。

### 4.6 "停用部门"的语义容易误解

`AccessPolicy.java:56-62` 明确写了：**停用只表示"不能再往这个部门里放人"，已经在里面的人完全不受影响**（数据范围照常以这个部门为根展开）。

这个决定本身是合理的，但**只有 `DeptDialog` 里"状态"字段的说明解释过一次**，而且代码注释里明确说了"两边必须一致"。用户很可能会以为"停用技术部 = 技术部所有人的数据都看不见了"。

**建议**：在部门列表页的"状态"列加一个 Tooltip 说明，而不只在编辑弹窗里。

### 4.7 `sys_role.data_scope` 的 4 档缺一档

见 `DATA-SCOPE-ROLE-DEPT.md`。**注意：`sys_role_dept` 表当前不存在**（不是"闲置"），所以"给角色指定若干部门"这个需求目前**完全无法满足**。

### 4.8 `maxScopeRank` 的比较语义有点绕

`canManageUser` 里：
```java
return actor.permissions().containsAll(targetPermissions) && !actor.permissions().equals(targetPermissions)
    && targetScopes.stream().allMatch(scope -> scopeRank(scope) <= actor.maxScopeRank());
```

`!actor.permissions().equals(targetPermissions)` 这一句的意思是"不能管理权限集合与自己完全相同的用户"。初衷是防同级互相管理，但副作用是：**一个恰好拥有相同权限集合的合法下属无法被管理**。

更微妙的是 `requireAssignableRoles`：
```java
if (combined.equals(actor.permissions())) throw new BusinessException("不能分配与自身同级的权限组合");
```
它比较的是"待分配角色的**权限组合**是否与自己的**完全相等**"。如果自己的权限集合是 `{A, B}`，那么一个只有 `{A}` 的角色能分配（`{A} != {A,B}`），一个 `{A,B}` 的角色不能分配，一个 `{A,B,C}` 的角色也不能分配（但更该被拒的原因应该是"不能授予自己没有的权限"——那条由 `canAssignRole` 的 `containsAll` 挡了）。

**这套规则是"安全的"（偏保守），但不好解释。** 如果将来要交给人维护，建议补一份判定表。

### 4.9 前端 `AuthGuard` 每次路由切换都重新拉 `/auth/me`

`AuthGuard.tsx:40` 的 effect 依赖里包含 `location.pathname`，而 effect 内部调 `fetchMe()`（`:22`）。

**后果**：
- 每次导航多发一次 API 请求（性能浪费，但可接受）；
- 更值得注意的是：它会重新 `dispatch(setProfile(...))` **替换掉 `menus` / `permissions`** —— 如果管理员在用户操作过程中改了他的权限，界面会在**下一次导航时静默变化**，可能让正在进行的操作失败。

**修法**：effect 依赖去掉 `location.pathname`（改成只在 token 变化时跑），或者用 ref 记录已加载标记。

### 4.10 操作日志记不到"改了谁、改了什么"

`sys_operation_log` 的字段只有：`user_id` / `username` / `module` / `action` / `request_ip` / `result` / `duration_ms` / `created_time`。

所以一次"修改用户"操作，审计里看到的是：

| user_id | module | action | result | duration_ms |
| --- | --- | --- | --- | --- |
| 1 | 用户管理 | 修改用户 | SUCCESS | 137 |

**看不到：改了哪个用户、改了哪些字段。**

对"谁登录过、谁删过东西"这类溯源够用；但**做不到"这个用户的名字是谁在什么时候改的"**——而这恰恰是后台系统最常见的审计需求。

`OperationLog` 注解本身也不支持传业务参数（`@OperationLog(module, action)` 只有两个属性），`OperationLogAspect` 也不读取方法入参。

**可行的低成本改进**（按成本排序）：
1. 给注解加一个 `SpEL` 表达式属性，例如 `@OperationLog(module="用户管理", action="修改用户", target="#id")`，切面解析后写进日志表。需要在 `sys_operation_log` 加一列 `target VARCHAR(120)`。
2. 更进一步：存一份脱敏后的请求体 JSON（**必须脱敏** —— 密码、手机号、邮箱不能原文落库）。这正是 `CodeViewer` 组件（当前是死代码）擅长的展示场景。

⚠️ **注意"脱敏"不是可选项**：`UserService.changeOwnPassword`、`UserCreateRequest`、`RegistrationRequest` 的请求体里都有明文密码。任何"记录请求体"的方案都必须先过一遍白名单/黑名单字段。

---

## 5. 未完成项清单（按"用户可见程度"排序）

| # | 未完成项 | 位置 | 用户会不会看到 |
| --- | --- | --- | --- |
| 1 | **首页看板全是假数据**（含一句 "React 19" 的假话） | `pages/home/index.tsx` | ✅ 每天第一眼 |
| 2 | ~~**首屏 loading 文案是乱码**~~ —— **已修复 ✅** | `index.html:79,84` 现为合法 UTF-8（`应用加载中` / `少女祈祷中...`） | 已解决；文案本身待改品牌 |
| 3 | **顶栏搜索框是空壳** | `AppLayout.tsx:204` | ✅ 每天看到 |
| 4 | **AI 助手是假回复** | `components/ai/AiAssistant.tsx:21-24` | ✅ 点开就看到 |
| 5 | **403 页自认为是"设计占位"** | `Forbidden.tsx:9,34,50`（"设计替换区"）+ `forbidden.css:2`（"纯占位实现，改版时整个文件可以直接替换"） | ✅ 权限不足时 |
| 6 | **"忘记密码"是死路** | `login/index.tsx:110-112` | ✅ 忘记密码时 |
| 7 | **暗色主题下多页不可读** | `loadingScreen.css` + `index.html` 内联 CSS、`pageUnavailable.css`、`notFound.css`、`setup.css`、`security.css`、`login.css` 全部硬编码浅色 | ✅ 开暗色就发现 |
| 8 | **部门变更审批没有待办角标/列表** | `account/notifications/index.tsx:62-79` | ✅ 审批人 |
| 9 | **消息中心点击跳转只支持一种业务类型** | `notifications/index.tsx:38` | ⚠️ 加第二个业务模块时 |
| 10 | **文件上传前端组件没接上**（后端完成） | `components/FileUpload/`、`api/file.ts` | ⚠️ 需要导入 Excel 时 |
| 11 | **`CodeViewer` 完成了但没用**（Monaco + JSON/XML 格式化 + 下载） | `components/CodeViewer/` | ⚠️ 操作日志页缺"看请求/响应报文"的能力 |
| 12 | **UIAS SDK / ESF 适配器待内网实现** | `auth/uias/` 的两个端口 | ⚠️ 部署到内网前 |
| 13 | **员工工号需预置并验证** | `sys_user.employee_no` | ⚠️ 首次启用 UIAS 前 |
| 14 | **无 i18n** | `ThemeProvider.tsx:3,88` 硬编码 `zhCN`；TSX 里约 150 处中文字面量 | ⚠️ 需要多语言时 |
| 15 | **没有健康检查端点** | `pom.xml` 里没引 `spring-boot-starter-actuator` | ⚠️ 上容器编排时（k8s 探针需要） |
| 16 | **日志删除不可恢复、无归档** | `loginlog/index.tsx:88`、`operatelog/index.tsx:88` 的提示文案自己写了"删除后不可恢复"；`LogRetentionJob` 也是物理删除 | ⚠️ 合规审计时 |
| 17 | **操作日志没有详情/报文查看** | `operatelog/index.tsx` | ⚠️ 排查问题时 |
| 18 | **没有自助注册审核流程** | `pages/register/index.tsx` 注册即生效（给 `basic_role`） | ⚠️ 开放注册到内网时（建议内网直接关掉注册） |

---

## 6. 优先级路线图

> 我按"性价比 × 必要性"排的。**阶段一和阶段二是搬去内网的前置条件。**

### 阶段一：安全整改（**必须做，全部是"一改就完"级别，预计半天**）

| # | 任务 | 参考 |
| --- | --- | --- |
| 1 | `jwt-secret` 改环境变量，`application-prod.yml` 补上（无默认值） | `INTRANET-MIGRATION.md` 1.1 |
| 2 | 处理 Jasypt 硬编码口令（内网建议直接弃用，全走环境变量） | 同上 1.2 |
| 3 | 检查 `application-local.yml` 是否进过 git 历史 | 同上 1.3 |
| 4 | 三方登录 callback 加来源限流（P1-4） | `THIRD-PARTY-LOGIN.md` 7.2 |
| 5 | 三方登录网络层异常加日志（P2-5） | 同上 7.3 |
| 6 | 决定是否启用三方登录。**不启用就把 providers 全设 false**（那样 P1-3 的 CSRF 问题自动消失） | 同上第四节 |

**做完这 6 项：内网部署的安全性就到一个可接受的水平了。**

### 阶段二：止血（**恢复工程能力，预计 1 天**）

| # | 任务 | 说明 |
| --- | --- | --- |
| 8 | **恢复 3 个被删的测试** | `git checkout HEAD -- <path>`，成本极低，收益极高（507 行测试，覆盖最敏感的 3 个模块） |
| 9 | **捞回 5 个旧升级脚本 + 建立迁移目录** | 从 `backend/target/lbl-shit-1.0.0.jar` 的 `BOOT-INF/classes/db/` 提取（命令见 `INTRANET-MIGRATION.md` 5.2）；建立 `db/migration-manual/` 约定 |
| 10 | ~~修掉 `index.html` 的中文乱码~~ —— **已完成 ✅** | 顺便建立了乱码检测法：源码里出现**半角片假名**（`シ` `コ`）= 乱码铁证 |
| 11 | **删掉或实现顶栏搜索框** | 一行删除 vs 一个功能，二选一 |
| 12 | **加一个顶层 ErrorBoundary** | 约 30 行，消掉"白屏无提示" |
| 13 | **压缩 3 张图片**（favicon 995KB / logo 995KB，两者其实是同一文件；background.png 974KB） | 登录首屏能省约 2MB |

### 阶段三：品牌清理 + 内网部署（**预计 1 天**）

按 `INTRANET-MIGRATION.md` 第 ②③④ 节逐项执行。这一步是机械工作，清单已经给全了。

**关键提醒**：`favicon.ico` 和 `logo.png` 是**同一个文件的两个名字**（字节数完全相同），替换时两处都要处理。

### 阶段四：把首页做真（**预计半天，收益最高**）

| # | 任务 |
| --- | --- |
| 14 | 后端加 `GET /api/home/overview`，返回 user/role/dept/menu 的 count + 近 7 天登录趋势（从 `sys_login_log` 按天聚合）+ 部门用户分布（`sys_user` group by dept_id） |
| 15 | 前端把这些接上，删掉硬编码数据；顺手把"React 19"改成真实版本 |
| 16 | 首页的"最近登录"接 `sys_login_log`（按 `login_time` 倒序取 4 条） |

**这是"从 demo 变成系统"的最直观一步。** 而且 4 个卡片的 count 查询都是 `SELECT COUNT(1)`，成本极低。

### 阶段五：消重 + 提升可维护性（**预计 2-3 天，可以慢慢做**）

| # | 任务 | 收益 |
| --- | --- | --- |
| 17 | 抽 `useFormDialog` hook（吃掉 5 个 Dialog 的重复模板） | 消掉约 200 行重复 |
| 18 | 抽 `utils/tree.ts`（`toTree` + `blockedIds`） | 消掉 5 处重复实现 |
| 19 | 把两个日志页合并成一个泛型 `LogPage` 组件 | 消掉约 90 行重复 |
| 20 | 抽统一的 `formatDateTime` 工具，替换 9 处内联 `replace('T',' ')` | — |
| 21 | 让 4 处内联的 `"PASSWORD".equals(authMethod)` 改调 `appliesToPasswordLogin`（或删掉那个方法并加注释） | 消掉"单一真相来源没被使用"的问题（2.3 节） |
| 22 | 拆 `AppLayout.tsx`（6 个关注点） | — |
| 23 | 删掉 9 个死代码文件 + 3 处死 CSS + 7 个未使用的 npm 依赖（**但先决定 `monaco-editor` 是否要用**） | 减少认知负担与构建体积 |
| 24 | 装 ESLint + Prettier（`.prettierrc.json` 已经写好了）并接入 build | 拦住 `MenuOutlined` 那类问题，并且让那份配置不再孤儿 |
| 25 | 把 `types/log.ts:1` 的 `org.LBL` 改成 `org.lbl`；删掉 `application-local.yml:34` 那条指向不存在包的日志配置 | 清理错误的文档 |
| 26 | 引入 Flyway 并 `baseline-on-migrate=true` | 一劳永逸解决迁移问题 |

### 阶段六：功能补全（**按需**）

| 优先级 | 任务 |
| --- | --- |
| 高（内网前） | 部门变更审批的待办角标 + 管理侧申请列表（4.4 节） |
| 高（内网前） | 决定"忘记密码"怎么办：加自助重置，还是把重置权限开放给系统管理员（4.3 节） |
| 中 | 实现 `sys_role_dept` 自定义数据范围（`DATA-SCOPE-ROLE-DEPT.md`） |
| 中 | 操作日志的请求/响应报文查看器（**用现成的 `CodeViewer`**） |
| 中 | 导入 Excel 功能（**用现成的 `FileUpload` + 后端 `/api/files/staged`**） |
| 低 | 暗色主题补全（修 6 处硬编码浅色的 CSS） |
| 低 | 403 页重新设计（它自己都写着"设计占位"） |
| 低 | i18n（如果只服务内网，可以永远不做） |
| 低 | 引入 actuator 加健康检查端点 |
| 按需 | 在内网实现 UIAS SDK 与 ESF 两个适配器（流程骨架已完成，见 `UIAS-INTEGRATION.md`） |

---

## 7. 最后：这个项目值得继续投入吗？

**值得。理由有三条**：

1. **地基是真的硬。** 认证/鉴权/权限这三层，在个人项目里做到这个程度的很少见。而且**设计决策都被记录下来了**（为什么这么写、不这么写会怎样），这意味着半年后你回来看还能看懂，别人接手也能看懂。这部分**重写一遍的成本远高于继续用**。

2. **扩展点都留好了。** `AccessPolicy` 的 `Actor` 抽象让"加数据范围类型"只要改一处；`componentRegistry` 的构建期扫描让"加页面"不用改路由；`SystemPermissionInitializer` 让"加权限"能自动补建到老库；`Result`/`PageResult`/`GlobalExceptionHandler` 让"加接口"有一致的契约。

3. **欠的都是"工程债"而不是"设计债"。** 首页假数据、死代码、缺迁移、测试被删 —— 这些**都不需要重构架构**，是"花几个小时就能还掉"的债。这和"设计错了要推倒重来"是完全不同量级的问题。

**唯一需要你主动决策的**是：**你想要的是"个人玩具"还是"能上内网的系统"**。
- 如果是前者：阶段一 + 阶段二做完就够，剩下的随心。
- 如果是后者：**阶段一（安全）、阶段二（止血）、阶段四（首页做真）、阶段六的前两项（审批待办 + 忘记密码）** 是必须做的，否则第一个月就会被同事问爆。

**一个小提醒**：现在有 **149 个未提交的改动**（`git status`），而且只有 4 个 commit。**建议在做阶段一之前先提交一次快照**（哪怕 commit message 写"WIP"），这样改坏了能回滚。这也顺便解决了"`application-local.yml` 历史上有没有泄露过密码"这个问题——如果它从来没被提交过（很可能），一次干净的提交就能把这个风险关掉。
