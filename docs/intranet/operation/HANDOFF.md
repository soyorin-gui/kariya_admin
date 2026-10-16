# 交易运行监控与在线验证中心当前交接

> 更新日期：2026-10-16
>
> 当前阶段：外网阶段已验收，模块暂停；M6 及后续内网开发由用户自行设计和实现。
>
> M1：外网页面已由用户验收，内网 Redis Cluster 适配与真实验收未完成

## 当前现场

- `operation` 外网阶段已收口，当前暂停，等待未来内网接续。
- 已建立模块独立 `PLAN.md`、`HANDOFF.md` 及全局状态索引。
- 已新增 operation 流水查询后端、前端页面和定向测试；共享测试样例新增 `intranet_operation_test_case` 表脚本；未新增依赖或全局配置。
- M0–M5 外网功能均已由用户验收；M6 与真实内网连接、规则和服务逻辑均由用户自行接续。
- 当前 Git 基线为 `main` 跟踪 `origin/main`，二者均为 `029a734`，工作区干净；M4/M5 模拟调用、共享测试样例和相关文档已提交并推送。

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

1. 后端 `org.lbl.intranet.operation.log` 保留历史与当前日志两个数据源接口；外网仅启用虚构模拟源。
2. 前端 `pages/intranet/operation/flow/FlowLogDrawer.tsx` 由流水表格最左侧图标打开，关闭后保留原查询与分页状态。
3. `POST /api/intranet/operation/logs/context` 聚合流水概要、匹配文本、相关文件、截断与警告；已删除旧历史/实时独立查询接口和请求/响应模型。
4. 滚动和当前日志通过带文件类型的受控下载路由下载，下载权限为 `intranet:operation:log:download`。
5. 菜单 SQL 会清理独立“日志追踪”菜单，并在“流水查询”下创建查看、下载按钮权限；尚未执行或人工验证。

### 用户最新确认的最终交互

- 不保留独立日志追踪页面或菜单。流水表格最左侧显示日志图标，点击后打开宽抽屉。
- 抽屉顶部左侧为流水信息，右侧为相关日志文件；底部 CodeViewer 展示按全局流水号匹配出的日志内容。
- 应用日志按 20 MB 和准小时 rollover，已落盘文件基础命名为 `容器名-yyyyMMddHH.log`。
- 相关文件需要包含交易时间附近的已滚动日志，以及当前尚未落盘的 `.log.crn` 文件，并逐个提供下载。
- 已删除流水页独立路由跳转、独立日志菜单配置及旧的日志查询 API；历史/当前数据源边界和受控下载机制保留。

### 明日内网推荐切入点

1. 在开发环境执行并验证菜单脚本，重新登录后检查按钮权限和独立菜单清理。
2. 用外网模拟源人工验收抽屉：图标打开、文件下载、无匹配内容与截断提示。
3. 进入内网前确认容器映射、OBS 目录、同小时多次 20 MB rollover 的真实文件名、`.log.crn` 来源和实时 HTTP 下载协议。
4. 用跨整点、跨日、同小时多次 rollover、仅命中当前文件、无匹配日志等场景校准内网数据源。

## M3 当前实现与接续

1. 新增 `pages/intranet/operation/invoke`；选择交易后读取既有请求字段树，递归生成对象、数组和标量输入表单。
2. 当前表单使用临时类型映射：数值、布尔、日期/时间和文本；M4 在同页将字段值生成 SOAP 报文并展示原始响应。
3. 新菜单路由为 `/intranet/operation/invoke`，权限为 `intranet:operation:invoke:list`；菜单 SQL、验证和回退脚本已同步更新。
4. M3 本身未新增后端接口；后续 M4/M5 已新增 operation 模拟接口。后续共享测试样例已新增 `intranet_operation_test_case` 脚本；未新增依赖或真实环境配置，未修改交易资产/血缘 API 或业务逻辑。
5. 用户已确认当前在线调试页面和请求/响应交互无误；对象嵌套、标量数组、对象数组等边界样例仍待专项验证。业务仍需确认默认值、枚举、格式、数组语义与版本选择规则。

## M4 当前实现与限制

1. 在线调试页现支持目标地址选择/输入及 SOAP 1.1/1.2 临时报文预览；点击发送仅生成固定 SOAP 成功响应，不发起网络请求。
2. 目标地址控件仍默认展示交易资产的 `esfServiceAddress`，用于确认后续交互；静态响应模式不读取或调用该地址。
3. 已验证原前端直连会被目标应用 CORS 策略拦截；浏览器预检响应缺少 `Access-Control-Allow-Origin`，因此真实 SOAP POST 未成功发送。
4. 内网旧系统的合理链路为前端调用本系统后端，再由后端 HttpClient/CXF 调用交易容器；后端到后端不受浏览器 CORS 限制。正式实现仍待协议确认。
5. 当前单笔静态响应明确显示模拟状态，包含虚构的 `Y`、`MOCK_SUCCESS`、模拟流水号和耗时；不得作为真实调用结果。
6. 在线调试页新增“一键调用”：加载全部交易资产分页结果，在宽抽屉中填写统一容器地址，逐笔调用 `POST /api/intranet/operation/invocations/mock`，展示进度和每笔可展开的模拟请求/响应 XML。
7. 模拟接口复用交易资产只读 Service 获取交易信息，不修改其契约；接口不构建 HttpClient，也不访问目标地址，沿用 `intranet:operation:invoke:list` 权限。
8. 真实批量调用的交易状态范围、请求参数来源、并发/超时/停止/重试策略和目标地址白名单均为待确认。
9. 单笔报文区已修复 Ant Design 非活动 Tab 被业务 CSS 强制显示的问题；现在请求和响应 CodeViewer 互斥显示。进入页面/切换交易回到请求页签，发送时立即跳到响应页签，并同时显示按钮 loading 与响应 loading，避免重复点击。
10. 用户已人工确认单笔静态响应、Tab/loading 和一键批量模拟调用效果无误；该验收只覆盖外网模拟交互，不代表真实 SOAP 链路已完成。

## M5 当前实现与限制

1. 用户明确要求独立页面和菜单，覆盖早期“放在在线验证内部”的建议；新增路由 `/intranet/operation/compare` 和权限 `intranet:operation:compare:list`。
2. 对比页复用 operation 共享动态请求表单，同一请求 XML 发送给两个虚构环境；不修改交易资产模块。
3. 后端提供环境列表和模拟对比接口，返回双侧状态、响应码、耗时、流水号、原始响应 XML，以及一致/值不同/仅 A/仅 B 字段列表。
4. 页面提供环境 A/B 互斥选择；点击“开始对比”后立即打开宽抽屉，调用期间显示 loading 且禁止误关闭，完成后在抽屉内展示双侧摘要与 CodeViewer、差异高亮和“仅查看差异”。主页面不再纵向追加结果区域。
5. 已修正结果摘要 `Descriptions column={2}` 下单行 `span` 总和为 3 的配置错误；所有摘要项按两列自然排布，等待浏览器确认控制台警告消失。
6. 当前差异由模拟接口明确构造，不是通用 XML 解析器；动态字段忽略、数组顺序、空值、类型、精度、日期和命名空间规则仍待确认。
7. `01_operation_flow_menu.sql` 已加入独立菜单，`02_operation_flow_verify.sql` 和 `99_operation_flow_rollback.sql` 已同步；尚未执行数据库验证。

### 跨设备恢复入口

1. 先重新检查 `main` / `origin/main`、工作区和本文件；当前已提交基线为 `029a734`，M4/M5 模拟功能、共享测试样例和本次文档调整均已提交。
2. 在 `/intranet/operation/invoke` 验证单笔静态响应：点击发送后不应出现 Network 中的目标容器请求，应自动切换到响应页签并显示模拟 SOAP XML。
   验证等待期间发送按钮不可重复点击，响应区显示加载状态；请求和响应 CodeViewer 不得同时纵向出现。
3. 点击“一键调用”，填写任意虚构容器地址后启动；应只出现对本系统 `/api/intranet/operation/invocations/mock` 的逐笔请求，进度逐步完成，结果行可展开查看 XML。
4. 同时验收低窗口高度下请求/响应编辑器和批量抽屉不得溢出；选择交易后不应出现未挂载 Form 或独立 Spin 警告。
5. 取得 WSDL/XML 样例、公共 Header、命名空间、SOAPAction、认证和超时规则后，在 `operation` 内实现本系统后端转发，不再恢复浏览器直连。
6. 执行菜单脚本并重新登录，在 `/intranet/operation/compare` 验证 M5：同一交易和请求参数、环境互斥、点击后打开宽抽屉、loading 期间不可误关闭、双响应、差异高亮与仅差异开关；确认主页面下方不再追加结果，控制台无 `Descriptions` 列跨度警告。

### 当前 Git/工作区现场

- 分支仍为 `main`，跟踪 `origin/main`，二者当前基线提交均为 `029a734`，工作区干净。
- `029a734` 已包含 M4 单笔/批量模拟、M5 独立页面/接口/菜单 SQL、共享测试样例以及协作文档。
- 交易资产与血缘保护范围没有被修改；新增 `intranet_operation_test_case` 表脚本及对应服务/测试，未新增依赖、OBS SDK、CXF 或真实环境配置。
- 跨设备接续前应重新核对远端是否仍包含 `029a734`；如无新的本地改动，可直接继续 M5 验收。

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

- 已执行：`git diff --check`，通过。
- 已执行：`npm run build`；仍被既有 `G6Graph.tsx` 1 个和需求页面 3 个 TypeScript 错误阻断，输出未出现 operation M4/M5 改动错误。
- 已执行：`npm exec vite build -- --configLoader runner`，通过，确认 M5 新页面、共享表单和动态页面模块可以完成生产打包；仅有既有大分块提示。
- 已执行：`mvn -q '-Dtest=MockResponseComparisonServiceTest,MockTransactionInvocationServiceTest' test`，通过。
- 已执行：用户人工确认 M1 流水查询页面无问题。
- 已执行：用户人工确认 M2 抽屉页面达到当前外网验收范围。
- 已执行：用户提供浏览器控制台证据，确认原前端直连被目标应用 CORS 策略拦截。
- 已执行：用户人工确认 M4 单笔静态响应、请求/响应 Tab、按钮与响应 loading、一键批量进度及结果展示无误。
- 未执行：M5 菜单 SQL 和浏览器人工验收、M3 嵌套/数组边界样例专项验收、真实后端 SOAP 调用、真实 Redis Cluster、OBS 和实时 HTTP 验收。
- 未修改交易资产/血缘代码、数据库、依赖、默认 Redis、全局配置或基线公共组件。

