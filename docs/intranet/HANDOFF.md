# Intranet 跨设备交接

> 本文件保存当前工作现场。历史设计结论放入 `DECISIONS.md`，长期计划放入 `PLAN.md`，架构现状放入 `ARCHITECTURE.md`，迁移清单放入 `MIGRATION.md`。
> 接手方必须用真实代码和 Git 命令复核本文件；本文只记录能从代码、脚本命令输出或用户明确确认中验证的事实。未验证的内容一律标注“未执行”。

## 0. 交接元信息

- 本次交接整理时间：2026-10-11（Asia/Shanghai）
- 当前阶段：P1 交易资产与数据资产管理（P1-01 / P1-02 已完成，P1-03 代码已落地但未验证）
- 当前分支：`main`，跟踪 `origin/main`，ahead/behind = 0/0
- 当前 HEAD：`5113b62135536b88db81975e7e9dfe77824b9324`（提交信息仅显示时间戳“2026年10月10日 18:45:55”）
- 工作区状态：**有 16 个已跟踪文件被修改、8 个未跟踪路径，全部未提交、未推送**
- 本轮交接**没有**修改任何业务代码，**没有**执行 `commit` / `push` / `pull` / `merge` / `rebase` / `reset` / `clean`，**没有**执行数据库脚本，**没有**启动应用
- 本轮只做了三件事：复核代码与文档差异、运行构建验证、更新协作文档

### 给接手 AI 的一条硬约束

**P1-03（数据资产）的代码从未通过编译验证。** 本轮 `mvn -o -B test -f backend\pom.xml` 在**测试编译阶段**失败，因此在读到本文时：

- 不要相信任何“P1-03 已测试通过”的说法；
- 修好 `EsAssetServiceTest.java:32` 的编译错误并重跑测试之前，禁止在 `dataasset` 包上继续叠加新功能；
- 修这个错误需要用户批准（本轮用户明确要求“不要改，只记录为阻塞项”）。

## 1. 本轮任务与完成情况

| 用户要求 | 完成情况 |
| --- | --- |
| 更新 `HANDOFF.md` | 完成（本文重写） |
| 更新 `PLAN.md` 相关任务进度 | 完成（P1 表格、P1-03 执行任务、新增 P1 实现小结表） |
| 有新设计决策则更新 `DECISIONS.md` | 完成（新增 D-022/D-023/D-024，修正 D-014/D-015/D-020/T-006） |
| 涉及内网迁移则更新 `MIGRATION.md` | 完成（新增 V8/V9、幂等边界、权限码、构建与执行环境限制） |
| 标记已完成验证与仅初步实现的代码 | 完成（见第 3、4 节） |
| 明确下一步改哪些文件、禁止改哪些文件 | 完成（见第 8 节） |
| 明确数据库字段哪些已确认、哪些未确认 | 完成（见第 9 节） |
| 检查是否误改 intranet 以外基线代码 | 完成（见第 10 节，结论：未误改） |

## 2. 本轮实际动过的文件（只有文档）

- `docs/intranet/HANDOFF.md`（重写）
- `docs/intranet/PLAN.md`
- `docs/intranet/DECISIONS.md`
- `docs/intranet/MIGRATION.md`
- `docs/intranet/ARCHITECTURE.md`

除上述五个文档外，本轮没有写入任何仓库文件。`AGENTS.md` 未修改。构建只写入了被 `.gitignore` 忽略的产物目录（`backend/target/`、`frontend/dist/`，证据：`git check-ignore -v` 分别命中 `.gitignore:71 target/` 与 `.gitignore:144 dist/`）。

## 3. 已验证事实（有命令输出证据）

| 项 | 结果 | 证据 |
| --- | --- | --- |
| 前端构建 | **通过** | 2026-10-11 执行 `npm.cmd run build`（工作目录 `frontend`），输出 `✓ built in 24.68s`，产出 `dist/assets/**`；仅有 chunk 体积告警。PowerShell 因 npm 把告警写到 stderr 而报 exit 1，不代表构建失败 |
| 后端主代码编译 | **通过** | `mvn.cmd -o -B test -f backend\pom.xml` 在 `maven-compiler-plugin` 的 `default-compile` 阶段无错；`backend/target/classes/org/lbl/intranet/dataasset/**` 已生成 28 个 class 文件 |
| 后端测试套件编译 | **失败（阻塞项）** | 同一次构建在 `default-testCompile` 失败：`EsAssetServiceTest.java:[32,32] updateById 的引用不明确`（`参考: updateById(T) 与 updateById(Collection<T>)`），`BUILD FAILURE`，`Total time: 7.744 s` |
| 后端单元测试结果 | **未执行** | 测试编译失败，未产生任何测试运行结果。旧文档中“19/19 通过”是本次代码变更之前的记录，**不能**用于现在的代码状态 |
| 工作区改动范围 | 全部位于 `intranet` 路径或 `docs/intranet/` | `git diff --name-only` 的 16 个文件全部命中 `intranet`；未跟踪路径同样全部在 `intranet` 相关目录 |
| 基线文件改动 | **无** | `git diff --name-only | Where-Object { $_ -notmatch 'intranet' }` 输出为空 |
| 数据库脚本执行 | **V1~V6 已执行（用户确认）；V7/V8/V9 未执行** | V1~V6 来自用户 2026-10-11 明确确认，本次会话未查库复核；V7~V9 为本轮新增/既存脚本，无执行记录 |
| 应用启动与接口/页面联调 | **未执行** | 8080 / 5173 / 3306 / 6379 / 9200 均未监听；`Get-Process java,node` 无结果 |
| 构建执行环境限制 | 已确认并绕过 | 本会话 shell 受 Windows 沙箱限制无法写入工作区（`frontend\`、`backend\`、`backend\target\classes` 直接建文件均“访问被拒绝”），Maven 因此在 `maven-resources-plugin` 报 `application-dev.yml (拒绝访问)`。经用户批准后放宽该次命令权限才完成上述两次构建。**接手方若遇到同样报错，先申请放宽该次命令权限，不要怀疑代码** |

本机工具链（2026-10-11 实测，仅代表当前设备）：Node `v24.14.0`；Java `17.0.12`；Maven `D:\software\apache-maven\bin\mvn.cmd`（在 PATH 中）；`frontend/node_modules` 与 `backend/target` 均已存在。

## 4. 当前代码完成度：哪些已验证，哪些只是初步实现

### 4.1 已验证（构建层面）

- P1-03 前端：`frontend/src/pages/intranet/dataasset/**`、`frontend/src/pages/intranet/transaction/DataResourceDialog.tsx` 及其对交易页的接入，随 `npm run build` 通过 TypeScript 检查与 Vite 构建。
- P1-03 后端：`dataasset` 三个子包通过 `default-compile`，只证明语法与类型正确。

### 4.2 只是初步实现（未运行、未联调、未执行 SQL）

- P1-03 后端全部业务行为：HBase/ES 资产 CRUD、字段 CRUD、删除引用保护、交易数据资源整体替换（D-022）、交易删除时清理关联（D-015/D-024）。
- P1-03 数据层：V8 六张表、V9 菜单与权限，**均未在任何数据库执行过**。
- P1-03 前端交互：资产页两个 Tab、字段抽屉、删除确认、交易“数据资源”弹窗，**没有任何人工浏览器验证**。
- P1-02 的既有实现（报文字段 CRUD、同父英文名唯一预检、字段审计定位、字段页搜索/说明编辑/超长展示、交易详情高度修复）：代码在仓库中，但本轮**无法重跑测试**，因此也回到“未验证”状态。
- P1-03 的四个定向测试文件（`HbaseAssetServiceTest`、`EsAssetServiceTest`、`TransactionDataResourceServiceTest`、`TransactionAssetServiceTest`）：已写入仓库，**从未成功编译过**。

## 5. 工作区未提交内容清单（8 个未跟踪路径 + 16 个已跟踪修改）

### 5.1 P1-03 新增（未跟踪）

后端主代码：

- `backend/src/main/java/org/lbl/intranet/dataasset/hbase/{controller,entity,mapper,request,service,vo}`（`HbaseAssetController`、`HbaseTableEntity`、`HbaseFieldEntity`、`HbaseTableMapper`、`HbaseFieldMapper`、`HbaseTableRequest`、`HbaseFieldRequest`、`HbaseTableVO`、`HbaseFieldVO`、`HbaseAssetService`）
- `backend/src/main/java/org/lbl/intranet/dataasset/es/{controller,entity,mapper,request,service,vo}`（`EsAssetController`、`EsIndexEntity`、`EsFieldEntity`、`EsIndexMapper`、`EsFieldMapper`、`EsIndexRequest`、`EsFieldRequest`、`EsIndexVO`、`EsFieldVO`、`EsAssetService`）
- `backend/src/main/java/org/lbl/intranet/dataasset/relation/{controller,entity,mapper,request,service,vo}`（`TransactionDataResourceController`、`TransactionHbaseResourceEntity`、`TransactionEsResourceEntity`、`TransactionHbaseResourceMapper`、`TransactionEsResourceMapper`、`TransactionDataResourceRequest`、`TransactionDataResourceVO`、`TransactionDataResourceService`）

后端测试：

- `backend/src/test/java/org/lbl/intranet/dataasset/es/service/EsAssetServiceTest.java`（**编译失败源**）
- `backend/src/test/java/org/lbl/intranet/dataasset/hbase/service/HbaseAssetServiceTest.java`
- `backend/src/test/java/org/lbl/intranet/dataasset/relation/service/TransactionDataResourceServiceTest.java`
- `backend/src/test/java/org/lbl/intranet/transaction/service/TransactionAssetServiceTest.java`

数据库脚本：

- `backend/src/main/resources/db/intranet/V7__add_intranet_message_field_sibling_unique_key.sql`
- `backend/src/main/resources/db/intranet/V8__create_intranet_data_asset_tables.sql`
- `backend/src/main/resources/db/intranet/V9__seed_intranet_data_asset_menu.sql`

前端：

- `frontend/src/pages/intranet/dataasset/{index.tsx,index.css,AssetDialogs.tsx,api.ts,types.ts}`
- `frontend/src/pages/intranet/transaction/DataResourceDialog.tsx`

### 5.2 已跟踪但未提交的修改

后端：

- `backend/src/main/java/org/lbl/intranet/transaction/service/TransactionAssetService.java`（删除交易时按 HBase 关联 → ES 关联 → 报文字段 → 交易主记录的顺序清理；构造函数新增 3 个 mapper 依赖）
- `backend/src/main/java/org/lbl/intranet/transaction/field/controller/TransactionMessageFieldController.java`（审计 `targetType=INTRANET_TRANSACTION_MESSAGE_FIELD`，新增用 `交易ID:方向:父节点ID:英文名` 定位）
- `backend/src/main/java/org/lbl/intranet/transaction/field/mapper/TransactionMessageFieldMapper.java`（`deleteByTransactionId`、`countBySiblingFieldName`）
- `backend/src/main/java/org/lbl/intranet/transaction/field/service/TransactionMessageFieldService.java`（同父英文名唯一预检）
- `backend/src/test/java/org/lbl/intranet/transaction/field/service/TransactionMessageFieldServiceTest.java`（新增重复名用例）

前端：

- `frontend/src/pages/intranet/transaction/{index.tsx,index.css,api.ts,types.ts}`（数据资源入口与两个接口、ESF 服务名/操作名称单行截断+Tooltip、详情自然高度）
- `frontend/src/pages/intranet/transaction/fields/{index.tsx,index.css}`（请求遮罩、说明 Popover 多行编辑、超长截断、中英文名搜索与祖先链保留）

文档：第 2 节列出的五个文件。

## 6. 阻塞项与风险

### 6.1 阻塞：后端测试套件无法编译（必须先修）

- 位置：`backend/src/test/java/org/lbl/intranet/dataasset/es/service/EsAssetServiceTest.java:32`
- 现状代码：`verify(fields, never()).updateById(any());`
- 报错：`updateById 的引用不明确`，`BaseMapper.updateById(T)` 与 `BaseMapper.updateById(Collection<T>)` 均匹配
- 同文件 `HbaseAssetServiceTest` 用的是 `deleteById(anyLong())`，故不受影响；`TransactionMessageFieldServiceTest` 用的是 `any(TransactionMessageFieldEntity.class)`，是仓库内既有的正确写法
- 建议修法（**需用户批准后才能执行**）：改为 `verify(fields, never()).updateById(any(EsFieldEntity.class));` 或 `anyLong()` 风格的等价写法
- 影响：修好之前，P1-01/P1-02/P1-03 的任何测试都无法运行，`mvn test` 永远是红的

### 6.2 未确认项：ES 固定 RowKey 字段名

代码把 RowKey 字段名硬编码为 `id`（`EsAssetService.ROWKEY_FIELD_NAME = "id"`，前端 `AssetDialogs.tsx` 用 `field?.fieldName === 'id'` 禁止改名），但 `DECISIONS.md` 的 D-020 记录的是“固定字段名待用户提供”。**这属于未经用户确认的实现假设**，必须在 P1-03 定稿前确认（见第 9 节）。若用户给出别的名字，需同时改：后端常量与校验、`EsFieldMapper.countRowkeyField` 中的 `field_name = 'id'`、前端禁用逻辑与字段页提示。

### 6.3 模型缺口：源系统未建模

`ARCHITECTURE.md` 概念图中的“源系统”在 V8 中没有对应表，`intranet_hbase_table` 也没有 `system_id`/归属字段。P1-03 代码因此无法回答“这张 HBase 表属于哪个业务系统”。要么补一张源系统表并给 HBase/ES 加归属字段，要么由用户明确 P1 不做源系统——**当前是文档与实现之间的真实差异**，不要当成已完成。

### 6.4 一致性风险：ES 索引允许改挂 HBase 表

`EsAssetService.update` 只校验新的 `hbaseTableId` 存在（`requireHbase`），不阻止把已被交易关联的索引改挂到另一张 HBase 表，也不校验新表的 RowKey 语义是否与已有字段匹配。需要决定是否加“被交易关联时禁止变更归属表”的约束。

### 6.5 数据层风险：无数据库外键

V8 未建外键，`hbase_table_id`、`es_index_id`、`transaction_id` 的引用完整性全靠服务层。绕过应用直写库会产生孤立引用；服务层已有的删除保护（D-024）是唯一防线。若后续引入批量导入，必须重新评估。

### 6.6 已接受的历史风险（延续，不再阻塞）

- V2/V6（以及新增的 V9）用 `NOT EXISTS` 判断菜单路径/权限码，但未排除 `sys_menu.deleted = 1`；用户 2026-10-11 决定不修，靠业务操作避免“删除菜单后重复执行脚本”。
- `frontend/src/` 的 `pages/**/*.tsx` 动态扫描会把对话框类组件也列为菜单候选；用户 2026-10-11 决定不改基线扫描规则，靠菜单配置操作规范规避。P1-03 新页面同样受此约定约束。

### 6.7 新增的跨子域依赖（内网内部，但影响未来剥离）

`org.lbl.intranet.transaction.service.TransactionAssetService` 现在直接 import `org.lbl.intranet.dataasset.relation.mapper.*`。这是 D-015 删除契约的必然结果，仍在 `intranet` 包内，**不属于基线污染**；但将来若按子域拆分内网模块，需保留这个依赖或改为事件/接口解耦。

## 7. 测试执行情况汇总

| 命令 | 执行时间 | 结果 |
| --- | --- | --- |
| `npm.cmd run build`（`frontend`） | 2026-10-11 | 通过（`✓ built in 24.68s`） |
| `mvn.cmd -o -B test -f backend\pom.xml` | 2026-10-11 | 失败于测试编译：`EsAssetServiceTest.java:32 updateById 引用不明确` |
| 应用启动 | 未执行 | 相关端口未监听 |
| V7 / V8 / V9 数据库脚本 | 未执行 | 未连接数据库 |
| 浏览器人工验收（字段页、资产页、交易数据资源弹窗） | 未执行 | 无运行环境 |
| 跨设备拉取复核 | 未执行 | 本轮未提交，另一台设备看不到这些改动 |

## 8. 下一步行动（按顺序）

### 8.1 必须先做

1. **请用户确认两件事**：ES 固定 RowKey 字段名是否为 `id`；HBase 表“数据量/数据深度”的业务语义与样例值（见第 9 节）。
2. **修复后端测试编译错误**（6.1 节），需用户批准；然后重跑 `mvn.cmd -o -B test -f backend\pom.xml`，把真实用例数写回本文档。
3. **决定源系统是否在 P1 建模**（6.3 节）。
4. **决定 ES 索引归属表是否允许在被交易关联后变更**（6.4 节）。

### 8.2 允许修改的文件（P1-03 收尾）

- `backend/src/test/java/org/lbl/intranet/dataasset/es/service/EsAssetServiceTest.java`（只修断言重载歧义）
- `backend/src/main/java/org/lbl/intranet/dataasset/**`（仅在用户确认第 8.1 节事项后按结论调整）
- `frontend/src/pages/intranet/dataasset/**`、`frontend/src/pages/intranet/transaction/DataResourceDialog.tsx`
- `backend/src/main/resources/db/intranet/V8__*.sql`、`V9__*.sql`（若未执行过，可直接修正；**若已在任何环境执行过，必须新增 V10 修正脚本，不得改写历史脚本**）
- 五个 `docs/intranet/*.md` 文档

### 8.3 禁止修改（除非先取得用户明确批准，并按 `AGENTS.md` §2 说明影响与替代方案）

- 根目录 `AGENTS.md`
- `frontend/src/` 下 `pages/intranet/` 以外的所有基线页面、组件、路由注册表、请求封装、权限组件（如 `components/SmartTable`、`permission/Permission`、`utils/request`、`utils/apiError`）
- `backend/src/main/java/org/lbl/` 下 `intranet/` 以外的所有基线代码，尤其是：`config/MybatisConfig`（本次复核确认其 `auditHandler` 已满足需求，无需改动）、`system/log/aspect/OperationLog` 及其切面、审批聚合逻辑、`SystemPermissionInitializer`
- 现有基线数据库脚本（`init_schema.sql`、`init_data.sql`）
- `backend/src/main/resources/db/intranet/V1__*.sql` ~ `V6__*.sql`（用户已确认在数据库执行过，属既成事实，禁止改写）
- `docs/intranet/` 以外的任何文档

### 8.4 数据库迁移执行顺序（执行前先备份/确认）

1. 执行 V7 的第一段重复数据查询；**仅在无重复结果时**再执行 `ADD UNIQUE KEY`。
2. 执行 V8（六张 `CREATE TABLE`，无 `IF NOT EXISTS`，先确认目标库无同名表）。
3. 执行 V9；执行前核查是否已存在同路径 `/intranet/dataasset` 或同权限码 `intranet:dataasset:*` 的软删除菜单。
4. 校验项见 `MIGRATION.md` 第 3 节；注意无外键，需手写 SQL 检查孤立引用。

### 8.5 联调验证清单（全部未执行）

- 交易列表 → “数据资源” → 保存后重开是否回显；缺少 `id` 字段的 ES 索引是否被拒绝。
- 数据资产页 HBase/ES 两个 Tab 的分页、搜索、新增、编辑、删除。
- 删除保护：被 ES 引用的 HBase 表、被交易关联的 ES 索引、ES 的 `id` 字段，均应给出业务提示而不是 500。
- 交易物理删除后，`intranet_transaction_hbase_resource`、`intranet_transaction_es_resource`、`intranet_transaction_message_field` 是否都无残留。
- 权限：用非超管角色验证 `intranet:dataasset:*` 与 `intranet:transaction:*` 的实际拦截效果。
- 操作日志：`INTRANET_HBASE_TABLE`、`INTRANET_ES_INDEX`、`INTRANET_TRANSACTION` 的 targetType/id 是否正确落库。

## 9. 数据库字段确认状态

### 9.1 用户已明确确认（可直接依赖）

| 项 | 结论 | 依据 |
| --- | --- | --- |
| HBase 表锁字段 | `lock_key VARCHAR(255) NULL`，空输入归一为 `NULL` | 用户确认 → D-018；V8 第 8 行一致 |
| HBase 表业务标识 | 集市名 + 表名 | 用户确认 → D-019；V8 唯一键 `uk_intranet_hbase_table_market_name` |
| HBase 字段范围 | 列族（默认 `info`）+ 字段名 + 数据类型（可存 `varchar(32)` 文本）+ 说明 + 顺序；无中文名、无长度/精度列 | 用户确认 → D-019；V8 `intranet_hbase_field` 一致 |
| ES 字段范围 | 单层字段：字段名 + 数据类型 + 可搜索 + 可排序 + 说明 + 顺序；无中文名 | 用户确认 → D-019；V8 `intranet_es_field` 一致 |
| 资产删除规则 | 被引用禁止删除；删资源时同事务删其字段；ES RowKey 字段禁止单独删除 | 用户确认 → D-019；服务层实现 |
| 审计列 | `created_by/created_time/updated_by/updated_time`，复用 `MybatisConfig.auditHandler` | 用户确认 → D-021；V8 每表都有 |
| 迁移脚本已执行范围 | V1~V6 已执行 | 用户 2026-10-11 确认（本次会话未查库复核） |

### 9.2 用户明确确认过“不这么做”的范围

- P1 不建 RowKey 规则表/组件表，不解析 RowKey 规则文本（D-016）。
- P1 不建表锁实体或锁关联表，不接入 Redis 实时锁状态（D-018）。
- P1 不为每个年/月物理 ES 索引分别建记录，不保存 `rowkey_field_id`（D-020）。
- P1 不在交易资源关联上保存顺序、分支、主/备或合并逻辑（D-017、D-022）。

### 9.3 已写入实现但**用户尚未确认**（接手方必须问清楚）

| 未确认项 | 当前实现 | 影响文件 |
| --- | --- | --- |
| ES 固定 RowKey 字段名 | 硬编码 `id` | `EsAssetService.ROWKEY_FIELD_NAME`、`EsFieldMapper.countRowkeyField`、`AssetDialogs.tsx` |
| HBase `data_volume` / `data_depth` 的业务语义与取值格式 | `VARCHAR(64)` 自由文本，前端 `maxLength={64}` | V8、`HbaseTableEntity`、`AssetDialogs.tsx` |
| 源系统是否需要独立表、是否给 HBase/ES 加归属字段 | 未实现 | 待新增脚本与新表 |
| ES 索引 `index_pattern` 唯一是否足够表达“一族多物理索引” | `UNIQUE KEY uk_intranet_es_index_pattern` | V8 |
| V8 的字段长度（如 `table_name VARCHAR(128)`、`description VARCHAR(1000)`）是否满足真实内网最长值 | 已写入 | V8（**未执行，仍可直接调整**） |

## 10. 基线与 Git 说明

- 本轮除文档外没有修改任何文件；`docs/intranet/` 之外的已跟踪改动都来自本次交付的 P1-02/P1-03 实现，且全部位于 `intranet` 路径（前端 `frontend/src/pages/intranet/**`、后端 `backend/src/main/java/org/lbl/intranet/**` 与对应测试）。
- 未修改 `AGENTS.md`、基线页面/组件、基线后端公共代码、基线数据库脚本、构建依赖。
- 没有执行 `commit`、`push`、`pull`、`merge`、`rebase`、`reset`、`clean`，没有改动远程配置。
- **跨设备提醒**：所有这些改动仍在工作区，未进入远程仓库。另一台设备拉取 `origin/main` 只能得到 `5113b62`，**看不到 P1-02/P1-03 的任何新代码和本轮文档更新**。要跨设备接续，必须先由用户决定如何提交（本 AI 不得代为提交）。
- 构建产物 `backend/target/`、`frontend/dist/` 均被 `.gitignore` 忽略，未进入改动范围。
- 仓库中的样例数据均为虚构；本轮没有新增任何真实内网数据、地址、凭据或证书。

## 11. 接手方最短路径

1. 读 `AGENTS.md` → 本文 → `PLAN.md` 的 P1 段 → `ARCHITECTURE.md` 第 5 节“已实现的表结构与代码路径”。
2. 执行 `git status --short --branch`、`git log --oneline -3`，确认工作区仍是本文描述的状态（若已有人提交，以新提交为准并更新本文）。
3. 修 `EsAssetServiceTest.java:32` 的编译错误，跑 `mvn.cmd -o -B test -f backend\pom.xml`，把真实结果写回第 3、7 节。
4. 只在得到用户对第 9.3 节四个问题的答复后，才继续扩展 P1-03；否则优先做第 8.5 节的联调验证与 V7~V9 的执行确认。
