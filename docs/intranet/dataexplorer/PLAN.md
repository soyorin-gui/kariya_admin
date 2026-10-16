# 多数据源查询工作台开发计划

> 更新日期：2026-10-16
>
> 模块标识：`dataexplorer`
>
> 当前状态：D1 外网前端阶段已由用户验收，模块暂停；D2 由用户在内网自行实施。

## 阶段总览

| 阶段 | 目标 | 状态 | 验收条件 |
| --- | --- | --- | --- |
| D0 | 现有能力分析 | 已完成 | 已确认动态路由、菜单、请求层、CodeViewer、SmartTable 和前端 Mock 边界 |
| D1 | 前端查询工作台 | 已完成 | 三数据源可切换，资源浏览、模板、编辑器、执行状态、动态结果和详情展示已由用户验收 |
| D2 | 内网真实查询接入 | 暂停 | 由用户在内网完成 Java 查询实现、环境配置和真实数据验收 |

## D1 实现范围

- 页面位于 `frontend/src/pages/intranet/dataexplorer/`，采用现有动态菜单路由约定；已新增独立开发/验证菜单脚本，未修改路由基础设施。
- `api.ts` 是页面唯一数据访问入口；D1 通过集中 `mock.ts` 返回正式 TypeScript 类型，不在页面组件中硬编码业务演示数据。
- 支持 Elasticsearch JSON DSL、HBase Get/Scan 参数生成、Redis 受控只读命令模板；不连接任何真实数据源。
- 结果支持动态表格、分页、原始 JSON、记录详情、复制/下载/格式化和错误、空数据、加载状态。

## D2 接续边界

- 内网实现可将 `api.ts` 中的 Mock 调用替换为 `request` 接口调用，保留页面类型与交互。
- 建议接口：环境列表、资源目录、查询执行和按需记录详情；具体请求字段、游标和权限由内网实现时确认。
- 菜单脚本为 `backend/src/main/resources/db/dataexplorer/01_dataexplorer_menu.sql`，路由 `/intranet/dataexplorer`、组件 `intranet/dataexplorer/index`、权限 `intranet:dataexplorer:list`；仅供开发/验证环境手工执行。
