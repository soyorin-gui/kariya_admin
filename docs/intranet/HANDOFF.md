# Intranet 跨设备交接

> 本文件保存“当前工作现场”，每次开发结束时覆盖更新对应章节。历史设计结论放入 `DECISIONS.md`，长期计划放入 `PLAN.md`。接手设备必须用真实代码和 Git 命令复核本文件。

## 交接元信息

- 更新时间：2026-10-09（Asia/Shanghai）
- 交接设备/人员：当前 Codex 会话；具体设备标识待用户按需补充
- 当前阶段：P0 开发环境与协同规范
- 当前分支：`main`，跟踪 `origin/main`

## 当前开发任务

初始化轻量级跨设备 Codex 协同机制，只阅读现有项目并创建协作文档，不创建 intranet 业务代码，不修改现有业务逻辑、数据库、依赖、分支或远程配置，不执行 commit/push。

## P1-01 最新现场（2026-10-10）

- 用户已确认交易主表字段并明确允许建表。交易编码为 `VARCHAR(64)`；状态为 `0-3`、默认 `0`；删除为物理删除；四个 ESF 字段必填；打印文件方式为 `0=无/1=异步/2=同步`，方式为无时服务端清空文件配置。
- 已新增独立内网 SQL：`V1__create_intranet_transaction.sql` 创建 `intranet_transaction`；`V2__seed_intranet_transaction_menu.sql` 创建内网交易菜单/按钮权限并授予超管。脚本尚未执行。
- 已新增后端 `org.lbl.intranet.transaction` 的 Entity、Mapper、Request、VO、Service、Controller，提供分页查询、详情、新增、编辑与物理删除，并使用 `intranet:transaction:*` 权限码。
- 已新增前端 `pages/intranet/transaction` 的列表、查询、编辑弹窗和详情抽屉，复用 SmartTable、Permission、Ant Design 与既有请求封装。
- 已将交易弹窗调整为与系统用户管理一致的 660px 横向表单及共享 `system-dialog` 间距规范；新增仅含虚构信息的 `V3__insert_intranet_transaction_demo_data.sql` 供界面验证。
- 交易详情抽屉的 Descriptions 已扩展至抽屉正文全高；交易表单标签已局部改为单行省略展示并补充输入提示；交易状态改为表头筛选，更新时间支持服务端升降序排序。未修改 SmartTable：它已具备将表头 filters/sorter 传入请求的能力。
- P1-02 已确认单表 `intranet_transaction_message_field` 管理请求、响应、对象、数组及嵌套字段；已新增 V4 建表脚本、后端 CRUD 与详情抽屉内的请求/响应树形字段维护。未对同父节点英文名增加唯一约束（该规则尚未确认）。
- 已新增 V5 虚构字段演示数据，覆盖请求字段、响应对象、响应数组及数组内对象/字段，依赖 V1、V3、V4 后按需执行。
- 字段维护已从交易详情抽屉迁至隐藏路由 `/intranet/transaction/fields?transactionId=<id>`，新增 V6 隐藏菜单脚本；页面使用树形行内编辑、保存、取消、删除及新增子字段，不修改动态路由基础设施或 SmartTable。
- 字段设计行内保存会显式提交既有行或草稿行的 `parentId`，避免因未挂载的表单字段导致后端报“parentId不能为空”；请求/响应切换改为 Ant Design 标签页。字段中文名、英文名使用中文必填提示，说明编辑改为本地多行输入，表格编辑控件的宽度和高度已在内网页面样式内调整。未将弹出式文本域提升为基线通用组件。
- 验证：`mvn -q -DskipTests compile` 未执行成功，当前终端没有 `mvn` 命令；`npm run build` 执行到 TypeScript 依赖解析，但基线缺少 `react-markdown`、`remark-gfm` 模块，在既有 `AgentMarkdown.tsx` 停止。两者均未显示 P1-01 源码错误。
- 未修改任何基线业务代码、全局路由、全局权限初始化器、依赖或现有 SQL；仅新增 intranet 目录内容及协作文档。未执行 commit/push。

## 最近完成内容

- 检查仓库结构、Git 状态和近期提交。
- 核实现有技术栈、目录、动态路由、RBAC、审批、通知、实时通道、G6 和 Agent 工具能力。
- 新建根目录 `AGENTS.md`。
- 新建 `docs/intranet/ARCHITECTURE.md`、`PLAN.md`、`HANDOFF.md`、`DECISIONS.md`、`MIGRATION.md`。
- 将审批聚合尚未通用化、RowKey 规则、血缘规则等未知内容显式标记为待确认。

## 已验证功能和测试结果

| 检查/测试 | 结果 |
| --- | --- |
| `git status --short --branch`（修改前） | `main` 跟踪 `origin/main`，工作区干净 |
| 仓库目录与关键文件只读检查 | 已确认前后端分离结构，初始化前无根 `AGENTS.md` 和 `docs/` |
| 前端依赖检查 | 已确认 React/Vite/Ant Design、`@antv/g6` 5.x 及现有 `G6Graph` 封装 |
| 后端依赖与源码检查 | 已确认 Java 17、Spring Boot 3.4.4、Security、MyBatis-Plus、MySQL、Redis、WebSocket |
| 审批复用边界检查 | 已确认公共审批概念存在，但 `ApprovalTaskMapper` 当前直接绑定部门调动表 |
| 业务自动化测试 | 未执行；本次仅修改 Markdown 文档，不安装依赖、不改业务代码 |

## 新增或修改的主要文件

- `AGENTS.md`：全仓库会话初始化、基线保护、隔离、Git 与交接规则。
- `docs/intranet/ARCHITECTURE.md`：总体架构、关系模型、复用边界与待确认项。
- `docs/intranet/PLAN.md`：P0-P6 任务、状态、验收条件和依赖。
- `docs/intranet/HANDOFF.md`：本交接现场和后续统一模板。
- `docs/intranet/DECISIONS.md`：已确认决策与待决策清单。
- `docs/intranet/MIGRATION.md`：未来内网迁移范围与检查清单。

## 未完成事项

- 用户审阅并确认本次协同机制和目录规划。
- 在两台设备分别确认 Java/Maven/Node/npm 版本、实际启动和测试命令。
- 演练一次“设备 A 更新并提交交接文档—设备 B 拉取并复核”的流程。
- P1 前确认权限码命名、菜单配置方式、intranet SQL 目录和首批资产模型。
- P2 前评审审批复用方案；任何公共审批代码修改都需先获批准。

## 当前问题和风险

- Codex 本地会话不跨设备同步；只有已提交并推送到远程的代码和文档可可靠同步。未提交改动必须在切换设备前处理并写入交接。
- 当前审批任务聚合查询仅支持部门调动，内网需求审批不能无改造直接进入统一列表。
- 尚无 ES/HBase 客户端依赖和连接设计；集群版本、认证、RowKey 与一致性策略均待确认。
- 真实内网样例、地址和凭据不得进入本仓库；后续建模需要使用脱敏/虚构样例。

## 下一步建议

1. 用户先确认本次文档和“前端 `src/pages/intranet`、后端 `org.lbl.intranet`”目录约定。
2. 下一次会话按 `AGENTS.md` 执行初始化，只读复核本交接与 Git 状态。
3. 进入 P0 剩余工作：记录两台设备工具版本与基线测试命令，不升级依赖。
4. 再选择 P1 的第一个小切片，建议先做交易/字段/版本和三类数据资产的领域模型评审，暂不写业务代码。

## Git 工作区状态和最近提交

- 初始化前状态：工作区干净，`main...origin/main`，无超前/落后提示。
- 文档创建并核验后的状态：`AGENTS.md` 和 `docs/` 为未跟踪新增项；无已跟踪文件改动。接手时仍须重新执行 `git status` 复核。
- 最近提交：`b20d1c0`，提交说明为 `2026年10月8日 22:10:26`。
- 本次未执行 commit、push、pull、merge、rebase、分支切换或远程配置修改。

## 是否涉及基线公共代码修改

否。本次只新增协作文档，没有修改任何现有前后端代码、配置、SQL、依赖或 Git 配置。

---

## 后续交接填写模板

复制以下结构覆盖本文件上方对应章节；不要只追加流水账。

```markdown
## 交接元信息
- 更新时间：YYYY-MM-DD HH:mm（Asia/Shanghai）
- 交接设备/人员：
- 当前阶段：
- 当前分支及跟踪分支：

## 当前开发任务
- 目标、明确范围、用户批准边界：

## 最近完成内容
- 仅列已完成并能从代码/命令验证的事项。

## 已验证功能和测试结果
| 命令/场景 | 结果 | 备注 |
| --- | --- | --- |

## 新增或修改的主要文件
- 路径：用途与关键改动。

## 未完成事项
- 明确剩余工作和完成标准。

## 当前问题和风险
- 阻塞原因、影响、已尝试方案。

## 下一步建议
1. 给出可直接执行的小步骤。

## Git 工作区状态和最近提交
- `git status --short --branch` 摘要：
- 最近提交 hash/说明：
- 未提交改动是否已推送：

## 是否涉及基线公共代码修改
- 否；或：是，列出文件、原因、用户批准记录、替代方案和回归结果。
```
