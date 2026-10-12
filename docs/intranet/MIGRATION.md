# Intranet 内网迁移清单

> 更新日期：2026-10-12
> 当前状态：P1-01/P1-02/P1-03 代码与 SQL 已实现；公共审批与 P2 需求代码继续实施。迁移脚本执行状态以用户手工执行结果为准。2026-10-12 已验证前端构建、后端编译与公共审批状态机定向测试通过；审批中心/消息中心已接入新增交易需求详情和操作，浏览器端流程与权限仍待验证。

## 1. 迁移原则

- 迁移单元以 `intranet` 代码、专属资源、数据库脚本、权限菜单和显式依赖为主，不复制无关个人项目业务。
- 复用的基线公共能力必须列出版本和接口契约，不能假设目标工程天然存在。
- 本仓库不保存真实内网地址、数据、凭据或证书；迁移时由目标环境的安全配置渠道提供。
- 所有脚本先在隔离环境验证，记录执行顺序、幂等性、回滚/补偿和数据校验结果。

## 2. 代码复制范围

### 计划中的必选目录

| 路径 | 内容 | 当前状态 |
| --- | --- | --- |
| `backend/src/main/java/org/lbl/intranet/` | 后端内网领域、接口、服务、持久层与专属公共代码 | 已创建 P1-01 交易、P1-02 报文字段、P1-03 `dataasset`（`hbase`/`es`/`relation` 三个子包） |
| `frontend/src/pages/intranet/` | 前端内网页面、专属组件/API/类型 | 已创建交易及数据资产页面、两类隐藏字段设计页、交易资源对话框和 intranet 行内编辑共享组件 |
| `backend/src/main/resources/db/intranet/` | 内网表结构、菜单权限和虚构演示数据脚本 | 已创建 P1-01 / P1-02 / P1-03 共 11 个脚本；V1~V11 均已由用户执行 |
| `backend/src/test/java/org/lbl/intranet/` | 内网专属单元测试 | 当前 HEAD 中不存在；用户已决定本期不补（见 D-030），迁移时不随代码复制 |
| `docs/intranet/` | 架构、决策、实施与迁移说明 | 已创建文档框架 |

> 2026-10-11：`a3b8d0b` 删除了 5 个测试（内网 P1-02 的 `TransactionMessageFieldServiceTest` 与 4 个基线审计测试），用户确认为本人有意清理，**不需要恢复**。内网 P1-02/P1-03 的领域测试因此完全缺失；用户已决定本期不补（见 D-030），迁移时需在目标工程自行评估是否补测。

### 可能需要同步或在目标工程中适配的公共契约

- 统一返回 `Result` / `PageResult` 与全局异常处理。
- Spring Security 当前用户、权限码及菜单/RBAC 模型。
- `NotificationService`、通知表契约、WebSocket 实时通道和前端实时客户端。
- 审批任务页面/API/聚合契约（纯方案 2，见 D-034）。
- 公共审批基线 `backend/src/main/java/org/lbl/approval/core/`、`approval/task/` 及 `db/approval/`。纯方案 2下所有审批业务都写公共实例/任务表；迁移内网模块时应优先对接目标工程的等价公共审批能力，而不是复制第二套审批中心。
- 前端请求封装、布局、主题和明确复用的通用组件（含 `frontend/src/components/SmartTable/`、`frontend/src/components/EllipsisText/`）。
- 全局布局约定：intranet 字段页依赖基线 `frontend/src/styles/global.css` 中的 `page-fill` / `card-scroll` 规则（见 D-031），并由 `pages/intranet/components/InlineEditableTable.tsx` 渲染滚动容器、统一给 Table 传 `sticky`。基线 `frontend/src/components/SmartTable/SmartTable.tsx` 同样固定渲染 `.card-scroll` 并默认启用固定表头（见 D-038），所以任何加 `page-fill` 的列表页都得到同一效果。迁移到目标工程时必须同时带上：这组全局规则 + 这两个组件的对应版本实现，否则"标题固定 + 表头固定 + 表格内滚动"会失效。
- `G6Graph` 封装与 `@antv/g6` 版本。
- P5 所需 `AgentTool`、`ToolRegistry`、权限过滤和 Artifact 契约。

复制公共代码前应优先在目标工程对接已有等价能力，避免复制两套基础设施。

## 3. 数据库脚本

每个脚本落地后补充下表，不登记真实数据：

| 顺序 | 脚本 | 用途 | 幂等/回滚 | 依赖 | 状态 |
| --- | --- | --- | --- | --- | --- |
| V1 | `db/intranet/V1__create_intranet_transaction.sql` | 交易资产主表 | 独立执行；物理删除，无回滚脚本 | P1-01 | 已执行（用户确认，当前会话未查库复核） |
| V2 | `db/intranet/V2__seed_intranet_transaction_menu.sql` | 内网交易菜单、按钮权限与超管初始授权 | 独立执行；幂等插入 | V1、既有 RBAC 表 | 已执行（用户确认，当前会话未查库复核） |
| V3 | `db/intranet/V3__insert_intranet_transaction_demo_data.sql` | 虚构交易演示数据 | 按需执行；不用于生产数据 | V1 | 已执行（用户确认，当前会话未查库复核） |
| V4 | `db/intranet/V4__create_intranet_transaction_message_field.sql` | 交易请求/响应字段、对象及数组节点 | 独立执行；物理删除，无回滚脚本 | V1 | 已执行（用户确认，当前会话未查库复核） |
| V5 | `db/intranet/V5__insert_intranet_transaction_message_field_demo_data.sql` | 虚构交易报文字段演示数据 | 按需执行；不用于生产数据 | V1、V3、V4 | 已执行（用户确认，当前会话未查库复核） |
| V6 | `db/intranet/V6__seed_intranet_transaction_field_design_menu.sql` | 隐藏交易字段设计页及超管初始授权 | 独立执行；幂等插入 | V2 | 已执行（用户确认，当前会话未查库复核） |
| V7 | `db/intranet/V7__add_intranet_message_field_sibling_unique_key.sql` | 同一交易/方向/父节点下字段英文名唯一 | 先查询重复数据；存在重复时唯一索引创建失败且不自动清理 | V4 | 已执行（用户确认，本会话未查库复核） |
| V8 | `db/intranet/V8__create_intranet_data_asset_tables.sql` | HBase 表/字段、ES 索引/字段、交易 HBase/ES 资源关联共六张表 | 直接 `CREATE TABLE`，无 `IF NOT EXISTS`；重复执行报错，需先确认目标库无同名表 | V1 | 已执行（用户确认，本会话未查库复核） |
| V9 | `db/intranet/V9__seed_intranet_data_asset_menu.sql` | 内网数据资产菜单、四个按钮权限与超管初始授权 | 幂等插入；守卫未排除逻辑删除（见下方幂等边界） | V8、V2、既有 RBAC 表 | 已执行（用户确认，本会话未查库复核） |
| V10 | `db/intranet/V10__insert_intranet_data_asset_demo_data.sql` | 3 张虚构 HBase 表、字段、3 个逻辑 ES 索引及字段；存在 V3 演示交易时补充资源关联 | 按业务唯一键守卫，可重复执行；仅用于本地/测试展示 | V8；关联演示可选依赖 V3 | 已执行（用户确认，本会话未查库复核） |
| V11 | `db/intranet/V11__seed_intranet_data_asset_field_design_menu.sql` | 数据资产字段设计隐藏路由及超管初始授权 | 幂等插入；沿用既有软删除菜单边界 | V9 | 已执行（用户确认，本会话未查库复核） |
| AP1 | `db/approval/V1__create_public_approval_tables.sql` | 创建公共审批实例、任务表 | 直接建表；重复执行会报表已存在 | 现有用户表 | 已执行（用户确认，本会话未查库复核） |
| AP2 | `db/approval/V2__backfill_department_change_approval.sql` | 把现有部门调动实例和步骤回填公共表，并输出缺失计数 | `NOT EXISTS` 守卫；仅可在 AP3 前执行 | AP1、旧部门调动表 | 已执行，两个缺失计数均为 0（用户确认，本会话未查库复核） |
| AP3 | `db/approval/V3__cleanup_legacy_department_change_approval.sql` | 业务表改名为 `sys_department_change`，删除旧步骤表和四个重复审批字段 | 一次性破坏性清理，不可重复执行；执行前备份 | AP1、AP2 完整性计数均为 0 | **待执行** |
| AP4 | `db/approval/V4__add_approval_candidate_group.sql` | 公共审批任务补充候选部门/角色快照，部门调动改为部门审批人候选组 | 一次性 `ALTER TABLE`；用户已执行并做基础验证 | AP1、现有部门/角色数据 | 已执行（用户确认，本会话未查库复核） |
| V12 | `db/intranet/V12__create_intranet_requirement.sql` | 需求主表及新增/调用/变更三张固定明细表 | 直接建表；重复执行报错 | P2、MySQL | **待执行** |
| V13 | `db/intranet/V13__seed_intranet_requirement_approval_config.sql` | 需求审批、研发部门和开发角色种子 | 部门/角色按 code 幂等插入；用户角色须环境手工授权 | V12、现有 RBAC | **待执行** |
| V14 | `db/intranet/V14__seed_intranet_requirement_menu.sql` | 需求菜单、按钮权限和超管初始授权 | 按 route/权限码幂等插入；沿用软删除菜单边界 | V13、现有 RBAC | **待执行** |
| V15 | `db/intranet/V15__add_intranet_requirement_description_and_expected_online_date.sql` | 为已建立的需求主表补充需求描述和期望上线时间 | 一次性 `ALTER TABLE`；执行前确认两列不存在 | V12 | **待执行** |
| V16 | `db/intranet/V16__create_intranet_transaction_version.sql` | 交易主快照、报文字段快照、HBase/ES 资源关系快照四张版本表 | 直接建表；重复执行报错；每需求唯一生成版本 | V12、V1、V4、V8、公共审批表 | **待执行** |
| 待定 | 待创建 | 源系统资产（是否独立建模待确认） | 待确认 | P1 模型 | 未开始 |
| 待定 | 待创建 | 需求、审批关联、交易调用 | 待确认 | P2 方案 | 未开始 |
| 待定 | 待创建 | 血缘节点、关系、证据和有效期 | 待确认 | P3 模型 | 未开始 |
| 待定 | 待创建 | 变更事件、影响结果和处置记录 | 待确认 | P4 规则 | 未开始 |

迁移校验至少包括：表/索引/约束存在性、记录数、唯一键冲突、外键或逻辑引用完整性、字符集与时区、失败后的恢复方式。

2026-10-13 代码状态：V12 已规划的三张固定明细表均已有运行时实体、Mapper、读写接口、前端编辑和审批只读展示；V16 的四张版本表也已有运行时读写、快照生成和历史展示。本轮未执行数据库脚本；V16 必须在版本生效浏览器验收前执行。

已知幂等边界（执行前须确认）：V2/V6/V9/V11 的 `NOT EXISTS` 守卫未排除 `sys_menu.deleted = 1`，而该表为逻辑删除且 `route_path` / `permission_code` 上有唯一键。若目标库存在同路径或同权限码的软删除菜单，脚本会静默跳过插入而不报错，导致菜单不出现。执行前请先核查，或先决定守卫的修正方式。2026-10-11 用户确认 V2/V6/V9/V11 均已执行成功、未观察到跳过；该边界不作为已解决问题，重复执行或新增同类脚本时仍需按此核查。

V8 相关执行注意：六张表用直接 `CREATE TABLE`（与 V1/V4 一致），重复执行会因表已存在而报错；V8 未建数据库外键，`hbase_table_id` / `es_index_id` / `transaction_id` 的引用完整性完全由服务层校验，迁移校验阶段无法用外键工具核对孤立引用，需要写 SQL 自查。V9 的菜单 `component` 值为 `intranet/dataasset/index`，对应的 `frontend/src/pages/intranet/dataasset/index.tsx` 必须存在于目标构建产物中。

V10 仅包含虚构数据，覆盖 HDS/PBM 两个集市、按年/按月逻辑索引族、可空锁标识、`id` RowKey 字段及四类 ES 数据类型。执行 V10 前必须先执行 V8；如 V3 演示交易不存在，末尾三条资源关联语句会安全地插入 0 行。

公共审批部署顺序为：①备份现有部门调动两张表；②执行 AP1；③执行 AP2；④确认 AP2 最后的 `missing_instance_count` 和 `missing_task_count` 均为 0；⑤执行 AP3；⑥部署切换后的应用代码。用户已完成第②~④步，并确认旧部门调动两张表没有业务数据；当前只剩 AP3 待执行。AP3 会直接重命名业务表、删除旧步骤表和重复列，且不可重复执行；执行后 AP2 因依赖旧表名而不能再次运行。切换后审批中心只查询公共表，旧结构不能作为直接降级的数据源。

## 4. 构建和运行依赖

### 当前已存在、预计复用

- 后端：Java 17、Spring Boot 3.4.4、Spring Security、MyBatis-Plus、MySQL 驱动、Redis、WebSocket。
- 前端：Node 版本范围 `>=20.19 <25`、React 18、TypeScript、Vite、Ant Design、Redux Toolkit、`@antv/g6` 5.x。

### 本机实测版本（2026-10-10，仅代表当前设备）

| 项 | 实测值 |
| --- | --- |
| Node | `v24.14.0`（2026-10-11 当前设备实测） |
| Java | `17.0.12`（2026-10-11 Maven 测试日志确认） |
| Maven | `D:\software\apache-maven\bin\mvn.cmd`（当前 PATH 可直接调用 `mvn.cmd`） |
| Maven 本地仓库 | 当前会话未单独核对 |
| 构建验证 | 2026-10-12：`mvn.cmd -o -B -DskipTests compile` BUILD SUCCESS（291 个主源码）；`ApprovalWorkflowServiceTest` 4/4 通过且不启动 Spring/数据库；`npm.cmd run build` 通过（5323 个模块，仅既存 chunk 告警及 D-012 已知页面扫描提示）。P1-03 定向测试仍缺失，不能据此认定 P1 领域行为已覆盖 |
| 数据库连接 | `local` profile 按 `application-local.yml` 使用**远程** MySQL 与 Redis（地址不记录在本文），ES 未使用。原“本地服务 MySQL 3306 / Redis 6379 / ES 9200 / 应用 8080 均未监听”的记录已不适用：本次 `mvn test` 成功建立了该远程 MySQL 连接 |
| 执行环境限制 | Windows 沙箱下无法写入工作区（含 `backend\target`、`frontend\dist`）：Maven 在 `maven-resources-plugin` 阶段报 `application-dev.yml (拒绝访问)`，前端 `vite` 因 esbuild 子进程 `spawn EPERM` 失败。**删除工作区内文件同样可能被拒**（`Remove-Item` 报“对路径的访问被拒绝”）。以上均在放宽该次命令权限后通过，属沙箱边界而非代码问题 |
| `mvn test` 的副作用 | `JasyptEncryptorTest` 为 `@SpringBootTest`，启动完整上下文并执行 `SystemPermissionInitializer`，因此跑测试会连接上述远程库，并可能为超管补写基线 `sys_role_menu` 授权行。它不创建 intranet 表、不执行 `db/intranet` 脚本；执行测试前须知悉该副作用 |

两条已验证命令（另一台设备请重新实测）：

```powershell
mvn.cmd -o -B test -f backend\pom.xml
```

```powershell
cd frontend; npm.cmd run build
```

> Windows 注意：本机执行策略禁止直接运行 `.ps1`，`npm` 会被拦，请用 `npm.cmd`；机器上只有 Windows PowerShell 5.1，没有 `pwsh`。

### 未来可能新增（均待确认，当前不得安装）

- Elasticsearch Java/HTTP 客户端及与目标集群匹配的版本。
- HBase 客户端及 Hadoop 相关依赖、冲突排除策略。
- 数据迁移/版本化工具（若决定引入）。
- 大规模异步任务、调度或图存储依赖（仅在实测证明需要时）。

新增依赖时必须记录准确版本、许可证、离线安装包来源、目标环境兼容性以及为何现有依赖不能满足。

## 5. 权限与菜单配置

迁移时需随代码同步以下逻辑配置；交易资产现有权限码已确认，后续模块权限码在对应任务开发前确认：

- intranet 顶级菜单及需求、交易资产、数据资产、血缘、影响分析子菜单。
- 各页面的查看、新增、修改、删除、导入/导出、审批、发布、分析等按钮权限。
- 后端 `@PreAuthorize` 权限码与前端 `Permission` 判断的一致性。
- 角色授权、默认不授权策略和超级管理员行为。
- 动态菜单的 `route_path`、`component`，以及目标构建产物中对应页面是否存在。
- P5 Agent 工具权限与普通页面权限之间的映射。

已确认：内网菜单与权限通过 `db/intranet/` 种子脚本写入现有 RBAC（D-011），不修改 `SystemPermissionInitializer`。已落地权限码：交易资产 `intranet:transaction:list/add/update/delete`（V2）、数据资产 `intranet:dataasset:list/add/update/delete`（V9）；资产选项接口额外声明 `intranet:transaction:list`、`intranet:transaction:update`。V6 隐藏页面是否需要独立权限码仍待确认；任何修改现有初始化器的方案仍属于基线修改，必须先获批准。

## 6. 环境配置项

只记录配置键/用途/提供方，不在本文写值：

| 类别 | 配置项（占位命名，待实现时固化） | 用途 | 提供方 |
| --- | --- | --- | --- |
| MySQL | intranet 使用现有数据源或独立数据源 | 元数据与业务流程存储 | 目标环境 DBA |
| Redis | 复用现有连接配置 | 实时事件/可能的缓存 | 目标平台 |
| ES | endpoints、index/alias、认证、TLS、超时 | 检索和定位 | ES 管理方 |
| HBase | quorum、znode、namespace、认证、超时 | RowKey 回表和资产访问 | HBase 管理方 |
| 安全 | 密钥引用、证书路径、凭据提供器 | 安全连接 | 密钥管理平台 |
| Agent | 模型端点/凭据、工具开关 | P5 助手能力 | 目标 AI 平台 |

所有真实值应通过环境变量、外置配置或目标密钥管理系统注入。提交前检查 `.env*`、`application-local/secret`、证书、导出数据和日志。

## 7. 外部数据与初始化

- 使用虚构小样本验证结构；真实存量数据只在获授权的目标环境中处理。
- 导入前盘点编码、主键、时区、空值、重复项、字段长度和敏感级别。
- 导入应支持预检查、批次 ID、错误明细、断点/重试及可验证的回滚或补偿。
- ES 索引模板、mapping、alias、生命周期策略和重建步骤需单独登记。
- HBase namespace/table/column family、版本数、TTL、压缩及 RowKey 规则需单独登记。
- ETL 作业定义、调度、服务账号和网络白名单属于部署清单，但敏感值不入库。

## 8. 部署与验收顺序（草案）

1. 核对目标工程基线版本与公共契约。
2. 准备数据库变更并完成备份/回滚演练。
3. 配置权限菜单和目标环境的秘密引用。
4. 部署后端 intranet 模块，执行健康检查与权限/API 验证。
5. 构建并部署包含 intranet 页面的前端，验证动态菜单和资源加载。
6. 配置 ES/HBase 连接、模板/表结构与网络权限，再做脱敏连通性测试。
7. 按批次导入/治理存量元数据，执行完整性和血缘校验。
8. 验证审批、通知、实时刷新、影响分析和（P5 后）Agent 越权防护。
9. 记录版本、脚本校验值、执行人、时间、结果和遗留项。

## 9. 迁移注意事项

- 目标内网可能无法访问公网，提前准备经批准的 Maven/npm 离线依赖和许可证清单。
- 核对 MySQL、Redis、ES、HBase 版本兼容性，不以个人环境版本作假设。
- 检查反向代理对 WebSocket、路径前缀、超时和消息大小的配置。
- 校准服务器时区与业务日期语义，避免版本有效期和审批时间偏移。
- 大图和影响分析使用脱敏规模数据做性能验证，不能只用少量样例判断。
- 迁移完成后验证个人项目基线功能未受影响，并保留回退路径。

## 10. 迁移验收记录模板

```markdown
- 迁移版本/提交：
- 目标环境：
- 代码目录与校验：
- 数据库脚本及结果：
- 权限菜单结果：
- 环境配置项是否齐全（不记录值）：
- API/UI/审批/通知/血缘/影响分析测试：
- 敏感信息扫描结果：
- 回滚演练结果：
- 遗留问题与负责人：
```
