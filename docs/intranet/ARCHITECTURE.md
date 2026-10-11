# Intranet 总体架构

> 状态：初始架构基线  
> 更新日期：2026-10-11
> 原则：本文区分“仓库已确认能力”“已确认方向”和“待确认细节”，不把规划写成现状。

## 1. 当前工程基线（已从代码确认）

### 后端

- Java 17、Spring Boot 3.4.4。
- Spring Security + JWT/会话能力，控制器使用权限码进行方法级鉴权。
- MyBatis-Plus，当前关系型数据库驱动为 MySQL。
- Redis 支撑跨实例实时事件，WebSocket 向前端推送通知和审批刷新事件。
- 已有用户、部门、角色、菜单、权限、部门调动审批、通知、文件暂存、操作日志和 Agent 工具体系。
- `AgentTool` 由 `ToolRegistry` 自动发现，并按调用者权限过滤和执行。

### 前端

- React 18、TypeScript、Vite 6、Ant Design 5、Redux Toolkit。
- 菜单和权限由后端配置驱动，页面组件通过 `src/pages/**/*.tsx` 构建期扫描。
- 已有主题、布局、表格、文件上传、状态标签、代码查看、图表、AI 助手等公共能力。
- 已安装 `@antv/g6` 5.x，且已有 `frontend/src/components/G6Graph/` 生命周期封装。

## 2. 目标目录边界

以下为已确认的组织方向，实际创建业务代码时再细化子包：

```text
backend/src/main/java/org/lbl/intranet/
  requirement/       需求管理
  transaction/       交易资产与版本
  dataasset/         ES、HBase、源系统资产
  lineage/           ETL、映射、依赖与血缘
  impact/            变更事件与影响分析
  assistant/         对话助手适配（P5）
  common/            仅供 intranet 内部复用

frontend/src/pages/intranet/
  requirement/
  transaction/
  dataasset/
  lineage/
  impact/
  shared/            仅供 intranet 页面复用
```

前端选择 `src/pages/intranet/` 是为了同时满足目录隔离与现有动态页面扫描机制，不需要修改路由基础设施。API、类型和专属组件是否全部内聚在该目录，待 P1 首个页面落地时按现有代码风格确认。

### 动态页面扫描对 `shared/` 的约束（2026-10-10 从代码确认）

`componentRegistry` 用 `import.meta.glob(['../pages/**/*.tsx', ...])` 收录页面，并把每个命中的模块强制断言为 `{ default: ComponentType }`。由此产生两个必须注意的后果：

1. `pages/` 下**任何** `.tsx` 都会进入 `availableComponents()`，也就是「菜单管理 → 前端组件」下拉的候选列表；
2. 只有命名导出、没有 default 导出的组件（例如 `intranet/transaction/TransactionDialog`）同样会出现在该下拉里，一旦被选为菜单组件，`React.lazy` 取不到 `default`，页面渲染失败。

因此 `frontend/src/pages/intranet/shared/` 下的共享 `.tsx` 组件也会成为菜单候选。2026-10-11 用户决定暂不修改扫描规则，由菜单配置操作规范避免误选非页面组件；该风险当前接受，不作为 P1 开发阻塞。若未来需要从技术上消除，可选方向：

- 把页面注册表收敛为只收录 `**/index.tsx`——全仓库 9 个菜单 `component` 值全部以 `/index` 结尾（已核对 `init_data.sql` 与 V2/V6），所以该约定是安全的。这属于**基线修改**，需按 D-003 批准；
- 或把内网共享组件放到 `pages/` 之外（需要先修改本节的目录约定）；
- 纯逻辑（无 JSX）的共享代码用 `.ts` 文件，不会被 glob 收录。

`availableComponents()` 目前只有 `MenuDialog` 一处消费者（构建期映射的 keys + sort）。

## 3. 业务模块划分

| 模块 | 主要职责 | 不承担的职责 |
| --- | --- | --- |
| 需求管理 | 新增交易、交易变更、交易调用需求的草拟、提交、审批状态和关联对象 | 不直接替代审批中心、通知中心 |
| 交易资产 | 交易基础信息、输入/输出字段、数组对象、版本快照及版本状态 | 不保存源系统物理字段的全部治理信息 |
| 数据资产 | 源系统表/字段、HBase 表与字段、ES 索引与字段及定位信息 | 不负责审批流转 |
| 血缘管理 | ETL 加工、字段映射、查询依赖、跨层级和字段级血缘 | 不把图形界面作为事实存储 |
| 影响分析 | 对扩位、下线、逻辑变更等事件执行确定性下游遍历、汇总、留痕 | 不让大模型自行猜测依赖 |
| 助手集成 | 将已计算的影响结果、证据和可解释路径提供给现有对话助手 | 不绕过权限或直接修改资产 |

### 报文节点树的结构约束（2026-10-10 确认）

交易的请求/响应报文字段以单表 `intranet_transaction_message_field` 按 `(transaction_id, message_side)` 构成一棵树，根节点用 `parent_id = 0` 表示。结构上必须保持**无环**：前端按 `parent_id` 建树，环上的节点找不到根，会让整棵子树从界面静默消失而不是报错。因此服务层在写入前沿目标父节点向上回溯，回到自身即拒绝；数据库层当前没有对应约束，直接写库仍可绕开，若后续引入批量导入需重新评估。

同一交易、同一报文方向、同一父节点下，`field_name_en` 必须唯一。服务层在新增和修改前预检，V7 迁移脚本增加数据库唯一索引；执行 V7 前必须先检查存量重复记录，不允许脚本自动删除或改名。

交易采用物理删除。删除交易时，服务层在同一事务内先删除该交易现有的全部报文字段，再删除交易主记录，避免孤立字段。后续新增交易关联表时，必须把它们纳入同一删除契约并补回归测试，不得仅依赖当前两张表的实现。

## 4. 需求、审批与交易版本

### 已确认的概念关系

```text
需求（新增交易 / 交易变更 / 交易调用）
  └─ 提交后进入审批流程
       ├─ 驳回：需求回到可修订或终止状态（具体状态待确认）
       └─ 通过：触发受控的业务生效动作
            ├─ 新增交易 -> 创建交易及首个有效版本
            ├─ 交易变更 -> 基于目标版本产生新版本，不覆盖历史版本
            └─ 交易调用 -> 建立调用/依赖关系；是否产生交易版本待确认
```

- 需求是“为什么变更、由谁申请、审批到哪里”的流程载体。
- 交易是稳定业务身份；交易版本是某一时点的结构和配置快照。
- 审批记录必须关联需求，不用交易版本本身替代流程记录。
- 审批通过后才允许形成或激活版本；草稿版本的创建时机、版本号规则、回滚策略均为待确认。
- 版本应能追溯来源需求和审批结论，历史版本不可被无痕覆盖。

### 现有审批复用边界

- 已有 `approval` 包定义了共享审批生命周期思想，通知服务和 `approval.request.updated` 实时事件具有一定通用性。
- 但当前 `ApprovalTaskMapper` 直接查询部门调动请求/步骤表，并固定 `DEPARTMENT_CHANGE` 业务类型；因此目前不能直接宣称支持内网需求。
- P2 前必须比较两种方案：
  1. 在 `intranet` 内实现审批适配，并接入现有通知与页面交互；
  2. 将公共审批任务聚合扩展为多业务类型。
- 方案 2 会修改基线公共代码，必须先说明影响和替代方案并取得用户批准。

## 5. ES 检索与 HBase RowKey 回表

### 总体关系（2026-10-11 用户确认）

```text
路径 A：条件不能直接定位 RowKey
查询入参
  -> ES 按条件检索
  -> 读取 ES 文档中专门存储的 HBase RowKey 字段
  -> 使用 RowKey 回表查询 HBase
  -> 获取真实全量数据

路径 B：入参已满足 RowKey 构造条件
查询入参
  -> 根据已知规则定位/构造 RowKey
  -> 跳过 ES，直接查询 HBase
  -> 获取真实全量数据
```

- HBase 是真实全量数据的权威存储；ES 承担条件检索和 HBase RowKey 定位，不作为完整业务事实的权威源。
- 通过 ES 查询时，ES 文档中必然存在一个明确字段保存 HBase RowKey；不需要用 ES `_id` 充当 RowKey，也不在 ES 命中后再用多字段构造 RowKey。
- 当交易入参已能满足对应 HBase 表的 RowKey 规则时，可直接查询 HBase，无需访问 ES。
- RowKey 规则仅作为 HBase 表的展示型元数据，不额外拆分 RowKey 组件实体，也不由 P1 对规则文本做解析或执行。
- 一个交易可关联多个查询资源，也可访问多张 HBase 表。交易实现中可能存在条件分支、先查表 A 再查表 B、同时查询多表后合并结果等编排逻辑。
- 一个 ES 索引在业务上只对应一个 HBase 表，且只有一个 ES 字段保存该表的完整 RowKey；一张 HBase 表可被多个 ES 索引定位。
- 系统同时管理多个 HBase 数据集市，例如 `HDS:TABLE` 和 `PBM:TABLE`；HBase 表资产必须显式保存集市名，表的稳定业务标识由“集市名 + 表名”构成。

### 待确认

- 直连 HBase 路径中，“交易入参已满足 RowKey 条件”是否只做人工元数据说明，还是未来需要系统可执行的入参映射。
- 交易内部的条件分支、执行顺序、并行查询和结果合并是否需要在 P3 建模为可视化的查询处理图。
- 索引刷新机制、延迟目标、失败补偿、重建与一致性校验方案。
- HBase/ES 集群版本、Java 客户端、认证方式、网络边界和本地开发替身。

### ES 逻辑索引族与物理分片索引（2026-10-11 用户确认）

- 同一套 ES 数据可因数据量按年或按月拆成多个物理索引，例如 `index_2016`、`index_2017` 或按年月后缀拆分；这些物理索引共享相同字段 mapping、HBase 回表目标和 RowKey 字段。
- P1 把这组索引建模为一个“逻辑索引族”，字段定义、HBase 表关联和交易资源关联只保存一份。
- 逻辑索引族保存可展示的索引命名模式和拆分规则；例如模式 `index_{yyyy}`、规则“按年”。它们不被当前系统解析或用于动态计算索引名。
- P1 不为 `index_2016`、`index_2017` 等物理实例分别建资产记录；如后续需要监控物理索引容量、健康状态或生命周期，再从 ES 动态发现，不与当前元数据混存。
- ES 中保存完整 HBase RowKey 的字段名由业务统一约定为 `id`，不再为每个逻辑索引族保存 `rowkey_field_id`。服务层按 `id` 校验并禁止改名或删除。

### HBase 表锁元数据（设计边界与数据字典均已确认）

- 数据组的 ETL 作业在跑批期间会设置表锁，应用读取 HBase 数据时依据同一锁契约执行其他协调逻辑。
- 该锁契约附着于 HBase 表，不是 ES 字段映射或血缘关系，因此应在 P1 HBase 表资产中设计，不推迟到 P3。
- 同一张 HBase 表只有一个固定锁标识。P1 只在 HBase 表资产上保存该展示值，允许为空；不建锁实体或锁关联表。
- 锁的真实运行状态存储在 Redis，但当前应用不会读取资产页保存的锁标识；P1 不接入 Redis 锁状态，不保存当前是否已加锁、持有者、加锁时间或过期时间。
- P3 血缘可展示 HBase 表的锁元数据，但不把表锁建模为血缘节点或关系。
- 已确认数据字典：HBase 表使用 `lock_key VARCHAR(255) NULL`；前端空输入在后端归一为 `NULL`，页面展示为 `-`，不保留 `NULL` 和空字符串两种无锁语义。

### P1-03 数据资产模型（已按确认数据字典实现）

> 2026-10-11 复核：本节的概念关系已由 V8 脚本和 `org.lbl.intranet.dataasset` 包实现，见下方“已实现的表结构与代码路径”。表名、字段名、类型和长度是**当前实现事实**，其中三处业务语义仍待用户确认（见本节末尾“仍待确认”），不得据此认为数据字典全部定稿。

```text
源系统
  ├─ HBase 表
  │    ├─ 集市名 + 表名
  │    ├─ HBase 字段（默认 `info` 列族 + Qualifier/逻辑字段）
  │    └─ RowKey 规则（表级展示型元数据）
  └─ ES 逻辑索引族
       ├─ 索引命名模式/拆分规则
       └─ ES 字段（当前实现为单层字段，无嵌套层级）

交易
  └─ 交易关联查询资源
       ├─ ES 检索回表资源：ES 索引（由索引确定 RowKey 字段和 HBase 表）
       └─ HBase 直查资源：HBase 表

交易内部逻辑（P1 不建模）
  ├─ 条件分支
  ├─ 先 A 后 B
  └─ 多表查询后整合
```

评审建议：

- HBase 和 ES 按各自物理结构建模，不用一张多态资产表混存两类专属属性。
- HBase 字段和 ES 字段都保留稳定数据库 ID，P3 的字段映射、血缘和影响分析直接引用该 ID。
- HBase 表除集市名、表名、RowKey 规则和锁标识外，再保存“数据量”和“数据深度”两个静态展示值，不影响实际查询逻辑。
- HBase 字段保留列族且默认为 `info`，同时保留字段名、数据类型（允许直接写 `varchar(32)` 等完整表达）、说明和展示顺序；不保存中文名，不单独建长度/精度字段。
- ES 只有单层字段，字段类型范围为 `keyword`、数值类型、`date`、`text`，不建父子树。ES 字段保留字段名、数据类型、是否可搜索、是否可排序、说明和展示顺序；不保存中文名。
- P1-03 新表统一包含 `created_by`、`created_time`、`updated_by`、`updated_time`，实体使用 MyBatis-Plus `FieldFill.INSERT/INSERT_UPDATE` 复用已有 `MybatisConfig.auditHandler`，无需修改基线配置。
- RowKey 规则按用户确认收敛为 HBase 表的展示型元数据，不建 RowKey 规则表或组件表。
- 交易关联应能区分“ES 检索后回表”和“HBase 直查”两类资源访问；P1 只记录可能被访问的资源集合，不用顺序号或主/备标记猜测真实代码分支。交易报文字段与数据资产字段的映射仍属于 P3。
- P1 先实现 MySQL 中的元数据管理；真实 ES/HBase 连接、认证和数据读取等待集群版本与网络边界确认后另行实施。

首轮必须确认的业务分歧（2026-10-11 状态：第 2 项由代码按 `id` 实现但用户仍未确认，第 3 项字段名已落地、业务语义待确认，第 1、4 项仍未答复；详见下文“仍待确认”）：

1. 直查 HBase 所需的“入参到 RowKey”关系是否仅做文档展示；如果未来要由本系统执行构造，将需要另行设计可执行映射，不能仅依赖 RowKey 说明文本。
2. ES 固定 RowKey 字段的准确名称，以及是否需要禁止修改/删除该字段。
3. HBase 表“数据量”和“数据深度”的字段名、长度和样例值。
4. 源系统是业务归属元数据，还是需要区分业务系统、HBase 集群和 ES 集群三类独立的管理对象。

### 已实现的表结构与代码路径（2026-10-11 代码复核）

脚本 `backend/src/main/resources/db/intranet/V8__create_intranet_data_asset_tables.sql`（六张表，均含 `created_by/created_time/updated_by/updated_time`）：

| 表 | 关键字段 | 唯一约束 |
| --- | --- | --- |
| `intranet_hbase_table` | `market_name`、`table_name`、`description`、`rowkey_rule`、`lock_key`、`data_volume`、`data_depth` | `(market_name, table_name)` |
| `intranet_hbase_field` | `hbase_table_id`、`column_family`（默认 `info`）、`field_name`、`data_type`、`description`、`sort_order` | `(hbase_table_id, column_family, field_name)` |
| `intranet_es_index` | `index_name`、`index_pattern`、`partition_rule`、`hbase_table_id`、`description` | `(index_pattern)` |
| `intranet_es_field` | `es_index_id`、`field_name`、`data_type`、`searchable_flag`、`sortable_flag`、`description`、`sort_order` | `(es_index_id, field_name)` |
| `intranet_transaction_hbase_resource` | `transaction_id`、`hbase_table_id` | `(transaction_id, hbase_table_id)` |
| `intranet_transaction_es_resource` | `transaction_id`、`es_index_id` | `(transaction_id, es_index_id)` |

- 后端：`org.lbl.intranet.dataasset.hbase`（表与字段）、`.es`（索引与字段）、`.relation`（交易关联），均为 `Controller` + `Service` + `Mapper` + `entity/request/vo` 分层；查询侧统一按“更新时间倒序 + id 倒序”分页，编号重复在服务层预检。
- 删除与引用保护：HBase 表被 ES 索引或交易关联时拒绝删除；ES 索引被交易关联时拒绝删除；删除资源时在同一事务内先删字段；ES 固定 RowKey 字段禁止删除。V8 **未建数据库外键**，绕过应用直写库仍可产生孤立引用。
- 权限码：`intranet:dataasset:list/add/update/delete`，由 V9 种子脚本写入 `sys_menu` / `sys_role_menu`；资产选项接口额外允许 `intranet:transaction:list` 与 `intranet:transaction:update`。
- 前端：`frontend/src/pages/intranet/dataasset/{index,AssetDialogs,api,types}.tsx|ts` 提供 HBase/ES 两个 Tab 的资产分页和资产对话框；字段入口跳转隐藏页 `frontend/src/pages/intranet/dataasset/fields/index.tsx`，使用行内可编辑表格，不再保留字段抽屉或字段 Modal。交易列表通过 `frontend/src/pages/intranet/transaction/DataResourceDialog.tsx` 配置资源集合。
- intranet 共享：`frontend/src/pages/intranet/components/InlineEditableTable.tsx` 和 `FieldText.tsx` 被交易、HBase、ES 字段设计页复用；业务规则留在页面内。
- 基线 SmartTable：`searchConfig` 支持多条件查询、显式查询/重置、高级条件展开和自定义控件，见 D-025；既有 `searchRender` 页面保持兼容。
- 交易删除契约已扩展到关联表：`TransactionAssetService.remove` 在同一事务内先删 HBase 关联、ES 关联、报文字段，再删交易（见 D-015、D-024）。
- 当前 HEAD 的 `backend/src/test` 仅保留 `JasyptEncryptorTest.java`，原 P1-03 定向测试不在仓库中。
- **验证状态**：2026-10-11 执行 `mvn test` 已完成主代码编译并运行现存测试 2/2；因缺少 HBase/ES/交易资源领域测试，删除保护、固定 `id` 约束和整体替换仍需补充自动化验证。

### 仍待确认（代码已落地，但必须在 P1-03 定稿前答复）

1. 源系统是否需要在后续阶段独立建模，以及是否区分业务系统、HBase 集群和 ES 集群（P1 当前无源系统表）。
2. 直查 HBase 的“入参到 RowKey”关系未来是否需要升级为可执行映射；P1 已确认仅保存 `rowkey_rule` 自由文本，不解析、不执行。

## 6. 统一血缘模型

关系型数据库优先承载可治理、可审计的血缘元数据；G6 负责展示，不作为事实源。

### 节点类型

| 层级 | 节点示例 |
| --- | --- |
| 源系统 | 系统、库/模式、表、字段 |
| ETL | 作业、步骤、输入、输出、转换表达式 |
| HBase | 命名空间、表、列族、Qualifier/逻辑字段 |
| ES | 索引/别名、字段 |
| 交易 | 交易、交易版本、输入字段、输出字段、数组对象/子字段 |

### 关系类型

- `CONTAINS`：资产层级包含。
- `READS_FROM` / `WRITES_TO`：作业或步骤读写资产。
- `MAPS_TO` / `DERIVES_FROM`：字段映射或加工派生。
- `INDEXES_FROM`：ES 字段由 HBase 或其他数据字段建立索引。
- `EXPOSES_AS`：数据字段暴露为交易输入/输出字段。
- `CALLS` / `DEPENDS_ON`：交易调用或查询依赖。

关系名是当前概念模型，数据库枚举和命名规范在 P3 确认。每条关键关系至少需要来源、目标、关系类型、有效状态、版本/有效期、来源证据及最后确认时间；是否保存表达式全文和解析结果待确认。

### 数组对象

数组对象应作为可寻址的结构节点，子字段保留父路径和顺序/键语义，使字段级血缘能落到数组内部字段。数组嵌套深度、路径语法以及与 ES `nested`、HBase Qualifier 的对应规则待确认。

## 7. 影响分析

影响分析采用“确定性图遍历 + 可解释证据 + 人工确认”的总体思路：

1. 建立变更事件：对象、字段、变更类型（扩位/下线/逻辑变更）、计划时间和说明。
2. 将发生变更的源系统字段作为种子节点。
3. 按允许的下游关系和有效版本遍历：源字段 -> ETL -> HBase -> ES -> 交易字段/版本 -> 调用方。
4. 结合关系类型和变更类型应用规则，区分确定影响、潜在影响和信息不足。
5. 聚合受影响交易、版本、需求、责任人和处理状态，并保存命中路径与证据快照。
6. 由 G6 展示影响子图；用户可从汇总下钻到字段级路径。
7. 后续 Agent 只对确定性结果做检索、解释和摘要，不自行创建不存在的血缘边。

### 待确认

- 不同变更类型的规则矩阵，例如“扩位”对长度校验、类型转换和下游字段的判定条件。
- 遍历深度、环路处理、跨版本选择、时间点查询和失效关系策略。
- 影响结果是否持久化、何时重算、如何标记人工确认与误报。
- 数据负责人和交易负责人的来源。
- 规模、响应时间目标及是否需要图数据库；P4 前不预设引入图数据库。

## 8. 现有公共能力复用策略

| 能力 | 复用方式 | 边界/风险 |
| --- | --- | --- |
| 登录与 RBAC | 复用当前会话、权限码、菜单及前后端权限控制 | 内网权限码和菜单需独立命名；配置方式待 P1 确认 |
| 统一 API | 复用 `Result`、`PageResult`、异常处理、请求封装 | 不为内网复制一套协议 |
| 审批 | 复用生命周期概念、审批入口体验、通知与实时刷新 | 当前聚合 SQL 绑定部门调动；公共扩展需批准 |
| 通知与实时 | 复用 `NotificationService`、业务类型/业务 ID、WebSocket 通道 | 新事件命名、跳转解析和权限过滤需设计 |
| UI 与主题 | 复用 Ant Design、布局、主题、表格、状态标签等 | 专属组合组件放 `intranet/shared` |
| 血缘图 | 优先复用现有 `G6Graph` | 大图性能、布局、折叠和交互能力需 P4 验证 |
| Agent | 复用 `AgentTool`、权限过滤、报告/Artifact 展示 | 只能读取授权后的确定性分析结果；P5 实施 |
| 审计 | 评估复用操作日志切面 | 是否满足内网字段脱敏与审计要求待确认 |

## 9. 安全与可迁移性

- 仓库只保存结构、脱敏示例和占位配置，不保存真实内网数据与连接信息。
- 外部系统访问必须通过配置注入和最小权限账户；环境变量名可提交，真实值不得提交。
- 迁移对象持续登记到 `MIGRATION.md`，包括代码、SQL、依赖、权限菜单和环境项。
- 新增跨模块依赖时记录方向，避免内网模块反向渗透到基线业务。
- 数据可见性、字段脱敏、审计留痕和生产网络拓扑在接入真实内网环境前必须专项确认。
