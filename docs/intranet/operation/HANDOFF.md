# 交易运行监控与在线验证中心当前交接

> 更新日期：2026-10-14
>
> 当前阶段：M2 OBS 和实时日志追踪（交互方案已调整，待内网接续）
>
> M1：外网页面已由用户验收，内网 Redis Cluster 适配与真实验收未完成

## 当前现场

- `operation` 已设为当前活跃模块。
- 已建立模块独立 `PLAN.md`、`HANDOFF.md` 及全局状态索引。
- 已新增 operation 流水查询后端、前端页面和定向测试；未新增数据库对象、依赖或全局配置。
- M0 已完成；M1 外网页面已由用户确认无问题；M2 已有独立日志页技术原型，但用户确认最终应改为流水页内抽屉，当前原型不得视为 UI 验收通过。
- 当前 Git 基线为 `main` 跟踪 `origin/main`，初始化时双方均为 `9e4ba56` 且工作区干净；本次仅有协作文档改动。

## 已确认协同边界

- 与交易资产/血缘共用当前工程和主分支，不创建独立分支、Worktree 或子工程。
- 交易资产与血缘处于保护状态；operation 可以复用已有 API/Service，但不得未经批准修改其数据库、字段、API 契约或业务逻辑。
- 如确需扩展受保护模块，先提交最小改动方案，说明原因、范围、替代方案及代价、回归计划，等待用户批准。
- 新模块仍放在 intranet 目录/包下，复用现有 UI、组件、鉴权、统一响应和代码规范。
- 所有数据库表名、字段名、类型和长度必须由用户确认；M0 只能提出候选设计，不能写入结构脚本。
- 不新增独立架构，不重复封装通用能力，不将真实内网连接或数据写入仓库。

## M1 当前实现

1. 后端 `org.lbl.intranet.operation.flow` 提供环境、响应筛选项及分页查询 API。
2. `TransactionFlowParser` 按已确认十字段和时间格式解析；非法记录直接忽略。
3. `TransactionFlowQueryService` 完成组合过滤、稳定倒序、精确总数和内存分页。
4. `TransactionFlowSource` 隔离原始数据来源；`MockTransactionFlowSource` 仅供 `default/local` 外网开发。
5. 前端 `pages/intranet/operation/flow` 复用 SmartTable 和交易资产列表，提供完整查询页面且不含导出。

## 接续入口

1. 内网新增 `RedisClusterFlowSource`，按环境管理独立 Lettuce 集群连接，不把连接注册成全局默认 Redis Bean。
2. 从内网配置读取环境清单、节点、认证、TLS、超时和固定响应码；真实凭据不得提交仓库。
3. 核对真实 List 文本的分隔和转义细节；如有差异只调整 `TransactionFlowParser`。
4. 手工配置菜单：路由建议 `/intranet/operation/flow`，组件 `intranet/operation/flow/index`，权限 `intranet:operation:flow:list`。
5. 完成浏览器与真实集群验收后，才能把 M1 标记为完成。

开发环境已有辅助脚本：`db/operation/01_operation_flow_menu.sql` 用于创建并授权菜单，`02_operation_flow_verify.sql` 用于只读核对，`99_operation_flow_rollback.sql` 用于开发环境回退。脚本均不创建业务表或流水数据，也不会自动执行。

## M2 当前实现与接续

1. 后端 `org.lbl.intranet.operation.log` 将历史日志和实时日志拆成两个外部数据源接口；外网仅启用虚构模拟源。
2. 前端 `pages/intranet/operation/logs` 包含 OBS 历史日志和应用实时日志两个页签，复用 CodeViewer。
3. 历史日志支持查询、预览和原始文件下载；下载权限为 `intranet:operation:log:download`。
4. 流水列表在具备 `intranet:operation:log:list` 时显示日志追踪入口，并预填环境、日期和流水号。
5. 重新执行 `db/operation/01_operation_flow_menu.sql` 可增量创建日志菜单及权限；执行后需要重新登录。
6. 内网接续时分别新增 OBS 历史日志源和内部 HTTP 实时日志源，不修改页面与 Controller 契约。

### 用户最新确认的最终交互

- 不保留独立日志追踪页面或菜单。流水表格最左侧显示日志图标，点击后打开宽抽屉。
- 抽屉顶部左侧为流水信息，右侧为相关日志文件；底部 CodeViewer 展示按全局流水号匹配出的日志内容。
- 应用日志按 20 MB 和准小时 rollover，已落盘文件基础命名为 `容器名-yyyyMMddHH.log`。
- 相关文件需要包含交易时间附近的已滚动日志，以及当前尚未落盘的 `.log.crn` 文件，并逐个提供下载。
- 当前前端 `pages/intranet/operation/logs/index.tsx`、流水页路由跳转和 SQL 中的独立日志菜单是待重构原型；后端历史/实时数据源和下载边界可以保留。

### 明日内网推荐切入点

1. 先确认容器映射、OBS 目录、同小时多次 20 MB rollover 的真实文件名、`.log.crn` 来源和实时 HTTP 下载协议。
2. 定义聚合响应 `FlowLogContext`：流水概要、匹配文本、相关文件列表、文件类型（滚动/当前）、大小、修改时间、是否截断及警告。
3. 将日志查看/下载权限调整为流水菜单下的按钮权限，停止使用独立日志页面菜单。
4. 在流水页实现 `FlowLogDrawer`：最左图标入口、顶部双栏、底部 CodeViewer；关闭抽屉后保留原查询和分页状态。
5. 用跨整点、跨日、同小时多次 20 MB rollover、仅命中 `.log.crn`、无匹配日志等场景验收。

### 当前 Git/工作区现场

- 分支仍为 `main`，跟踪 `origin/main`，当前基线提交为 `9e4ba56`。
- M1/M2 operation 代码、测试、菜单 SQL及多模块文档均为未提交改动；未 commit、push、pull、merge 或创建分支。
- 交易资产与血缘保护范围没有被修改；没有新增业务数据库表、依赖、OBS SDK、CXF 或真实环境配置。
- 跨设备前必须由用户将这些未提交改动安全提交并推送；仅文档无法同步本机未提交代码。

## 待业务确认清单

- 内网环境数量、环境标识、Redis Cluster 节点、认证、TLS、超时和环境访问权限。
- 原始记录是否存在逗号转义或空字段；真实固定响应码列表；单日峰值和查询限流。
- OBS 的产品定义、检索接口，以及实时日志源和实时推送方式。
- SOAP 服务清单、WSDL/报文来源、认证、证书、网络白名单、超时与审计。
- 动态测试表单对交易版本、请求字段、数组/嵌套字段和默认值的选择规则。
- 响应比较的忽略字段、排序、空值、类型、时间、数值精度和差异等级。
- 共享样例的权限、版本、脱敏、审核、保留和复用范围。
- 是否需要新增持久化对象；若需要，逐项确认表名、字段名、类型、长度、默认值、索引和生命周期。

## 本次验证

- 已执行：`mvn test "-Dtest=TransactionFlowParserTest,TransactionFlowQueryServiceTest,OperationLogTraceServiceTest"`，7 项测试全部通过。
- 已执行：针对 operation 流水和日志页面的独立 TypeScript 检查，通过。
- 已执行：`npm run build`；被既有 `G6Graph.tsx` 和需求页面 4 个 TypeScript 错误阻断，输出未出现 operation 新页面错误。
- 已执行：用户人工确认 M1 流水查询页面无问题。
- 未执行：M2 抽屉新方案实现和人工验收、真实 Redis Cluster、OBS 和实时 HTTP 验收；当前独立日志页仅为需要重构的技术原型。
- 未修改交易资产/血缘代码、数据库、依赖、默认 Redis、全局配置或基线公共组件。

