# Intranet 跨设备交接

> 本文件保存“当前工作现场”，每次开发结束时覆盖更新对应章节。历史设计结论放入 `DECISIONS.md`，长期计划放入 `PLAN.md`。接手设备必须用真实代码和 Git 命令复核本文件。

## 交接元信息

- 更新时间：2026-10-10 18:36（Asia/Shanghai）
- 交接设备/人员：当前接管会话（DeepSeek Harness）；具体设备标识待用户按需补充
- 当前阶段：P1 交易资产与数据资产管理（P0 仍有未收尾任务，见“未完成事项”）
- 当前分支：`main`，跟踪 `origin/main`，与远程完全同步（ahead/behind = 0/0）
- 上一轮交接（范围 B 的最小修复批次）已由用户提交为 `c1d34d2`，工作区仅剩本轮改动

## 当前开发任务

**本轮（2026-10-10 第三轮）**：按用户批准的范围，只做「字段设计可编辑表格」的**步骤 1**——

1. 展开图标改为与菜单管理页/侧边栏子菜单一致的 chevron；
2. 表格默认全展开；
3. 新增子字段时自动展开其父节点。

用户同时确认了两项设计决策：可编辑树形表格**暂不提升为基线通用组件**（将来先放 `intranet/shared`，等第二个用例再评估提升，见 D-012）；该表格**不引入分页**，数据量靠筛选控制（见 D-013）。

**步骤 2（搜索 + 节点类型筛选）与步骤 3（抽取 `intranet/shared` 组件）本轮明确不做。**

未获批准因而**未执行**：运行 V1~V6 SQL、启动数据库或后端服务、修改任何基线代码、`commit` / `push`。

### 上一轮（2026-10-10 第二轮，已提交 `c1d34d2`）

「Codex 交接后的只读复核 + 用户批准的最小修复批次（范围 B）」：修复前端类型检查阻断、更新失效文档、删除死代码 `MessageFieldPanel.tsx`、后端补报文节点树环校验。用户同时确认 `V2`/`V6` 向基线 `sys_menu` / `sys_role_menu` 写入种子数据属于「复用现有 RBAC」（见 D-011）。

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

### 本轮：字段设计表格的展开体验（2026-10-10 18:36，步骤 1）

范围仅限 `frontend/src/pages/intranet/transaction/fields/index.tsx`：

- **展开图标对齐基线**：原先该表格没有传 `expandable`，用的是 antd 默认的 `.ant-table-row-expand-icon`（带边框的方块，内部画 +/−），与菜单管理页/侧边栏子菜单的 chevron 不是同一视觉。现改为基线同款 `tree-expand-arrow` 按钮 + `indentSize: 18`，写法照抄菜单管理页与部门管理页。
- **零新增样式**：`tree-expand-arrow` 定义在基线 `pages/system/shared.css`，而本页 `fields/index.css` 已经 `@import '../../../system/shared.css'`。已核对构建产物 —— 该类出现在 8 个产物 CSS chunk 中，本页本就带着它，**不需要任何基线改动**。
- **默认全展开**：新增 `collectParentIds` 收集所有仍有子节点的节点 id，配合 `expandedRowKeys ?? defaultExpanded` 受控展开 + `onExpandedRowsChange`，语义与菜单管理页一致（用户首次点箭头后转为固定集合）。
- **新增子字段时自动展开父节点**：这是本页特有的坑 —— 草稿行挂在 `parentId` 下，父节点若正收起着，编辑器就藏在里面，用户点「子字段」看不到任何反应。现 `startCreate(parentId)` 会把该父节点并入展开集合；若用户尚未手动操作过，则先把默认全展开物化出来再追加。
- **切换请求/响应时重置展开集合**：换 Tab 等于换了一棵全新的树，`setExpandedRowKeys(undefined)` 让新方向重新回到默认全展开，而不是沿用旧方向的 id 集合。

**本轮未做**（用户明确划出范围）：步骤 2 的搜索与节点类型筛选；步骤 3 的 `intranet/shared` 组件抽取。

### 上一轮修复（2026-10-10 17:58，已提交 `c1d34d2`）

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
| `tsc --noEmit -p tsconfig.app.json`（本轮展开改动后） | **通过**，exit 0 | 受控 `expandedRowKeys` 类型与基线写法一致 |
| `npm.cmd run build`（本轮展开改动后） | **通过**，exit 0，45.05s | 展开逻辑已进入产物 chunk（可按 `收起子字段` 文案检索到） |
| 展开箭头样式可用性 | `.tree-expand-arrow` 出现在 8 个产物 CSS chunk 中 | 本页 `index.css` 已 `@import` 基线 `shared.css`，无需新增样式 |
| 展开交互的实际观感 | **未执行** | 需启动前后端与数据库后人工确认（默认全展开、点箭头收起、新建子字段自动展开） |
| 应用启动 / 接口联调 / SQL 执行 | **未执行** | 本机无 MySQL、Redis；且未获授权执行建表脚本 |

## 新增或修改的主要文件

### 本轮修改（相对 `c1d34d2`，尚未提交）

- `frontend/src/pages/intranet/transaction/fields/index.tsx`（修改）：`tree-expand-arrow` 展开图标 + `indentSize: 18`、默认全展开（`collectParentIds` + 受控 `expandedRowKeys`）、新增子字段时自动展开父节点、切 Tab 重置展开集合。
- `docs/intranet/DECISIONS.md`（修改）：新增 D-012（可编辑树形表格暂不提升为基线组件）与 D-013（不分页，用筛选控制数据量）。
- `docs/intranet/ARCHITECTURE.md`（修改）：新增「动态页面扫描对 `shared/` 的约束」，记录 `pages/**/*.tsx` 全量 glob 带来的菜单组件候选污染与其解决方向。
- `docs/intranet/HANDOFF.md`、`PLAN.md`（修改）：同步本轮现场与状态。

### 上一轮修改（已由用户提交为 `c1d34d2`）

- `frontend/src/pages/intranet/transaction/index.tsx`（修改）：修正 `filters` 可选导致的类型错误与空数组 `NaN` 风险。
- `frontend/src/pages/intranet/transaction/MessageFieldPanel.tsx`（**删除**）：154 行死代码，迁至隐藏路由后已无引用。
- `backend/src/main/java/org/lbl/intranet/transaction/field/service/TransactionMessageFieldService.java`（修改）：`requireParent` 增加环校验；父节点不存在改为独立报错文案。
- `backend/src/test/java/org/lbl/intranet/transaction/field/service/TransactionMessageFieldServiceTest.java`（**新增**）：4 个环校验单元测试。
- `docs/intranet/ARCHITECTURE.md`、`DECISIONS.md`、`HANDOFF.md`、`MIGRATION.md`、`PLAN.md`（修改）：同步事实、状态、环境实测与 D-011。

### 已有（提交 `b638c91`）

- 后端 13 个文件：`org/lbl/intranet/transaction/` 下 Entity / Mapper / Request / VO / Service / Controller，及 `field/` 同构一套。
- SQL 6 个：`backend/src/main/resources/db/intranet/V1~V6`。
- 前端 7 个：`frontend/src/pages/intranet/transaction/` 下 `index.tsx`、`TransactionDialog.tsx`、`fields/index.tsx`、`api.ts`、`types.ts`、`index.css`、`fields/index.css`。

## 未完成事项

- **数据库未落地**：V1~V6 全部仍为「待执行」。表和菜单权限目前不存在，两个页面即使能构建也打不开。完成标准：在可用 MySQL 上按 V1→V6 顺序执行并记录结果。
- **P1 无任何运行验证**：没有启动过后端、没有联调过接口、没有打开过页面。完成标准：至少一次「建表 → 启后端 → 登超管 → 交易 CRUD + 字段树维护」的正向与异常路径实测。
- **P0 未收尾**：`PLAN.md` 中「确认两台设备的运行版本和启动/测试命令」仍为进行中（本机已完成并记入“开发环境实测”，另一台设备与数据库启动项待补）；「演练一次跨设备交接」仍为未开始。「确认内网权限命名、菜单初始化与 SQL 存放位置」已由 D-011 关闭。
- **P1 剩余切片未开始**：源系统 / HBase / ES 资产模型与建表；Excel 导入；脱敏样例数据策略。
- **字段设计表格的步骤 2 / 3 未做**（本轮用户明确划出范围）：步骤 2 是搜索 + 节点类型筛选；步骤 3 是把可编辑树形表格抽成 `intranet/shared` 组件。步骤 2 可直接沿用部门管理页 `filterTree` 的 `prune` 思路（见“下一步建议”）。
- **抽取共享组件前有一个基线约束待决策**：`pages/` 下任何 `.tsx` 都会被动态页面扫描收录为「前端组件」下拉候选，没有 default 导出的组件被选中会渲染失败（现状已影响 `intranet/transaction/TransactionDialog`）。详见 `ARCHITECTURE.md` 的「动态页面扫描对 `shared/` 的约束」。
- **待确认的表结构项仍在等用户**（见 `DECISIONS.md` T 系列），特别是报文子表同父英文名唯一约束、V6 隐藏菜单是否需要独立权限码。
- **展开交互只做了静态验证**：类型检查与构建通过，但“默认全展开 / 点箭头收起 / 新建子字段自动展开 / 切 Tab 回到全展开”四个行为都还没有在浏览器里确认过，需要数据库就绪后补。

## 当前问题和风险

- **未提交改动不会跨设备出现**：本轮 1 个代码文件与 4 份文档改动只在当前工作区，尚未 `commit` / `push`（按用户要求未执行）。切换设备前必须处理，否则另一台设备拉不到。
- **环校验只是应用层防护**：`intranet_transaction_message_field` 没有数据库约束阻止环，直接写库仍可绕过。当前阶段可接受，若后续引入批量导入需重新评估。
- **展开集合是"首次交互即固化"语义**：沿用菜单管理页的 `expandedRowKeys ?? 默认集合` 写法，用户点过任意箭头之后默认值不再生效。本轮已在新增子字段时显式并入父节点、切 Tab 时重置，因此本页行为正确；但若将来把这段逻辑抽成通用组件，这个语义必须一起搬走，否则新页面很容易踩到。
- **菜单种子脚本的幂等边界**：V2/V6 的 `NOT EXISTS` 守卫未排除 `deleted = 1`，而 `sys_menu` 是 `@TableLogic` 逻辑删除且 `route_path` / `permission_code` 上有唯一键。若存在同路径的软删除菜单，脚本会**静默跳过插入**（不报错），菜单永远不会出现。尚未处理，待用户决定。
- **字段级审计信息不完整**：`TransactionMessageFieldController` 的 3 个 `@OperationLog` 都未填 `targetId`，操作日志无法定位到具体字段；基线其他模块普遍填 `targetId = "#id"`。
- **报文子表缺少业务唯一约束**：同父节点下英文名可重复，可能导致字段语义歧义。文档已标记为未确认，未擅自添加约束。
- **环境差异风险**：本机 Maven 不在 PATH，另一台设备是否一致未知；接手时请重新实测本节命令。
- **审批复用仍是硬边界**：`ApprovalTaskMapper` 直接绑定部门调动表，P2 前必须有方案并获批准（详见 `ARCHITECTURE.md`）。

## 下一步建议

1. 用户审阅本轮改动（`git diff`）并决定是否提交；未提交前不要切换设备。
2. 启动前端 `npm run dev`（配一个可达的后端，或用演示数据）先人工确认四个展开行为；这是本轮唯一未验证的部分。
3. 准备可用的 MySQL 实例，按 V1→V6 顺序执行脚本并记录每步结果；执行前确认 V2/V6 是否已存在同名软删除菜单。
4. 端到端实测：交易列表分页/筛选/排序、新增/编辑/删除、字段树增删改与**环拦截提示**、隐藏字段页直达访问、超管菜单可见性。
5. **步骤 2（搜索 + 节点类型筛选）**：可直接复用部门管理页 `filterTree` 里的 `prune` 写法 —— `matches(item) || children.length ? [...] : []`，它保留命中项的祖先链，正好避免“命中子节点因父节点被过滤而跳到顶层”的层级错乱。筛选放前端（数据已全量在内存），搜索字段建议只取 `fieldNameCn` / `fieldNameEn`（可选 `description`），另加节点类型下拉。
6. 实测通过后更新 `HANDOFF.md` 与 `PLAN.md`，把 P1-01 / P1-02 标记为已完成并附验证证据。
7. 再进入 P1 的第三切片（源系统 / HBase / ES 资产模型评审），仍先评审模型、不写业务代码。
8. 顺手可做的低风险项：补 `TransactionMessageFieldController` 的 `targetId`；决定是否处理 V2/V6 的 `deleted` 边界。

## Git 工作区状态和最近提交

- `git status --short --branch` 摘要：`## main...origin/main`，与远程完全同步；工作区含本轮 5 个已跟踪改动（`fields/index.tsx`、`DECISIONS.md`、`ARCHITECTURE.md`、`HANDOFF.md`、`PLAN.md`）。
- 最近提交：`c1d34d2`，提交说明为 `2026年10月10日 18:18:00`（提交说明即时间戳是本仓库现有习惯）；上一轮的修复批次已包含在此提交中并已同步远程。
- 未提交改动**尚未推送**，且未执行 `commit` / `push` / `pull` / `merge` / `rebase` / `reset`。
- 构建产物（`backend/target/`、`frontend/dist/`）已由 `.gitignore` 忽略，未进入改动范围。

## 是否涉及基线公共代码修改

**否。** 本轮只改了 `frontend/src/pages/intranet/transaction/fields/index.tsx` 与 `docs/intranet` 下的文档。展开箭头复用的是基线 `pages/system/shared.css` 里**已存在**的 `tree-expand-arrow` 类，而本页 `index.css` 此前就已 `@import` 该文件，因此没有新增、也没有修改任何基线样式或组件。

逐提交核对：`b7a9212` 只新增 `AGENTS.md` 与 `docs/`；`b638c91` 只新增 intranet 代码与文档；`c1d34d2` 只改 intranet 代码、内网测试与文档。

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
