# Intranet 开发交接

> 当前工作现场；长期模型见 `ARCHITECTURE.md` / `DECISIONS.md`，计划见 `PLAN.md`，脚本见 `MIGRATION.md`。

## 0. 最新会话补充（2026-10-13，P2 交易版本）

- 用户已完成需求提交流程和前端展示的基本验收，确认本阶段可继续；不同普通用户角色的审批权限回归按用户计划留到最终集中验证。
- 用户确认：一个需求只关联一个交易，但同一交易允许被多个新增/变更需求依次关联；每个已通过需求最多生成一个版本。已移除“交易已关联其它需求”的反向唯一限制，仍不增加 `intranet_requirement.transaction_id` 唯一约束。
- 新增但未执行 `db/intranet/V16__create_intranet_transaction_version.sql`：交易主快照、报文字段快照、HBase 关系快照、ES 关系快照四张表。版本号按交易从 V1 递增；每个需求通过唯一键最多生成一次；生效新版本时旧活动版本转历史，但历史内容不覆盖。
- 需求列表的已通过新增/变更需求新增“实施”入口：可分配交易和开发负责人，并显式点击“版本生效”。生效前要求需求及公共审批均为已通过、已分配交易和开发负责人、关联交易未下线；生效后交易状态更新为“已投产”，实施配置锁定。
- 交易详情新增版本历史；可查看任一版本的交易基础信息、报文字段及 ES/HBase 资源关系快照。已有版本的交易禁止删除，避免版本成为孤立历史。
- 本轮版本能力全部位于 intranet 目录，复用既有 `intranet:requirement:assign` 和 `intranet:transaction:list` 权限，没有修改公共审批基线或新增全局权限码。
- 验证：后端主源码编译通过（311 个源码）；`ApprovalWorkflowServiceTest` 4/4、`TransactionVersionServiceTest` 1/1，共 5/5 通过；前端 `npm.cmd run build` 通过（5327 个模块，仅既有 chunk 体积与 D-012 页面扫描告警）。数据库脚本未执行，浏览器版本生效流程未验收。

## 0. 最新会话补充（2026-10-12，SmartTable 滚动容器与固定表头）

- 会话范围：**只做日志页布局问题**（用户明确要求不接手 P2）。本轮未执行任何 Git 写操作，未执行数据库脚本，未修改 intranet 业务代码。
- 问题：在操作日志页根节点加 `page-fill` 后，表格内容溢出卡片且完全不滚动。原因是 `page-fill` 约定要求卡片内有一个直接子元素 `.card-scroll` 承担滚动，而 `SmartTable` 不渲染它——搜索区/工具栏与 `<Table>` 是并列的兄弟节点，于是 `.page-fill > .table-card > *` 把两者都冻结在内容高度，且没有任何元素带 `overflow: auto`。
- 修复（基线改动，用户批准，见 D-038）：`frontend/src/components/SmartTable/SmartTable.tsx` 固定渲染 `<div className='card-scroll' ref>` 包住 `<Table>`，并默认传 `sticky={{ getContainer: () => scrollRef.current ?? window }}`。搜索区/工具栏留在容器外层，因此在 `page-fill` 页面里保持固定、只有表格滚动；`.card-scroll` 样式只作用于 `page-fill` 作用域，未加 `page-fill` 的页面不启用卡片内滚动（但会新增 antd 固定表头）。
- 已启用 `page-fill` 的页面：`pages/system/operatelog`（用户自行添加）、`pages/system/loginlog`（本会话添加）。
- 同轮后续（用户实测反馈后）：用户确认效果符合预期，但提出①分页会跟着滚；②`loginlog` 出现一条只能滚一点点、形似"死"的滚动条。经核对，②是**真实溢出**：`page-fill` 下卡片高度 = min(内容高度, 可用高度)，所以"内容没超过卡片"在溢出时恒成立；`loginlog` 按 10 行估算纵向需求约 956px，而 1080p 屏幕的浏览器内视口通常只有 880~950px，必然小幅溢出。
- 修正一（机制，`styles/global.css`）：`.card-scroll` 不再自己滚动，只做 flex 传高度；高度沿 `.ant-table-wrapper` → `.ant-table` → `.ant-table-container` 传导，**只有 `.ant-table-body` 滚动**；表头与分页成为滚动区之外的兄弟节点，**由布局钉住**。`.ant-table-body` 需 `overflow-y: auto !important` 覆盖 rc-table 的内联 `overflow-y: hidden`（升级 antd 大版本须复查）。
- 修正二（页面 props）：`loginlog` / `operatelog` 的 SmartTable 加 `size='small'`，10 行由约 550px 降到约 390px，常见视口下不再出现那条小行程滚动条。
- **回退范围（用户确认）**：只回滚"为日志页竖向滚动条所做的改动"——**移除 `loginlog` 根节点的 `page-fill`**（该页出现过小行程竖滚动条）+ 移除两页的 `size='small'`（B2 无效）。
- **明确保留**：`operatelog`（操作日志，用户口中的"审计页"）的 `page-fill` 已恢复，并要求保留"分页不参与滚动"的 A 设计；字段设计页的 `page-fill`、机制修正 A（`global.css`）、D-038 的 SmartTable 改动全部保留。
- 当前 `page-fill` 使用点共 3 处：`pages/system/operatelog`、`pages/intranet/dataasset/fields`、`pages/intranet/transaction/fields`。
- **选择器与 overflow 修正（用户两次实测反馈）**：①A 的第一版用直接子选择器写高度链与分页，而 antd 的 Table 结构是 `.ant-table-wrapper > .ant-spin-nested-loading > .ant-spin-container > [.ant-table, .ant-pagination]`——分页不是 `.ant-table-wrapper` 的直接子元素，高度链也在 `Spin` 两层处断掉，已改为穿过 spin 两层的统一 flex 链 + 分页后代选择器；②第二版为"兜底"给 `.card-scroll` 加了 `overflow: auto`，与 `.ant-table-body` 形成**两条滚动条**，已移除该声明。最终 `.card-scroll` 只做 flex 传高度且不设 overflow，`.ant-table-body` 是唯一滚动者。全过程与教训已记入 D-038。
- 验证：`tsc --noEmit` 通过；`npm run build` 通过（修正后 ✓ built in 23.77s）。**布局效果构建验证不出**，见 §4。
- 影响面提醒：修正一改的是 `.card-scroll` 共同规则，因此**字段设计页的滚动机制也变了**（字段页无分页，预期视觉一致，但必须重新目视）；其它未加 `page-fill` 的列表页会新增固定表头与可能的吸附式横向滚动条，需按 §4 回归。

## 0. 最新会话补充（2026-10-13，Codex 接管修复）

- Git 基线：本轮开始时 `main` = `origin/main` = `968e214`、工作区干净；工作期间仓库由用户侧前进并同步到 `d04ae00`，该提交已包含三类明细及此前审批接入等改动。Codex 未执行 commit、push、pull、merge、rebase、reset、clean 或数据库脚本；当前未提交内容为审批详情聚合/权限修正及本节文档更新。
- 已拒绝需求现在允许修订后重新提交：后端锁定原公共审批实例并调用 `ApprovalWorkflowService.resubmit`，保留旧轮次、创建下一审批轮次，同时刷新业务标题/摘要并把需求状态改回审批中；前端在已拒绝状态显示“重新提交”。
- 需求类型创建后固定：编辑弹窗禁用需求类型，后端同时拒绝修改；如需改变类型，按用户规则删除后重新新建。
- 新增交易明细补齐“是否投产数据”“是否生成文件/文件生成范围”“是否涉及计算/计算场景”；文件或计算选择“否”时清空对应附属内容。
- 三类固定明细现均已具备后端一对一读写和前端编辑表单：新增交易、交易变更、交易调用均只允许创建人在草稿/已拒绝状态编辑；没有新增或修改数据库表字段。
- 公共审批中心和消息中心共用 `RequirementApprovalDetailModal`，通过单个审批详情聚合接口只读展示需求概要及对应类型的全部固定明细；待办候选人可直接通过或填写原因拒绝。弹窗因属于复用组件，按用户要求保持 820px，不做加宽；未知业务类型仍保留原提示，部门调动流程不变。
- 审批详情和通过/拒绝不再错误依赖需求管理页 `list/submit` 权限：接口仅要求正常登录，服务层再校验申请人、当前轮次候选人/已处理人或超级管理员，普通 `department_approver` 无需开放需求管理菜单且不能查看无关申请。
- 需求提交/重新提交会通知申请人、候选审批组与超级管理员；通过/拒绝会通知申请人；撤销会产生申请人通知。上述动作均在事务提交后发送 `notification.created` 和 `approval.request.updated` 实时事件。顶栏通知跳转新增 `businessType` / `notificationType` 参数，避免不同业务 ID 冲突并保留审批按钮语义。
- 用户确认本阶段不增加需求行锁/乐观锁、不增加 `transaction_id` 唯一约束、不增加提交完整性校验、不把明细 Entity 改为 DTO，也保留明细表中的冗余字段；这些均按内网低并发和人工审批纠错边界处理，不再列为当前缺陷。
- 审批中心详情这一代码前置项已完成，但浏览器端正向/拒绝/重提/通知/实时刷新和非超管权限仍待人工验证，验证完成前不把需求审批流程标记为验收通过。
- 验证：`mvn.cmd -o -B -DskipTests compile` BUILD SUCCESS（296 个主源码）；`ApprovalWorkflowServiceTest` 新增重新提交用例后 4/4 通过且不启动 Spring/数据库；`npm.cmd run build` 通过（5325 个模块，仅既存 chunk 体积告警及 D-012 已知页面扫描提示）；`git diff --check` 通过。三类表单和普通审批人权限仍待浏览器人工验证。
- 本轮修改了 intranet 需求服务/页面、公共审批测试，并按需求审批接入需要修改了用户查询 Mapper、审批中心、消息中心和顶栏通知跳转；没有修改数据库脚本或执行迁移。基线改动范围仅限用户已批准的公共审批/通知集成点。

## 0. 最新会话补充（2026-10-12，P2 启动，未执行迁移）

- 本日后续实现与用户浏览器验证：需求列表已支持描述省略展示、类型/状态筛选、期望上线时间和更新时间排序；新建/编辑弹窗已独立为 `RequirementDialog`，采用与交易弹窗一致的横向标签布局，`ThemeProvider` 统一启用 `dayjs.locale('zh-cn')`。修复了始终挂载 Dialog 导致的 `useForm` 未连接警告：仅编辑状态存在时挂载弹窗。
- 流程：草稿可提交；审批中可由申请人在需求列表或审批中心“我的申请”撤销；后端锁定公共审批实例并同步需求状态为 `4已撤销`。审批中心为该内网业务增加撤销入口是用户授权的基线前端修改。
- 需求详情：列表已提供概要详情抽屉。新增交易已完成固定明细后端读写和前端“明细”对话框（业务信息、分类/架构及非功能指标），仅申请人可在草稿/已拒绝时编辑。交易调用、交易变更明细 UI/API、审批中心内网需求详情与审批操作、审批通知/实时刷新、Excel 预览保存、版本/调用关系仍未完成。
- 验证：本轮多次运行 `npm.cmd run build`，`tsc --noEmit` 未报类型错误并进入 Vite `transforming`；执行环境在最终完成行前回收输出，不能登记为完整构建成功。`git diff --check` 通过。当前机器未发现 Maven，后端未重新编译。
- 当前 Git：`main...origin/main`，未提交；未执行 commit/push/pull/merge/rebase/reset/clean 或数据库脚本。除用户批准的公共审批/审批中心/主题本地化修改外，其余新增均在 `intranet` 目录；不得覆盖本轮改动。

- 用户确认：`oa_no` 是独立的 OA 联系单编号，不映射为系统用户；创建人仍由现有审计字段记录。需求类型和状态分别使用 `TINYINT`：`1/2/3` 与 `0/1/2/3/4`。不使用通用 `content` 字段。
- 已新增但**未执行**：`db/intranet/V12__create_intranet_requirement.sql`（需求主表及三类固定明细表）、`V13`（需求审批/开发候选部门与角色种子）、`V14`（菜单与按钮权限）。在目标库执行前请先审阅并按顺序执行 V12→V13→V14。
- 已编写：需求主表分页/新建/编辑；草稿提交公共审批、候选审批组通过/拒绝；新增/变更类型在通过后可按“内网研发部 + intranet_developer”候选组分配一名负责人。审批候选组仍是“内网需求审批部 + department_approver”。
- 需求概要后续补充：`V15` 为已存在的需求主表增加 `description VARCHAR(1000)` 与 `expected_online_date DATE`；前端 OA 文案统一为“OA编号”，对话框使用与交易新增一致的横向标签布局；创建/编辑时不再暴露交易 ID。后端已限制交易码只能在审批通过后分配、只能选状态为“评估中”的交易、一个交易只能关联一个需求；交易码和开发负责人分配互不依赖。
- 尚未实现：三类固定明细的保存与展示、Excel 导入后预览再保存、审批中心对需求的详情与操作入口、审批通知/实时刷新、版本生效和交易调用关系。因此 P2 不能标为完成。
- 公共审批基线继续有用户批准的改动：任务候选部门/角色字段及部门调动改为候选组审批；取消状态 UI 已修正为“申请已撤销”。用户确认已手动执行 V4 并完成基础验证。
- 本会话前端执行了 `npm.cmd run build`，命令输出进入 Vite transforming 阶段后工具回收，未获得成功退出的完整证据；后端 Maven 在当前环境不可用，未编译。

## 0. 最新会话（2026-10-12，公共审批纯方案 2基线）

- Git 基线：开始时 `main` = `origin/main` = `b4e55bf`，工作区干净；本轮未执行 commit、push、pull、merge、rebase、reset、clean 或数据库脚本。
- 用户确认：需求类型采用 `NEW_TRANSACTION` / `CHANGE_TRANSACTION` / `TRANSACTION_CALL`；需求状态采用 `DRAFT` / `PENDING` / `APPROVED` / `REJECTED` / `CANCELLED`；公共审批采用纯方案 2，不使用长期 `UNION ALL`；实例表不设 `version`，并发使用事务内 `SELECT ... FOR UPDATE`。
- 基线新增：`approval/core` 下公共实例/任务实体、Mapper 和 `ApprovalWorkflowService`；支持串行步骤、通过、驳回、撤销和驳回后新轮次重提。
- 部门调动已改为把审批状态和步骤写入公共表；业务详情由精简后的 `sys_department_change` 提供，审批中心只查公共表。旧步骤 Java 实体/Mapper 已删除，业务实体也已删除 `status`、`currentStep`、`version`、`finishedTime` 等重复审批字段。
- 数据库：用户已执行 `db/approval/V1`、`V2`，并确认 AP2 输出的 `missing_instance_count` / `missing_task_count` 均为 0；新增一次性 AP3，把空表 `sys_department_change_request` 重命名为 `sys_department_change`，删除空表 `sys_department_change_step` 及业务表中的四个重复审批字段。**AP3 尚未执行，本会话未直接查询数据库复核。**
- 前端：审批中心改为通用业务类型、标题、摘要和步骤列；部门调动详情与操作保持原接口。
- 验证：`mvn.cmd -o -B -DskipTests compile` BUILD SUCCESS（280 个主源码）；`ApprovalWorkflowServiceTest` 3/3 通过且未启动 Spring/数据库；`npm.cmd run build` 通过（5314 个模块，仅既存 chunk 告警）；`git diff --check` 无内容错误。
- 未完成：用户执行 AP3、部门调动提交/两级通过/驳回/撤销/角标/通知的浏览器端到端回归、非超管权限验证；需求审批人来源仍待确认，因此尚未开始 `intranet_requirement`。
- 基线修改披露：本轮按用户明确批准修改公共审批后端、基线初始化 SQL、审批中心前端，并新增公共审批迁移脚本与定向测试；没有修改 intranet P1 业务代码。

## 0.1 上一会话（2026-10-11 接手复核，仅文档修复）

- 会话性质：只读复核 + 文档修复。**未修改任何代码、未执行数据库脚本、未执行任何 Git 写操作。**
- HEAD：`2be715c`；上一轮的实现代码与 V10/V11 脚本已由用户提交进该提交，工作区干净。
- 复核结论：P1-03 实现与设计文档基本一致——V8 六张表字段、权限码、交易删除契约、引用保护、ES 固定 `id`、资源集合整体替换均已逐项核对通过。
- 本次实测：`mvn test` BUILD SUCCESS（现存 2/2 通过）；`npm run build` 通过（`tsc --noEmit` + vite 22.10s）。两次构建在默认沙箱下分别因工作区写入被拒与 esbuild 子进程 `spawn EPERM` 失败，放宽权限后通过。
- 用户于本次会话确认的事实：
  1. `a3b8d0b` 中删除暂存文件上传模块、把审计 Agent 工具由 4 个整合为 2 个、删除 5 个测试，**均由用户本人有意完成，不需要恢复**。
  2. `frontend/src/pages/error/pageUnavailable.css` 的 `max-width` 改动由用户本人修改。
  3. V7~V11 已由用户实际执行并验证，**不需要再核对**。
  4. 交易列表的“ESF 服务名/操作名”和“关联索引/HBase 表”两个查询条件不接后端，是用户为验证基线 SmartTable 前端样式有意安排，**不是缺陷**（见 D-026）。
  5. ES 字段 `data_type` 不做服务端强校验，用户认为内网自用系统上该设计过度（见 D-027）。
- 未验证：字段隐藏页的浏览器交互验证与非超管权限验证（2026-10-11 用户确认尚未验证，见 §4）。
- 已确认本期不做：P1 领域定向测试（D-030，用户决定“直接忽略”）。
- 最近推进（2026-10-11）：Excel 导入经用户决定**暂缓**（实施方式已预确认，见 D-032）；P2 前置的**审批复用方案评审**已完成，用户选定**方案 2**（公共审批表聚合，见 D-033），形态约束与实现顺序写入 `ARCHITECTURE.md` §4。**当前阻塞点：①三类需求的状态机（产品规则）②公共审批表与需求表的表名/字段/类型/约束**——两项均由用户确认后才动代码与脚本。
- 本次会话改动文件：
  - 文档：`docs/AGENT-GUIDE.md`、`docs/LOG-AUDIT-GUIDE.md`、`docs/intranet/{ARCHITECTURE,DECISIONS,HANDOFF,MIGRATION,PLAN}.md`
  - **基线新增（用户批准，方案 B）**：`frontend/src/components/EllipsisText/{EllipsisText.tsx,ellipsisText.css,index.ts}`
  - **基线修改（用户批准）**：`styles/global.css`（新增 opt-in 的 `page-fill` / `card-scroll` 布局约定，见 D-031）；`pages/account/approvals/index.tsx`、`pages/system/loginlog/index.tsx`、`pages/system/operatelog/index.tsx`、`components/ai/artifacts/common/ArtifactTable.tsx`（长文本列改用 `EllipsisText`，见 D-029）
  - intranet 修改：`pages/intranet/components/InlineEditableTable.tsx`、`components/FieldText.tsx`、`components/field-text.css`、`transaction/index.tsx`、`transaction/index.css`、`transaction/fields/index.{tsx,css}`、`dataasset/index.tsx`、`dataasset/index.css`、`dataasset/fields/index.{tsx,css}`
  - intranet 中途新增、随后被基线组件取代并删除：`pages/intranet/components/EllipsisText.tsx`、`ellipsis-text.css`（最终仓库只有一份实现）
  - `frontend/src/components/SmartTable/SmartTable.tsx` 当前显示的改动是用户本地的格式化，不是本会话产生；本会话**没有**按方案 A 修改 SmartTable 的列处理语义

## 1. Git 与阶段

- 日期：2026-10-12（Asia/Shanghai）
- 分支：`main`，跟踪 `origin/main`
- 本轮开始时 HEAD：`b4e55bf`，ahead/behind = 0/0；当前存在本轮公共审批未提交改动
- 当前阶段：P2 公共审批基线实施；AP1/AP2 已由用户执行并确认完整性计数为 0，等待 AP3 执行与部门调动回归
- 本轮未执行 commit、push、pull、merge、rebase、reset、clean 或数据库脚本

## 2. 上一轮实现（P1-03 数据资产字段设计与通用查询能力收尾）

### 数据资产字段隐藏页

- 新增 `/intranet/dataasset/fields?assetType=HBASE|ES&assetId=...`。
- HBase/ES 列表的“字段设计”跳转隐藏页；返回时通过 URL `tab` 恢复原 HBase/ES Tab。
- 字段页采用与交易字段一致的行内新增、编辑、保存和取消。
- HBase 字段列：列族、字段名、数据类型、顺序、说明。
- ES 字段列：字段名、数据类型、可搜索、可排序、顺序、说明。
- ES 固定 `id` 字段编辑时不能改名，也不显示删除操作。
- 字段名支持前端快速搜索；编辑期间禁用搜索和新增；不分页。
- 说明使用单行输入 + Popover TextArea，展示时省略并悬停查看全文。

### 废弃代码清理

- 已删除数据资产页字段 Drawer、字段 Table、字段加载/编辑状态和相关 API 调用。
- 已删除 `HbaseFieldDialog`、`EsFieldDialog` 以及对应依赖和导入。
- 交易字段页当前本来就没有旧字段 Drawer/Modal；本轮确认后未误删交易详情 Drawer 或交易新增/编辑 Modal。

### intranet 内部共享

- 新增 `frontend/src/pages/intranet/components/InlineEditableTable.tsx`。
- 新增 `FieldText.tsx` / `field-text.css`。
- 交易字段、HBase 字段、ES 字段共同使用上述编辑骨架和长文本交互。
- 组件没有提升为基线业务组件；树形、字段列、校验、权限和 API 仍由页面负责。

### 基线 SmartTable（用户已批准）

- 修改 `frontend/src/components/SmartTable/SmartTable.tsx` 并新增组件专属 CSS。
- `searchConfig` 新增字段标签、宽度、自定义 render、高级条件和展开/收起。
- 新增 `searchActions`，控制查询/重置文字、重置显示、回车提交和默认展开。
- 查询仍使用 `search` / `submittedSearch` 双状态，只有 submit 后才请求。
- 既有 `searchRender` 页面逻辑不变。

### 交易列表搜索样式与资产表单提示

- 交易列表改用 SmartTable `searchConfig`，展示“交易名称/编码”“ESF 服务名/操作名”“关联索引/HBase 表”三个条件以及查询、重置按钮。
- 按用户要求，本轮只有交易名称/编码继续传给既有后端 `keyword`；ESF 和关联资源值不加入请求参数，也不会实际过滤数据。
- 2026-10-11 复核更正：这两个条件**刻意不接后端**，用途是验证基线 SmartTable `searchConfig` 的前端样式；属于 D-026 确认的设计，不是待修复缺陷，后续会话不要补齐。
- HBase 表、ES 索引新增/编辑 Modal 的表名、索引名、命名模式、数据深度、RowKey、关联 HBase 表和说明等字段已补齐 placeholder。

### 数据库脚本

- V10：虚构数据资产演示数据，上一轮生成，本轮未执行。
- V11：新增数据资产字段设计隐藏菜单及超管授权，待执行。
- 用户已在页面看到演示数据和资产页面，但本会话没有查询数据库，故 V8~V10 的具体执行状态记录为“待目标环境复核”。

## 3. 验证

### 上一轮验证结果

| 项目 | 结果 |
| --- | --- |
| `npm run build` | 通过；最新搜索样式和 placeholder 修改后再次构建成功，5311 个模块完成转换，仅有既存 chunk 体积告警 |
| 旧字段 Drawer/Modal 搜索 | 数据资产目录无 `HbaseFieldDialog`、`EsFieldDialog`、`fieldOwner`、字段 Drawer 残留 |
| 后端测试 | 本轮未重复执行；只新增 SQL，无 Java 改动。上一轮当前仓库现存测试 2/2 通过，但 P1 定向测试缺失 |
| 数据库脚本 | 本轮未执行；V11 明确待执行 |
| 浏览器人工联调 | 未执行 |

### 2026-10-11 接手复核复现结果

| 项目 | 结果 |
| --- | --- |
| `mvn.cmd -o -B test -f backend\pom.xml` | BUILD SUCCESS；Tests run: 2, Failures: 0, Errors: 0，耗时 36.8s。首次在 workspace-write 沙箱下因 `backend\target\classes\application-dev.yml` 写入被拒而失败，放宽权限后通过 |
| `npm.cmd run build`（frontend） | 通过；`tsc --noEmit` 无错误，vite `✓ built in 22.10s`，仅既存 chunk >500kB 告警。首次在沙箱下因 esbuild 子进程 `spawn EPERM` 失败，放宽权限后通过 |
| 表结构 vs `ARCHITECTURE.md` §5 | V8 六张表逐字段一致 |
| `@PreAuthorize` vs V2/V6/V9/V11 种子 | 一致，另 `/options` 额外放行 `intranet:transaction:list`、`intranet:transaction:update` |
| 旧字段组件残留检索 | `HbaseFieldDialog` / `EsFieldDialog` / `fieldOwner` 零命中 |
| 构建后 `git status` | 干净；`backend/target`、`frontend/dist` 已被 `.gitignore` 忽略 |
| 数据库脚本 | 本会话未查库；执行状态一律以用户确认为准（见 §0、§4） |

## 4. 待用户执行与验收

- **V7~V11：已由用户实际执行并验证**（2026-10-11 用户确认；本会话未查库复核），原“执行 V11”待办关闭。
- 以下交互与权限验证**尚未执行**（2026-10-11 用户确认隐藏字段页还未验证）：
  1. 分别从 HBase、ES 列表进入字段设计，验证新增、编辑、删除、搜索、说明展开和返回 Tab。
  2. 验证 ES `id` 字段不可改名且不能删除。
  3. 用非超管角色验证隐藏菜单授权和 `intranet:dataasset:*` 接口权限。
- 已确认本期不做：P1 领域定向测试（D-030）。删除保护、固定 `id`、资源整体替换和交易删除顺序因此没有自动化回归保护。
- **布局改动（D-031）已由用户于 2026-10-11 在浏览器目视确认通过**：卡片按内容高度、仅卡片内滚动、表头固定、固定列不遮挡表头、新增后自动滚到新行，5 项全部无问题。构建仍验证不出布局，后续再动这套约定时必须重新目视。
- **SmartTable 固定表头与日志页 `page-fill`（D-038，含同轮修正）需目视确认**：
  1. 操作日志 / 登录日志：卡片高度按内容、行数超出时**只有数据行滚动**；表头固定不动、**分页固定在卡片底部不随数据滚动**、搜索区与工具栏不滚走、标题固定；
  2. 数据只有几行时**不应出现任何滚动条**；加了 `size='small'` 后，10 行在常见视口下也应放得下。若仍出现小行程滚动条，请记录当时的视口高度与页面（用于判断是真实溢出还是结构问题）；
  3. **字段设计页必须重新目视**（HBase/ES 字段设计、交易字段设计）：滚动机制已由"整块滚动 + sticky 表头"改成"表体滚动 + 布局钉表头"，确认效果与之前一致（表头固定、只有表格滚动、新增后自动滚到新行仍正常）；
  4. 因为 `sticky` 对所有 SmartTable 页面生效，请顺带回归：用户、角色、部门、菜单、审批中心、交易列表、数据资产列表的**表头显示与列宽对齐**，以及页面滚动时表头是否吸在内容区顶部；
  5. 需要横向滚动的页面，确认横向滚动条出现在**表体底部（分页上方）**且位置合理。

## 5. 风险与边界

- V7~V11 已执行。V2/V6/V9/V11 沿用既有菜单脚本的软删除幂等边界（同路径软删除记录会导致静默跳过）仍然存在；本次执行未观察到跳过，该边界继续保留在 `MIGRATION.md`，不作为已解决问题。
- `pages/**/*.tsx` 动态扫描仍会发现组件型 TSX，用户已接受通过菜单配置规范规避；本轮没有修改路由扫描基线。
- SmartTable 是上一轮唯一修改的基线公共代码（`SmartTable.tsx` + 新增 `SmartTable.css`），按 D-025 记录为用户批准；前端全量构建通过，仍建议人工回归用户、角色、部门、日志、交易和资产列表。
- 未申报的基线改动已澄清：`frontend/src/pages/error/pageUnavailable.css` 的 `max-width: 720px → 100%` 由用户本人修改，不属于内网任务范围。
- 当前 HEAD 中没有 P1 领域单元测试，构建通过不能替代业务行为测试。用户已决定本期不补（D-030），因此删除保护、固定 `id`、资源整体替换与交易删除顺序没有回归保护，风险由用户接受；文档不得据此宣称 P1 领域行为已验证。
- `a3b8d0b` 中 intranet 之外的改动（删除 `org/lbl/file` 暂存文件上传模块、审计 Agent 工具由 4 个整合为 2 个、删除 5 个测试）**经用户确认为本人有意完成，不需要恢复**。其中的 P1-02 领域测试 `TransactionMessageFieldServiceTest` 缺失会削弱 P1 的回归能力；用户已决定本期不补（D-030）。
- **`mvn test` 不是只读操作**：`JasyptEncryptorTest` 是 `@SpringBootTest`，会启动完整 Spring 上下文并执行 `SystemPermissionInitializer`（`ApplicationRunner`），因此会连接 `application-local.yml` 配置的远程 MySQL，并可能为超管补写 `sys_role_menu` 授权行。它只涉及基线菜单与按钮权限，不创建任何 intranet 表、不执行 `db/intranet` 脚本。详见 `MIGRATION.md` 第 4 节。
- 以下三项经用户确认**明确不做**，后续会话不要当作缺口或缺陷：交易列表 ESF/关联资源条件不接后端（D-026）、ES 字段 `data_type` 不做服务端强校验（D-027）、P1 领域定向测试（D-030）。
- **基线公共代码修改披露（AGENTS.md §2 / §6.7）**：本会话新增基线组件 `frontend/src/components/EllipsisText/`，并迁移 `pages/account/approvals/index.tsx`、`pages/system/loginlog/index.tsx`、`pages/system/operatelog/index.tsx`、`components/ai/artifacts/common/ArtifactTable.tsx` 的长文本列展示；用户 2026-10-11 明确选择方案 B 并批准（见 D-029）。改动只涉及各列的 `ellipsis` 配置与 render 包装，**未修改 `SmartTable` 的列处理或任何表格语义**。回归：`tsc --noEmit` 与 `npm run build` 均通过；仍建议人工回归审批列表、登录日志、操作日志与 AI 报告表格四处界面。
- **基线公共代码修改披露（第二项）**：本会话在基线 `frontend/src/styles/global.css` 新增 opt-in 布局约定 `page-fill` / `card-scroll`（用户 2026-10-11 批准，见 D-031），让"标题固定 + 只有卡片内表格滚动"不再依赖 `calc(100vh - N)` 魔法数字。该约定**默认不生效**，只有显式加类的页面受影响，最初仅 `intranet/{transaction,dataasset}/fields` 使用。配套改了内网共享组件 `pages/intranet/components/InlineEditableTable.tsx`：它自带 `.card-scroll` 容器、统一给 Table 传 `sticky`（表头固定，因为给 `th` 写 `position: sticky` 会被 antd 的 `.ant-table-content` 困住而无效），并在进入编辑时把目标行滚进视野。**布局效果构建验证不出**，必须按 §4 的清单目视确认；已知降级为极矮视口下卡片溢出。
- **基线公共代码修改披露（第三项）**：本会话修改基线 `frontend/src/components/SmartTable/SmartTable.tsx`——固定渲染 `.card-scroll` 滚动容器并默认给 Table 传 `sticky`（用户 2026-10-11 批准，理由是"整个项目的表格都希望能遵守这个效果"，见 D-038）；同时给 `pages/system/loginlog/index.tsx` 根节点加 `page-fill`（`operatelog` 由用户自行添加）。影响面是**所有使用 SmartTable 的页面都会获得 antd 固定表头**（antd 会拆表头并可能渲染吸附式横向滚动条）；未加 `page-fill` 的页面不会启用卡片内滚动。**布局效果构建验证不出**，需按 §4 目视确认。

## 6. 上一轮文件

新增：

- `frontend/src/components/SmartTable/SmartTable.css`
- `frontend/src/pages/intranet/components/InlineEditableTable.tsx`
- `frontend/src/pages/intranet/components/FieldText.tsx`
- `frontend/src/pages/intranet/components/field-text.css`
- `frontend/src/pages/intranet/dataasset/fields/index.tsx`
- `frontend/src/pages/intranet/dataasset/fields/index.css`
- `backend/src/main/resources/db/intranet/V11__seed_intranet_data_asset_field_design_menu.sql`

修改：

- `frontend/src/components/SmartTable/SmartTable.tsx`
- `frontend/src/components/SmartTable/index.ts`
- `frontend/src/pages/intranet/dataasset/index.tsx`
- `frontend/src/pages/intranet/dataasset/AssetDialogs.tsx`
- `frontend/src/pages/intranet/transaction/fields/index.tsx`
- `frontend/src/pages/intranet/transaction/fields/index.css`
- `docs/intranet/ARCHITECTURE.md`
- `docs/intranet/DECISIONS.md`
- `docs/intranet/PLAN.md`
- `docs/intranet/MIGRATION.md`
- `docs/intranet/HANDOFF.md`

> 更正（2026-10-11 接手复核）：上一轮的 V10 与 V11 已随 `2be715c` 提交，不再是“未提交文件”。当前工作区干净，无未提交改动。
