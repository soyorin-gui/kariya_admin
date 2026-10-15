# 交易资产与血缘分析模块计划

> 更新日期：2026-10-14
>
> 模块标识：`lineage`
>
> 当前状态：暂停、受保护
>
> 状态枚举沿用全局：`已完成`、`开发中`、`暂停`、`待业务确认`、`待验收`。

改造前的 P0-P6 逐项计划原文保存在 [`../history/PLAN-before-multi-module-2026-10-14.md`](../history/PLAN-before-multi-module-2026-10-14.md)，用于保留全部历史进度；本文件是冻结后的有效状态源。

## 1. 已实现范围

| 子域 | 已有实现 | 当前状态 |
| --- | --- | --- |
| 交易资产 | 交易基础信息 CRUD、交易报文字段树、字段搜索、状态/权限控制的编辑与只读展示 | 暂停；部分端到端与非超管场景待验收 |
| 数据资产 | HBase 表/字段、ES 逻辑索引/字段管理，交易关联 HBase/ES 资源，删除保护与固定 `id` 规则 | 暂停；接口及浏览器场景待继续验收 |
| 需求与交易版本 | 三类需求、公共审批接入、实施配置、投产时生成交易版本快照、历史版本查询 | 暂停；完整浏览器链路和部门调动回归待验收 |
| 字段来源 | 交易字段支持物理字段、调用方入参、固定常量、系统生成、计算得到五类取值方式 | 暂停；浏览器复验待完成 |
| 最小血缘 | HBase/ES 物理字段到交易请求/响应字段的一对多、多对一关系；记录直接读取、加工得到、查询条件及证据 | 暂停；后端定向测试缺失 |
| 影响分析 | 按 HBase/ES 字段确定性查询受影响交易字段；资产字段页提供入口 | 暂停；删除一致性和异常分支待验证 |
| 图谱 | 独立血缘分析页，可按交易、交易字段、HBase 表、HBase 字段检索并展示最小子图；支持适应、缩放和还原 | 基础浏览器验证通过；公共 G6 示例与异常场景待验收 |

## 2. 已确认数据库设计

当前最终结构位于 `backend/src/main/resources/db/intranet_schema.sql`，共 19 张 `intranet_*` 表；空库部署边界及现有环境升级限制以 `MIGRATION.md` 为准。已经确认并进入最终结构的分组为：

| 分组 | 已确认表 |
| --- | --- |
| 交易与字段 | `intranet_transaction`、`intranet_transaction_message_field` |
| 字段来源与血缘 | `intranet_transaction_field_source_config`、`intranet_field_lineage_relation` |
| HBase/ES 资产 | `intranet_hbase_table`、`intranet_hbase_field`、`intranet_es_index`、`intranet_es_field` |
| 交易资源关联 | `intranet_transaction_hbase_resource`、`intranet_transaction_es_resource` |
| 需求与流程 | `intranet_requirement`、三张需求类型明细表、`intranet_requirement_flow` |
| 交易版本快照 | `intranet_transaction_version`、`intranet_transaction_version_message_field`、`intranet_transaction_version_hbase_resource`、`intranet_transaction_version_es_resource` |

其中本轮血缘冻结需要重点保持的两张表为：

| 表 | 已确认字段/约束 | 用途 |
| --- | --- | --- |
| `intranet_transaction_field_source_config` | `target_field_id BIGINT` 主键；`source_type VARCHAR(32)`；`source_detail VARCHAR(2000) NULL` | 每个交易字段的一份取值方式配置 |
| `intranet_field_lineage_relation` | 自增 `id`；`source_type VARCHAR(32)`；`source_field_id BIGINT`；`target_field_id BIGINT`；`relation_type VARCHAR(32)`；`expression VARCHAR(2000) NULL`；`remark VARCHAR(1000) NULL` | 保存物理字段到交易字段的确定性关系和证据 |
| `uk_intranet_field_lineage` | `(source_type, source_field_id, target_field_id, relation_type)` 唯一 | 防止同类型重复关系 |
| `idx_intranet_field_lineage_target` | `target_field_id` 普通索引 | 支持从交易字段反查来源 |

已确认枚举：

- `source_type`（取值方式）：`PHYSICAL`、`CALLER_INPUT`、`CONSTANT`、`SYSTEM_GENERATED`、`COMPUTED`。
- 关系来源类型：`HBASE_FIELD`、`ES_FIELD`。
- `relation_type`：`DIRECT`、`DERIVED`、`QUERY_CONDITION`。

不得因运行监控模块需要而修改以上表、字段、类型、长度、唯一键或业务含义；确有需要时先提交最小改动方案并取得用户批准。

## 3. 验证状态

### 已验证

- 2026-10-14 历史记录：`npm.cmd exec tsc --noEmit` 通过。
- 用户已完成独立血缘分析页的基础浏览器验证，包括最小图谱和视图控制的基础可用性。
- Git 差异格式检查在当次血缘实现阶段通过。

### 待验收

- 交易字段来源弹窗的五类取值方式、关系增删、加载态、纵向滚动和错误提示。
- HBase/ES 字段影响入口与代表性一对多、多对一关系结果。
- 删除交易字段、HBase 字段/表、ES 字段/索引时的关系清理和事务一致性。
- 非法来源类型、关系类型、不存在字段及权限不足场景。
- `intranet:transaction:list` 只读权限与 `intranet:transaction:update` 维护权限的普通账号验证。
- 首页既有 G6 示例以及空图、较大图、缩放拖拽后的适应/还原等异常或边界场景。
- 后端编译和血缘模块单元/集成测试；当前仓库没有血缘定向测试，本机历史交接记录显示当时未执行 Maven 验证。
- 交易资产、需求审批和交易版本既有未验收项，详见冻结交接。

## 4. 待业务确认

- 作业平台是否能导出或查询字段级输入/输出映射，以及可作为事实证据的格式。
- Hive 元数据由手工、Excel 还是平台接口维护。
- 多输入、多输出及多个来源计算一个目标字段的真实业务规则。
- 是否建立独立应用资产，或第一版只记录交易调用方系统名称。
- 扩位、下线、类型/语义变化等影响规则矩阵。
- 遍历深度、环路、版本/时间点、关系失效及影响结果是否持久化。
- 责任人来源、人工确认与误报处置流程，以及真实环境的数据权限和脱敏要求。

## 5. 尚未完成或明确延后

| 任务 | 状态 | 恢复条件 |
| --- | --- | --- |
| Hive 元数据与同步作业接入 | 待业务确认 | 明确元数据来源和字段级映射契约 |
| HBase 上游及字段级 HBase→ES 映射 | 待业务确认 | 获得真实映射事实，禁止推断 |
| 独立应用资产与调用关系 | 待业务确认 | 确认应用模型与责任人来源 |
| 变更类型与影响规则矩阵 | 待业务确认 | 业务逐项确认规则 |
| 影响任务、证据快照和处理闭环 | 暂停 | 最小血缘验收并确认规则矩阵 |
| 对话助手解释 | 暂停 | 只能消费授权后的确定性结果，不承担遍历或事实计算 |
| Excel 导入 | 暂停 | 模板、校验、幂等及回滚规则由用户确认 |
| 现有未验收链路 | 待验收 | 按第 3 节逐项验证，不以构建通过替代浏览器验收 |

## 6. 恢复开发的推荐切入点

1. 按 `HANDOFF.md` 完成只读接管，确认 Git 和代码没有在暂停期间改变。
2. 先补齐最小血缘的浏览器验收与后端编译/定向测试，建立稳定基线，不立即扩展模型。
3. 由用户选择一个待业务确认问题；建议优先确认作业平台字段级映射能否可靠导出。
4. 基于真实样例形成最小数据字典和关系契约；任何表名、字段名、类型、长度先交用户确认。
5. 若恢复任务涉及当前运行监控对交易资产的扩展，先拆出最小改动方案并单独批准。

## 7. 公共能力依赖

- 登录会话、Spring Security `@PreAuthorize`、现有 RBAC 菜单和权限码。
- `Result`/`PageResult`、统一异常处理、MyBatis-Plus Mapper 与审计填充。
- 前端请求封装、Ant Design、现有页面布局和 intranet 共享字段组件。
- 公共审批表、通知与 WebSocket 实时刷新（需求/版本相关）。
- 已获批准扩展的公共 `G6Graph` 视图控制能力；血缘节点、边和布局仍留在 intranet 页面内。
- 交易、交易字段、HBase/ES 资产及交易资源关联 Service/Mapper；运行监控只能按已确认契约复用，不得反向改变其业务语义。

