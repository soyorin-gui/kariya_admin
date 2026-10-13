# 内网迁移与部署清单

> 更新日期：2026-10-13。本文只描述当前可部署的最终结构，不保留已执行的历史增量脚本。

## 数据库结构

用户确认当前开发数据库已执行过所有历史脚本。历史增量、菜单/角色种子及演示数据已从仓库删除，避免被误用于内网部署。

| 顺序 | 文件 | 用途 | 执行边界 |
| --- | --- | --- | --- |
| 1 | `backend/src/main/resources/db/init_schema.sql` | 系统基线表结构，含公共审批与最终的部门调动表 | 仅用于空数据库；不含初始数据 |
| 2 | `backend/src/main/resources/db/intranet_schema.sql` | 17 张最终 `intranet_*` 业务表及索引 | 在步骤 1 后、空的内网表空间中执行；不含任何数据或 RBAC 种子 |

当前内测库执行 [intranet_schema_update_requirements.sql](../../backend/src/main/resources/db/intranet_schema_update_requirements.sql) 以加入需求流转记录并删除重复交易关联字段。

两个结构脚本均使用直接 `CREATE TABLE`，重复执行会失败。这是面向新部署的明确保护，不是可升级脚本。已有环境不得执行，应由 DBA 根据表、列和索引清单核对差异并制定专用变更方案。

`intranet_schema.sql` 包含：交易、交易报文字段、HBase 表/字段、ES 索引/字段、交易资源关联、三类需求明细，以及四张交易版本快照表。它不建立跨业务外键；引用完整性仍由服务层负责，这是 D-019、D-022 和 D-040 的既有边界。

## 非结构数据

仓库不再提供 `init_data.sql`、内网菜单、角色、部门、权限授权或演示数据脚本。内网部署前由目标环境管理员按已确认的权限码、菜单路由和组织结构手工初始化，并在部署记录中保留执行人、时间和结果。

运行时代码需要至少具备：

- 内网菜单及权限：`intranet:transaction:*`、`intranet:dataasset:*`、`intranet:requirement:list/add/update/submit/assign`；
- 需求审批候选部门 `INTRANET_REQUIREMENT_APPROVAL`，角色 `department_approver`；
- 研发部门 `INTRANET_DEVELOPMENT`，角色 `intranet_developer`；
- 目标人员的部门、角色与菜单授权。

这些值是部署配置而非演示数据；不得从旧测试库复制真实账号、联系人或业务数据。

## 验收与环境

- 部署前核对 MySQL 8.0+、字符集 `utf8mb4`、时区及数据库备份策略。
- 部署后先验证表、唯一键和索引，再验证动态菜单、权限、公共审批、通知和交易版本流程。
- `mvn test` 会启动完整 Spring 上下文，可能连接并改写当前开发环境的超管权限；在目标内网执行前必须确认隔离的测试数据库。
- ES/HBase 连接、认证、网络白名单及真实数据导入仍未实施；仅有元数据管理能力。

## 已删除的历史脚本

`db/approval/` 与 `db/intranet/` 下的 V/AP 历史增量已在 2026-10-13 删除，包括回填、旧表清理、菜单/角色种子和虚构演示数据。它们已经完成其开发库演进职责，不是新内网环境的部署输入。

如需升级一个已部署的旧版本，必须依据该环境真实 schema 重新设计并评审专项迁移；不得从 Git 历史直接挑选执行旧脚本。
