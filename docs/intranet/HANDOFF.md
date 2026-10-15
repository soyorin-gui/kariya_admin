# Intranet 当前模块交接索引

> 更新日期：2026-10-15。本文件只记录当前活跃模块和交接入口，不保存模块开发历史。

## 当前活跃模块

- 模块：`operation`（交易运行监控与在线验证中心）
- 状态：开发中，当前进入 M5 多环境响应对比外网模拟页面验收
- 模块计划：[operation/PLAN.md](operation/PLAN.md)
- 当前交接：[operation/HANDOFF.md](operation/HANDOFF.md)
- 限制：未接真实 Redis Cluster、OBS、实时日志 HTTP 或 SOAP；M4/M5 均为明确标记的模拟接口和页面，不调用交易容器。

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

- 当前检查时：`main` 跟踪 `origin/main`，二者均指向 `d25aacd`；M1、M2 抽屉、M3 表单和 M4 旧直连原型已提交并推送。
- 本次在 `d25aacd` 上增加 M4 单笔/批量模拟调用，以及 M5 独立对比页、模拟接口、菜单 SQL 和协作文档，均为未提交改动；未修改数据库、依赖、默认 Redis、全局配置及受保护模块，未创建分支。
- 跨设备继续前需处理本次未提交的 M4/M5 代码及交接文档改动。
