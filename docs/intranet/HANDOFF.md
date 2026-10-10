# Intranet 跨设备交接

> 本文件保存“当前工作现场”，每次开发结束时覆盖更新对应章节。历史设计结论放入 `DECISIONS.md`，长期计划放入 `PLAN.md`。接手设备必须用真实代码和 Git 命令复核本文件。

## 交接元信息

- 更新时间：2026-10-10 17:58（Asia/Shanghai）
- 交接设备/人员：当前接管会话（DeepSeek Harness）；具体设备标识待用户按需补充
- 当前阶段：P1 交易资产与数据资产管理（P0 仍有未收尾任务，见“未完成事项”）
- 当前分支：`main`，跟踪 `origin/main`，与远程完全同步（ahead/behind = 0/0）

## 当前开发任务

本次是「Codex 交接后的只读复核 + 用户批准的最小修复批次」。用户批准范围（范围 B）为：

1. 修复前端类型检查阻断；
2. 更新已失效的协作文档；
3. 删除已无引用的死代码 `MessageFieldPanel.tsx`；
4. 后端补报文节点树的环校验。

用户同时确认：`V2`/`V6` 向基线 `sys_menu` / `sys_role_menu` 写入种子数据属于「复用现有 RBAC」，不按基线数据库脚本修改处理（见 `DECISIONS.md` D-011）。

未获批准因而**未执行**：运行 V1~V6 SQL、启动数据库或后端服务、修改任何基线代码、`commit` / `push`。

## P1 现场

### P1-01 交易基础信息（2026-10-10，已完成编码，未做运行验证）

- 用户已确认交易主表字段并明确允许建表。交易编码为 `VARCHAR(64)`；状态为 `0-3`、默认 `0`；删除为物理删除；四个 ESF 字段必填；打印文件方式为 `0=无/1=异步/2=同步`，方式为无时服务端清空文件配置。
- 已新增独立内网 SQL：`V1__create_intranet_transaction.sql` 创建 `intranet_transaction`；`V2__seed_intranet_transaction_menu.sql` 创建内网交易菜单/按钮权限并授予超管。脚本尚未执行。
- 已新增后端 `org.lbl.intranet.transaction` 的 Entity、Mapper、Request、VO、Service、Controller，提供分页查询、详情、新增、编辑与物理删除，并使用 `intranet:transaction:*` 权限码。
- 已新增前端 `pages/intranet/transaction` 的列表、查询、编辑弹窗和详情抽屉，复用 SmartTable、Permission、Ant Design 与既有请求封装。
- 交易弹窗为与系统用户管理一致的 660px 横向表单及共享 `system-dialog` 间距规范；`V3__insert_intranet_transaction_demo_data.sql` 仅含虚构信息，供界面验证。
- 交易详情抽屉的 Descriptions 扩展至抽屉正文全高；表单标签局部改为单行省略；交易状态改为表头筛选，更新时间支持服务端升降序排序。未修改 SmartTable。

### P1-02 请求与响应报文字段（2026-10-10，已完成编码，未做运行验证）

- 已确认单表 `intranet_transaction_message_field` 管理请求、响应、对象、数组及嵌套字段；已新增 V4 建表脚本、后端 CRUD 与字段设计页面。未对同父节点英文名增加唯一约束（该规则尚未确认）。
- 已新增 V5 虚构字段演示数据，覆盖请求字段、响应对象、响应数组及数组内对象/字段，依赖 V1、V3、V4 后按需执行。
- 字段维护已从交易详情抽屉迁至隐藏路由 `/intranet/transaction/fields?transactionId=<id>`，新增 V6 隐藏菜单脚本；页面使用树形行内编辑、保存、取消、删除及新增子字段，不修改动态路由基础设施或 SmartTable。
- 字段设计行内保存会显式提交既有行或草稿行的 `parentId`；请求/响应切换改为 Ant Design 标签页。字段中文名、英文名使用中文必填提示，说明编辑改为本地多行输入。
- 迁移后遗留的 `MessageFieldPanel.tsx` 已在本轮删除（无任何引用）。

### 本轮修复（2026-10-10 17:58）

- **前端类型检查阻断已修复**：`index.tsx` 原先读取 `filters.status`，而 `TableRequestParams.filters` 声明为可选，`tsc --noEmit` 报 `TS18048: 'filters' is possibly 'undefined'`，导致 `npm run build` 失败。现按基线写法（对照 `pages/home/index.tsx`）先收敛为数组再判长度。同一处顺带修掉一个隐蔽问题：antd 返回空数组 `[]` 时它是真值，原写法会算出 `NaN` 并发出 `status=NaN` 请求触发后端 400。
- **死代码清理**：删除 `frontend/src/pages/intranet/transaction/MessageFieldPanel.tsx`（154 行，无任何引用）。附带效果：由于 `componentRegistry` 用 `../pages/**/*.tsx` 全量 glob，该文件此前会被打成独立 chunk 并出现在「菜单管理 → 前端组件」下拉里，现已消失。
- **后端补环校验**：`TransactionMessageFieldService.requireParent` 原先只挡「父节点 = 自身」。若把节点挂到自己的后代下会形成环，而前端 `toTree` 中环上节点找不到根，**整棵子树会从界面静默消失而非报错**。现从目标父节点向上回溯，回到自身即拒绝；`visited` 集合兼作脏数据兜底，避免历史数据已有环时死循环。同时把「父节点不存在」的报错文案从「报文字段不存在」改为更准确的「父节点不存在」。
- **新增内网单元测试**：`TransactionMessageFieldServiceTest`（4 例），锁定上述环规则与「父节点不存在」判定。

## 最近完成内容

- 完成 Codex 交接后的只读复核：核对 5 份协作文档、Git 状态与全部 intranet 真实代码。
- 修正交接文档中 4 处与代码/命令不符的描述（详见下文“文档纠错记录”）。
- 完成用户在范围 B 内批准的全部 4 项修复，并补齐验证证据。

## 文档纠错记录（本轮修正的失效描述）

| 原文档说法 | 实际事实 |
| --- | --- |
| 「未执行 commit/push」「`AGENTS.md` 和 `docs/` 为未跟踪新增项」 | 已全部提交。接管时 HEAD = `b638c91`，工作区干净 |
| 「`npm run build` 因缺少 `react-markdown`、`remark-gfm` 停止」 | 两依赖已在 `b20d1c0` 写入 `package.json` 且已安装；当时真正的阻断是 1 个 TS 类型错误（本轮已修） |
| 「`mvn compile` 未执行成功，终端没有 `mvn` 命令」 | Maven 只是不在 PATH；用完整路径编译成功。见“开发环境实测” |
| 元信息「当前阶段：P0」 | 正文实际已推进到 P1-01 / P1-02 |

## 开发环境实测（本机，2026-10-10）

| 项 | 实测值 | 备注 |
| --- | --- | --- |
| Node | `v22.23.2` | 满足 `package.json` 的 `>=20.19 <25` |
| Java | `17.0.12`（`E:\codesoftware\Java\jdk17`） | 与 pom 的 Java 17 一致 |
| Maven | `3.9.14`（`E:\codesoftware\apache-maven-3.9.14\bin\mvn.cmd`） | **不在 PATH**，必须用完整路径；本地仓库 `E:\codesoftware\maven\mavenRepo` |
| 前端依赖 | `frontend/node_modules` 已安装 | 含 `react-markdown`、`remark-gfm`、`antd` |
| MySQL 3306 / Redis 6379 / ES 9200 / 后端 8080 | **均未监听** | 因此无法启动应用、无法执行 V1~V6、无法端到端验证 |

> 环境注意：本机为 Windows PowerShell 5.1（无 `pwsh`），且执行策略禁止直接运行 `.ps1`，`npm`/`npm.ps1` 会被拦；请用 `npm.cmd` 或直接调用 `node_modules\vite\bin\vite.js`。仓库 `core.autocrlf = true` 且无 `.gitattributes`，Git 会提示 LF 将被替换为 CRLF，属预期现象。

## 已验证功能和测试结果

| 命令/场景 | 结果 | 备注 |
| --- | --- | --- |
| `git status --short --branch` | `## main...origin/main`，工作区干净 | 接管时状态 |
| `git rev-list --left-right --count origin/main...main` | `0  0` | 与远程完全同步 |
| `mvn -o -B -DskipTests compile` | **BUILD SUCCESS**，261 个源文件 | intranet 后端代码**首次获得编译级验证** |
| `mvn -o -B test`（修改前） | Tests run: **13**, Failures: 0, Errors: 0 | 全部为基线审计/Jasypt 测试，intranet 当时无测试 |
| `mvn -o -B test`（修改后） | Tests run: **17**, Failures: 0, Errors: 0 | 13 基线 + **4 新增 intranet 测试**；`TransactionMessageFieldServiceTest` 4/4 通过 |
| `tsc --noEmit -p tsconfig.app.json`（修改前） | **失败**，1 个错误 `TS18048`（`index.tsx:98`） | 阻断 `npm run build` |
| `tsc --noEmit -p tsconfig.app.json`（修改后） | **通过**，无错误 | |
| `vite build`（修改前） | 成功，40.98s | 说明打包链路本身没问题，只卡类型检查 |
| `npm.cmd run build`（修改后） | **通过**，exit 0，37.86s | `tsc && vite build` 两步均通过 |
| 产物核对 | 构建输出中不再出现 `MessageFieldPanel` chunk | 确认死代码已从产物中移除 |
| 应用启动 / 接口联调 / SQL 执行 | **未执行** | 本机无 MySQL、Redis；且未获授权执行建表脚本 |

## 新增或修改的主要文件

### 本轮修改（相对 `b638c91`，尚未提交）

- `frontend/src/pages/intranet/transaction/index.tsx`（修改）：修正 `filters` 可选导致的类型错误与空数组 `NaN` 风险。
- `frontend/src/pages/intranet/transaction/MessageFieldPanel.tsx`（**删除**）：154 行死代码，迁至隐藏路由后已无引用。
- `backend/src/main/java/org/lbl/intranet/transaction/field/service/TransactionMessageFieldService.java`（修改）：`requireParent` 增加环校验；父节点不存在改为独立报错文案。
- `backend/src/test/java/org/lbl/intranet/transaction/field/service/TransactionMessageFieldServiceTest.java`（**新增**）：4 个环校验单元测试。
- `docs/intranet/HANDOFF.md`、`PLAN.md`、`MIGRATION.md`、`DECISIONS.md`（修改）：同步事实、状态与新增决策。

### 已有（提交 `b638c91`）

- 后端 13 个文件：`org/lbl/intranet/transaction/` 下 Entity / Mapper / Request / VO / Service / Controller，及 `field/` 同构一套。
- SQL 6 个：`backend/src/main/resources/db/intranet/V1~V6`。
- 前端 7 个：`frontend/src/pages/intranet/transaction/` 下 `index.tsx`、`TransactionDialog.tsx`、`fields/index.tsx`、`api.ts`、`types.ts`、`index.css`、`fields/index.css`。

## 未完成事项

- **数据库未落地**：V1~V6 全部仍为「待执行」。表和菜单权限目前不存在，两个页面即使能构建也打不开。完成标准：在可用 MySQL 上按 V1→V6 顺序执行并记录结果。
- **P1 无任何运行验证**：没有启动过后端、没有联调过接口、没有打开过页面。完成标准：至少一次「建表 → 启后端 → 登超管 → 交易 CRUD + 字段树维护」的正向与异常路径实测。
- **P0 未收尾**：`PLAN.md` 中「确认两台设备的运行版本和启动/测试命令」「确认内网权限命名、菜单初始化与 SQL 存放位置」「演练一次跨设备交接」三项仍为未开始。本机（第二台设备）信息已在“开发环境实测”补齐，可据此更新。
- **P1 剩余切片未开始**：源系统 / HBase / ES 资产模型与建表；Excel 导入；脱敏样例数据策略。
- **待确认的表结构项仍在等用户**（见 `DECISIONS.md` T 系列），特别是报文子表同父英文名唯一约束、V6 隐藏菜单是否需要独立权限码。

## 当前问题和风险

- **未提交改动不会跨设备出现**：本轮的 4 项代码改动与 4 份文档改动全部只在当前工作区，尚未 `commit` / `push`（按用户要求未执行）。切换设备前必须处理，否则另一台设备拉不到。
- **环校验只是应用层防护**：`intranet_transaction_message_field` 没有数据库约束阻止环，直接写库仍可绕过。当前阶段可接受，若后续引入批量导入需重新评估。
- **菜单种子脚本的幂等边界**：V2/V6 的 `NOT EXISTS` 守卫未排除 `deleted = 1`，而 `sys_menu` 是 `@TableLogic` 逻辑删除且 `route_path` / `permission_code` 上有唯一键。若存在同路径的软删除菜单，脚本会**静默跳过插入**（不报错），菜单永远不会出现。尚未处理，待用户决定。
- **字段级审计信息不完整**：`TransactionMessageFieldController` 的 3 个 `@OperationLog` 都未填 `targetId`，操作日志无法定位到具体字段；基线其他模块普遍填 `targetId = "#id"`。
- **报文子表缺少业务唯一约束**：同父节点下英文名可重复，可能导致字段语义歧义。文档已标记为未确认，未擅自添加约束。
- **环境差异风险**：本机 Maven 不在 PATH，另一台设备是否一致未知；接手时请重新实测本节命令。
- **审批复用仍是硬边界**：`ApprovalTaskMapper` 直接绑定部门调动表，P2 前必须有方案并获批准（详见 `ARCHITECTURE.md`）。

## 下一步建议

1. 用户审阅本轮改动（`git diff`）并决定是否提交；未提交前不要切换设备。
2. 准备可用的 MySQL 实例，按 V1→V6 顺序执行脚本并记录每步结果；执行前确认 V2/V6 是否已存在同名软删除菜单。
3. 启动后端 + 前端做一次端到端实测：交易列表分页/筛选/排序、新增/编辑/删除、字段树增删改与**环拦截提示**、隐藏字段页直达访问、超管菜单可见性。
4. 实测通过后更新 `HANDOFF.md` 与 `PLAN.md`，把 P1-01 / P1-02 标记为已完成并附验证证据。
5. 再进入 P1 的第三切片（源系统 / HBase / ES 资产模型评审），仍先评审模型、不写业务代码。
6. 顺手可做的低风险项：补 `TransactionMessageFieldController` 的 `targetId`；决定是否处理 V2/V6 的 `deleted` 边界。

## Git 工作区状态和最近提交

- `git status --short --branch` 摘要：`## main...origin/main`，与远程完全同步；工作区含本轮 3 个已跟踪改动（2 修改 + 1 删除）和 1 个未跟踪新增测试目录。
- 最近提交：`b638c91`，提交说明为 `2026年10月10日 17:29:17`（提交说明即时间戳是本仓库现有习惯）。
- 未提交改动**尚未推送**，且未执行 `commit` / `push` / `pull` / `merge` / `rebase` / `reset`。
- 构建产物（`backend/target/`、`frontend/dist/`）已由 `.gitignore` 忽略，未进入改动范围。

## 是否涉及基线公共代码修改

**否。** 本轮 4 项代码改动全部位于 `org.lbl.intranet` / `src/pages/intranet` 与 `docs/intranet` 内。逐提交核对历史：`b7a9212` 只新增 `AGENTS.md` 与 `docs/`；`b638c91` 只新增 intranet 代码与文档。未改动任何基线业务代码、全局路由、权限初始化器、构建配置、依赖或既有 SQL 脚本。

用户已确认 V2/V6 向 `sys_menu` / `sys_role_menu` 写入种子数据属于复用现有 RBAC（`DECISIONS.md` D-011），不按基线数据库脚本修改处理。

---

## 后续交接填写模板

复制以下结构覆盖本文件上方对应章节；不要只追加流水账。

```markdown
## 交接元信息
- 更新时间：YYYY-MM-DD HH:mm（Asia/Shanghai）
- 交接设备/人员：
- 当前阶段：
- 当前分支及跟踪分支：

## 当前开发任务
- 目标、明确范围、用户批准边界：

## 最近完成内容
- 仅列已完成并能从代码/命令验证的事项。

## 已验证功能和测试结果
| 命令/场景 | 结果 | 备注 |
| --- | --- | --- |

## 新增或修改的主要文件
- 路径：用途与关键改动。

## 未完成事项
- 明确剩余工作和完成标准。

## 当前问题和风险
- 阻塞原因、影响、已尝试方案。

## 下一步建议
1. 给出可直接执行的小步骤。

## Git 工作区状态和最近提交
- `git status --short --branch` 摘要：
- 最近提交 hash/说明：
- 未提交改动是否已推送：

## 是否涉及基线公共代码修改
- 否；或：是，列出文件、原因、用户批准记录、替代方案和回归结果。
```
