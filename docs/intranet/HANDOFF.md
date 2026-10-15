# Intranet 当前模块交接索引

> 更新日期：2026-10-14。本文件只记录当前活跃模块和交接入口，不保存模块开发历史。

## 当前活跃模块

- 模块：`operation`（交易运行监控与在线验证中心）
- 状态：开发中，当前为 M2 OBS 和实时日志追踪（抽屉方案待内网接续）
- 模块计划：[operation/PLAN.md](operation/PLAN.md)
- 当前交接：[operation/HANDOFF.md](operation/HANDOFF.md)
- 限制：当前独立日志页是待重构原型，不应继续配置为最终菜单；未接真实 Redis Cluster、OBS 或实时日志 HTTP；M3-M6 尚未开始。

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

- 初始化检查时：`main` 跟踪 `origin/main`，二者均指向 `9e4ba56`，工作区干净。
- 本次新增 operation M1 流水查询代码和测试并更新协作文档；未修改数据库、依赖、默认 Redis、全局配置及受保护模块，未创建分支，未提交或推送。
- 当前文档改动必须由用户确认后再决定是否提交；跨设备接续前应重新检查工作区并确认文档已进入远端提交。
