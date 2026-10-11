# Intranet 开发交接

> 当前工作现场；长期模型见 `ARCHITECTURE.md` / `DECISIONS.md`，计划见 `PLAN.md`，脚本见 `MIGRATION.md`。

## 0. 最新会话（2026-10-11 接手复核，仅文档修复）

- 会话性质：只读复核 + 文档修复。**未修改任何代码、未执行数据库脚本、未执行任何 Git 写操作。**
- HEAD：`2be715c`；上一轮的实现代码与 V10/V11 脚本已由用户提交进该提交，工作区干净。
- 复核结论：P1-03 实现与设计文档基本一致——V8 六张表字段、权限码、交易删除契约、引用保护、ES 固定 `id`、资源集合整体替换均已逐项核对通过。
- 本次实测：`mvn test` BUILD SUCCESS（现存 2/2 通过）；`npm run build` 通过（`tsc --noEmit` + vite 22.10s）。两次构建在默认沙箱下分别因工作区写入被拒与 esbuild 子进程 `spawn EPERM` 失败，放宽权限后通过。
- 用户于本次会话确认的事实：
  1. `a3b8d0b` 中删除暂存文件上传模块、把审计 Agent 工具由 4 个整合为 2 个、删除 5 个测试，**均由用户本人有意完成，不需要恢复**。
  2. `frontend/src/pages/error/pageUnavailable.css` 的 `max-width` 改动由用户本人修改。
  3. V7~V11 已由用户实际执行并验证，**不需要再核对**。
  4. 交易列表的“ESF 服务名/操作名”和“关联索引/HBase 表”两个查询条件不接后端，是用户为验证基线 SmartTable 前端样式有意安排，**不是缺陷**（见 D-026）。
  5. ES 字段 `data_type` 不做服务端强校验，用户认为内网自用系统上该设计过度（见 D-027）。
- 未验证：字段隐藏页的浏览器交互验证与非超管权限验证（2026-10-11 用户确认尚未验证，见 §4）。
- 已确认本期不做：P1 领域定向测试（D-030，用户决定“直接忽略”）。
- 本次会话改动文件：
  - 文档：`docs/AGENT-GUIDE.md`、`docs/LOG-AUDIT-GUIDE.md`、`docs/intranet/{ARCHITECTURE,DECISIONS,HANDOFF,MIGRATION,PLAN}.md`
  - **基线新增（用户批准，方案 B）**：`frontend/src/components/EllipsisText/{EllipsisText.tsx,ellipsisText.css,index.ts}`
  - **基线修改（用户批准）**：`styles/global.css`（新增 opt-in 的 `page-fill` / `card-scroll` 布局约定，见 D-031）；`pages/account/approvals/index.tsx`、`pages/system/loginlog/index.tsx`、`pages/system/operatelog/index.tsx`、`components/ai/artifacts/common/ArtifactTable.tsx`（长文本列改用 `EllipsisText`，见 D-029）
  - intranet 修改：`pages/intranet/components/InlineEditableTable.tsx`、`components/FieldText.tsx`、`components/field-text.css`、`transaction/index.tsx`、`transaction/index.css`、`transaction/fields/index.{tsx,css}`、`dataasset/index.tsx`、`dataasset/index.css`、`dataasset/fields/index.{tsx,css}`
  - intranet 中途新增、随后被基线组件取代并删除：`pages/intranet/components/EllipsisText.tsx`、`ellipsis-text.css`（最终仓库只有一份实现）
  - `frontend/src/components/SmartTable/SmartTable.tsx` 当前显示的改动是用户本地的格式化，不是本会话产生；本会话**没有**按方案 A 修改 SmartTable 的列处理语义

## 1. Git 与阶段

- 日期：2026-10-11（Asia/Shanghai）
- 分支：`main`，跟踪 `origin/main`
- 上一轮开始时 HEAD：`a3b8d0b`；上一轮改动已由用户提交为 `2be715c`，当前 ahead/behind = 0/0 且工作区干净
- 当前阶段：P1-03 数据资产字段设计与通用查询能力收尾
- 上一轮未执行 commit、push、pull、merge、rebase、reset、clean 或数据库脚本

## 2. 上一轮实现（P1-03 数据资产字段设计与通用查询能力收尾）

### 数据资产字段隐藏页

- 新增 `/intranet/dataasset/fields?assetType=HBASE|ES&assetId=...`。
- HBase/ES 列表的“字段设计”跳转隐藏页；返回时通过 URL `tab` 恢复原 HBase/ES Tab。
- 字段页采用与交易字段一致的行内新增、编辑、保存和取消。
- HBase 字段列：列族、字段名、数据类型、顺序、说明。
- ES 字段列：字段名、数据类型、可搜索、可排序、顺序、说明。
- ES 固定 `id` 字段编辑时不能改名，也不显示删除操作。
- 字段名支持前端快速搜索；编辑期间禁用搜索和新增；不分页。
- 说明使用单行输入 + Popover TextArea，展示时省略并悬停查看全文。

### 废弃代码清理

- 已删除数据资产页字段 Drawer、字段 Table、字段加载/编辑状态和相关 API 调用。
- 已删除 `HbaseFieldDialog`、`EsFieldDialog` 以及对应依赖和导入。
- 交易字段页当前本来就没有旧字段 Drawer/Modal；本轮确认后未误删交易详情 Drawer 或交易新增/编辑 Modal。

### intranet 内部共享

- 新增 `frontend/src/pages/intranet/components/InlineEditableTable.tsx`。
- 新增 `FieldText.tsx` / `field-text.css`。
- 交易字段、HBase 字段、ES 字段共同使用上述编辑骨架和长文本交互。
- 组件没有提升为基线业务组件；树形、字段列、校验、权限和 API 仍由页面负责。

### 基线 SmartTable（用户已批准）

- 修改 `frontend/src/components/SmartTable/SmartTable.tsx` 并新增组件专属 CSS。
- `searchConfig` 新增字段标签、宽度、自定义 render、高级条件和展开/收起。
- 新增 `searchActions`，控制查询/重置文字、重置显示、回车提交和默认展开。
- 查询仍使用 `search` / `submittedSearch` 双状态，只有 submit 后才请求。
- 既有 `searchRender` 页面逻辑不变。

### 交易列表搜索样式与资产表单提示

- 交易列表改用 SmartTable `searchConfig`，展示“交易名称/编码”“ESF 服务名/操作名”“关联索引/HBase 表”三个条件以及查询、重置按钮。
- 按用户要求，本轮只有交易名称/编码继续传给既有后端 `keyword`；ESF 和关联资源值不加入请求参数，也不会实际过滤数据。
- 2026-10-11 复核更正：这两个条件**刻意不接后端**，用途是验证基线 SmartTable `searchConfig` 的前端样式；属于 D-026 确认的设计，不是待修复缺陷，后续会话不要补齐。
- HBase 表、ES 索引新增/编辑 Modal 的表名、索引名、命名模式、数据深度、RowKey、关联 HBase 表和说明等字段已补齐 placeholder。

### 数据库脚本

- V10：虚构数据资产演示数据，上一轮生成，本轮未执行。
- V11：新增数据资产字段设计隐藏菜单及超管授权，待执行。
- 用户已在页面看到演示数据和资产页面，但本会话没有查询数据库，故 V8~V10 的具体执行状态记录为“待目标环境复核”。

## 3. 验证

### 上一轮验证结果

| 项目 | 结果 |
| --- | --- |
| `npm run build` | 通过；最新搜索样式和 placeholder 修改后再次构建成功，5311 个模块完成转换，仅有既存 chunk 体积告警 |
| 旧字段 Drawer/Modal 搜索 | 数据资产目录无 `HbaseFieldDialog`、`EsFieldDialog`、`fieldOwner`、字段 Drawer 残留 |
| 后端测试 | 本轮未重复执行；只新增 SQL，无 Java 改动。上一轮当前仓库现存测试 2/2 通过，但 P1 定向测试缺失 |
| 数据库脚本 | 本轮未执行；V11 明确待执行 |
| 浏览器人工联调 | 未执行 |

### 2026-10-11 接手复核复现结果

| 项目 | 结果 |
| --- | --- |
| `mvn.cmd -o -B test -f backend\pom.xml` | BUILD SUCCESS；Tests run: 2, Failures: 0, Errors: 0，耗时 36.8s。首次在 workspace-write 沙箱下因 `backend\target\classes\application-dev.yml` 写入被拒而失败，放宽权限后通过 |
| `npm.cmd run build`（frontend） | 通过；`tsc --noEmit` 无错误，vite `✓ built in 22.10s`，仅既存 chunk >500kB 告警。首次在沙箱下因 esbuild 子进程 `spawn EPERM` 失败，放宽权限后通过 |
| 表结构 vs `ARCHITECTURE.md` §5 | V8 六张表逐字段一致 |
| `@PreAuthorize` vs V2/V6/V9/V11 种子 | 一致，另 `/options` 额外放行 `intranet:transaction:list`、`intranet:transaction:update` |
| 旧字段组件残留检索 | `HbaseFieldDialog` / `EsFieldDialog` / `fieldOwner` 零命中 |
| 构建后 `git status` | 干净；`backend/target`、`frontend/dist` 已被 `.gitignore` 忽略 |
| 数据库脚本 | 本会话未查库；执行状态一律以用户确认为准（见 §0、§4） |

## 4. 待用户执行与验收

- **V7~V11：已由用户实际执行并验证**（2026-10-11 用户确认；本会话未查库复核），原“执行 V11”待办关闭。
- 以下交互与权限验证**尚未执行**（2026-10-11 用户确认隐藏字段页还未验证）：
  1. 分别从 HBase、ES 列表进入字段设计，验证新增、编辑、删除、搜索、说明展开和返回 Tab。
  2. 验证 ES `id` 字段不可改名且不能删除。
  3. 用非超管角色验证隐藏菜单授权和 `intranet:dataasset:*` 接口权限。
- 已确认本期不做：P1 领域定向测试（D-030）。删除保护、固定 `id`、资源整体替换和交易删除顺序因此没有自动化回归保护。
- **布局改动必须在浏览器目视确认（D-031，构建验证不出来）**：
  1. 字段数据 0~2 行时，卡片只有内容高度（与交易列表一致），不再撑满整屏；
  2. 字段数据超出视口时，**只有卡片内部**出现纵向滚动条，页面标题、卡片内的工具栏/Tabs 都不动；
  3. 滚动时表格列名保持吸顶（表头不随表体滚动），且右侧「操作」固定列与表头**不互相遮挡**；
  4. 把窗口高度压到 700px 以下（`global.css` 的 `page-head` 竖排断点）再复验一次；
  5. 数据很多时点「新增字段」：视图应自动滚到最下方的新增行（交易字段页与 HBase/ES 字段页都要看）。

## 5. 风险与边界

- V7~V11 已执行。V2/V6/V9/V11 沿用既有菜单脚本的软删除幂等边界（同路径软删除记录会导致静默跳过）仍然存在；本次执行未观察到跳过，该边界继续保留在 `MIGRATION.md`，不作为已解决问题。
- `pages/**/*.tsx` 动态扫描仍会发现组件型 TSX，用户已接受通过菜单配置规范规避；本轮没有修改路由扫描基线。
- SmartTable 是上一轮唯一修改的基线公共代码（`SmartTable.tsx` + 新增 `SmartTable.css`），按 D-025 记录为用户批准；前端全量构建通过，仍建议人工回归用户、角色、部门、日志、交易和资产列表。
- 未申报的基线改动已澄清：`frontend/src/pages/error/pageUnavailable.css` 的 `max-width: 720px → 100%` 由用户本人修改，不属于内网任务范围。
- 当前 HEAD 中没有 P1 领域单元测试，构建通过不能替代业务行为测试。用户已决定本期不补（D-030），因此删除保护、固定 `id`、资源整体替换与交易删除顺序没有回归保护，风险由用户接受；文档不得据此宣称 P1 领域行为已验证。
- `a3b8d0b` 中 intranet 之外的改动（删除 `org/lbl/file` 暂存文件上传模块、审计 Agent 工具由 4 个整合为 2 个、删除 5 个测试）**经用户确认为本人有意完成，不需要恢复**。其中的 P1-02 领域测试 `TransactionMessageFieldServiceTest` 缺失会削弱 P1 的回归能力；用户已决定本期不补（D-030）。
- **`mvn test` 不是只读操作**：`JasyptEncryptorTest` 是 `@SpringBootTest`，会启动完整 Spring 上下文并执行 `SystemPermissionInitializer`（`ApplicationRunner`），因此会连接 `application-local.yml` 配置的远程 MySQL，并可能为超管补写 `sys_role_menu` 授权行。它只涉及基线菜单与按钮权限，不创建任何 intranet 表、不执行 `db/intranet` 脚本。详见 `MIGRATION.md` 第 4 节。
- 以下三项经用户确认**明确不做**，后续会话不要当作缺口或缺陷：交易列表 ESF/关联资源条件不接后端（D-026）、ES 字段 `data_type` 不做服务端强校验（D-027）、P1 领域定向测试（D-030）。
- **基线公共代码修改披露（AGENTS.md §2 / §6.7）**：本会话新增基线组件 `frontend/src/components/EllipsisText/`，并迁移 `pages/account/approvals/index.tsx`、`pages/system/loginlog/index.tsx`、`pages/system/operatelog/index.tsx`、`components/ai/artifacts/common/ArtifactTable.tsx` 的长文本列展示；用户 2026-10-11 明确选择方案 B 并批准（见 D-029）。改动只涉及各列的 `ellipsis` 配置与 render 包装，**未修改 `SmartTable` 的列处理或任何表格语义**。回归：`tsc --noEmit` 与 `npm run build` 均通过；仍建议人工回归审批列表、登录日志、操作日志与 AI 报告表格四处界面。
- **基线公共代码修改披露（第二项）**：本会话在基线 `frontend/src/styles/global.css` 新增 opt-in 布局约定 `page-fill` / `card-scroll`（用户 2026-10-11 批准，见 D-031），让"标题固定 + 只有卡片内表格滚动"不再依赖 `calc(100vh - N)` 魔法数字。该约定**默认不生效**，只有显式加类的页面受影响，本次仅 `intranet/{transaction,dataasset}/fields` 使用。配套改了内网共享组件 `pages/intranet/components/InlineEditableTable.tsx`：它自带 `.card-scroll` 容器、统一给 Table 传 `sticky`（表头固定，因为给 `th` 写 `position: sticky` 会被 antd 的 `.ant-table-content` 困住而无效），并在进入编辑时把目标行滚进视野。**布局效果构建验证不出**，必须按 §4 的清单目视确认；已知降级为极矮视口下卡片溢出。

## 6. 上一轮文件

新增：

- `frontend/src/components/SmartTable/SmartTable.css`
- `frontend/src/pages/intranet/components/InlineEditableTable.tsx`
- `frontend/src/pages/intranet/components/FieldText.tsx`
- `frontend/src/pages/intranet/components/field-text.css`
- `frontend/src/pages/intranet/dataasset/fields/index.tsx`
- `frontend/src/pages/intranet/dataasset/fields/index.css`
- `backend/src/main/resources/db/intranet/V11__seed_intranet_data_asset_field_design_menu.sql`

修改：

- `frontend/src/components/SmartTable/SmartTable.tsx`
- `frontend/src/components/SmartTable/index.ts`
- `frontend/src/pages/intranet/dataasset/index.tsx`
- `frontend/src/pages/intranet/dataasset/AssetDialogs.tsx`
- `frontend/src/pages/intranet/transaction/fields/index.tsx`
- `frontend/src/pages/intranet/transaction/fields/index.css`
- `docs/intranet/ARCHITECTURE.md`
- `docs/intranet/DECISIONS.md`
- `docs/intranet/PLAN.md`
- `docs/intranet/MIGRATION.md`
- `docs/intranet/HANDOFF.md`

> 更正（2026-10-11 接手复核）：上一轮的 V10 与 V11 已随 `2be715c` 提交，不再是“未提交文件”。当前工作区干净，无未提交改动。
