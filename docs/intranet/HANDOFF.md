# Intranet 当前模块交接索引

> 更新日期：2026-10-16。本文件只记录当前活跃模块和交接入口，不保存模块开发历史。

## 当前活跃模块

- 模块：`statistics`（交易统计分析）
- 状态：S1–S3 已由用户验收；S4 新窗口无 Layout 交易监控大屏与集中 Mock 已实现，待用户验收
- 模块计划：[statistics/PLAN.md](statistics/PLAN.md)
- 当前交接：[statistics/HANDOFF.md](statistics/HANDOFF.md)
- 限制：不接入真实 Redis、原有统计定时任务或后端聚合计算；S1 只由前端 API Mock 提供演示数据。

## 最近暂停模块

- 模块：`dataexplorer`（多数据源查询工作台）
- 状态：D1 前端页面、集中 Mock 和开发/验证菜单已由用户验收；D2 真实内网查询接入由用户自行实施
- 模块计划：[dataexplorer/PLAN.md](dataexplorer/PLAN.md)
- 当前交接：[dataexplorer/HANDOFF.md](dataexplorer/HANDOFF.md)

- 模块：`operation`（交易运行监控与在线验证中心）
- 状态：外网阶段已验收，模块暂停；M6 及后续内网开发由用户自行设计和实现
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

- 当前检查时：`main` 跟踪 `origin/main`，二者均指向 `029a734`；工作区包含尚未提交的内网模块与协作文档改动，跨设备接续前需由用户自行提交或保留交接文件。
- `029a734` 已提交并推送 M4 单笔/批量模拟调用、M5 独立对比页/模拟接口/菜单 SQL、共享测试样例能力及协作文档；新增 operation 测试样例表脚本，但未修改默认 Redis、全局配置或受保护的 lineage 模块，未创建分支。
- 跨设备可直接从 `029a734` 恢复；本阶段无需继续 operation 外网开发。
