# 多数据源查询工作台当前交接

> 更新日期：2026-10-16
>
> 当前阶段：D1 外网前端阶段已由用户验收，模块暂停；D2 真实内网接入由用户自行实施。

## 当前实现

- `index.tsx` 提供环境、Elasticsearch/HBase/Redis 数据源切换、连接标识、资源浏览器、查询编辑器、模板选择、执行状态和结果区域。
- `mock.ts` 集中提供环境、资源、模板和查询结果；覆盖 ES 命中/空结果/错误、HBase Get/Scan、Redis String/Hash/List/Set，以及加载延迟。
- `api.ts` 是唯一 Mock 适配入口；后续内网接入仅替换此处，不让页面组件直接依赖 Mock。
- 查询结果支持动态列、分页、原始 JSON 的 CodeViewer、记录详情抽屉，以及 CodeViewer 自带格式化、复制、下载、全屏功能。

## 未实现边界

- 已新增开发/验证菜单脚本 `backend/src/main/resources/db/dataexplorer/01_dataexplorer_menu.sql`；未修改公共路由或菜单基础设施，也未新增后端 Controller、数据源客户端、连接配置或真实查询逻辑。
- 未连接 Elasticsearch、HBase 或 Redis，也不以 Mock 结果伪装真实内网查询。
- HBase 和 Redis 的游标、扫描限制、命令白名单、脱敏与审计规则待内网实现时确认。

## D1 验收步骤

1. 在开发/验证数据库执行 `backend/src/main/resources/db/dataexplorer/01_dataexplorer_menu.sql` 并重新登录；进入 `/intranet/dataexplorer`，确认环境、数据源和连接标识显示正常。
2. 切换三种数据源并选择资源、模板：验证 ES 正常/空结果/错误，HBase Get/Scan，Redis String/Hash/List/Set。
3. 验证编辑器格式化、清空、复制、下载和全屏；HBase 参数表单应同步更新查询描述。
4. 验证加载、错误、空结果、动态表格分页、原始 JSON 与记录详情抽屉。
5. D1 已由用户验收，停止外网开发；D2 由用户在内网实现真实 Java 查询和数据源接入。

## 本次验证

- 已执行：`npm run build`。D1 文件未出现 TypeScript 错误；构建仍被既有 `G6Graph.tsx` 1 处和需求页面 3 处 TypeScript 错误阻断，未修改这些基线问题。
- 已执行：`npm exec vite build -- --configLoader runner`。构建进入 `transforming` 阶段，但当前终端未返回最终成功或失败摘要，因此不作为通过证据。
- 已执行：`git diff --check`，通过。
- 未执行：浏览器人工验收、菜单/权限初始化、真实 Elasticsearch、HBase、Redis 或后端接口联调。
