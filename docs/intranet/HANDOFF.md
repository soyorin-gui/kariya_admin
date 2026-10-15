# Intranet 当前模块交接索引

> 更新日期：2026-10-15。本文件只记录当前活跃模块和交接入口，不保存模块开发历史。

## 当前活跃模块

- 模块：`operation`（交易运行监控与在线验证中心）
- 状态：开发中，当前为 M4 SOAP 在线调用前端直连原型
- 模块计划：[operation/PLAN.md](operation/PLAN.md)
- 当前交接：[operation/HANDOFF.md](operation/HANDOFF.md)
- 限制：未接真实 Redis Cluster、OBS 或实时日志 HTTP；SOAP 调用为浏览器直连原型，依赖目标应用 CORS，未接 WSDL、命名空间、认证或后端转发。

## 暂停模块

- 模块：`lineage`（交易资产与血缘分析）
- 状态：暂停、受保护
- 模块计划：[lineage/PLAN.md](lineage/PLAN.md)
- 冻结交接：[lineage/HANDOFF.md](lineage/HANDOFF.md)
- 恢复要求：先核对模块文档、相关设计、真实代码、测试和最近 Git 修改，向用户汇报并等待确认后再改代码。

## 共享文档入口

- 总体架构：[ARCHITECTURE.md](ARCHITECTURE.md)
- 长期决策：[DECISIONS.md](DECISIONS.md)
- 迁移与部署：[MIGRATION.md](MIGRATION.md)
- 全局状态：[PLAN.md](PLAN.md)

## 当前 Git 现场

- 当前检查时：`main` 跟踪 `origin/main`，二者均指向 `fe29d54`；工作区包含未提交的 operation M2–M4 代码、菜单脚本、测试和协作文档。
- `fe29d54` 已提交 operation 的 M1 流水查询、M2 日志后端原型、菜单 SQL、定向测试与多模块协作文档；本次未修改数据库、依赖、默认 Redis、全局配置及受保护模块，未创建分支。
- 跨设备继续前必须先将本次未提交改动安全提交并推送；仅远端的 `fe29d54` 不包含 M2 抽屉、M3 表单和 M4 前端直连原型。
