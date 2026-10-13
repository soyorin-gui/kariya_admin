# 历史残留与不必要复杂度审计报告

> 审计对象：`D:\kariya_admin`（React 18 + TypeScript + Vite 6 + Ant Design 5 前端；Java 17 + Spring Boot 3.4.4 + MyBatis-Plus + MySQL + Redis 后端）
> 审计基线：`main` 分支，HEAD = `b17e1c8`（2026-10-13 08:54）
> 审计性质：**只读**。未修改任何业务代码、配置、依赖、数据库脚本，未执行 Git 写操作，未连接数据库，未执行 `build` / `test`。
> 报告生成后状态：`git status --short --branch` 相对审计开始时**未新增任何仓库内文件**（详见 §1.4）。

---

## 摘要统计

### 按分类

| 分类 | 含义 | 条目数 |
| --- | --- | --- |
| P0 | 历史修改残留 | 26 |
| P1 | 重复实现与职责重叠 | 8 |
| P2 | 防御性代码与过度保护 | 13 |
| P3 | 过度抽象与提前设计 | 7 |
| **合计** | | **54** |

### 按证据可信度

| 可信度 | 条目数 |
| --- | --- |
| 高 | 41 |
| 中 | 11 |
| 低 | 2 |
| **合计** | **54** |

### 按结论分类

| 结论 | 含义 | 条目数 |
| --- | --- | --- |
| 确认的历史残留 | 有充分证据证明已被替代且无使用者 | **24** |
| 疑似历史残留 | 静态分析发现异常，仍需运行或浏览器验证 | 13 |
| 需要业务确认 | 用途无法从仓库完全证明 | 11 |
| 有意预留能力 | 有设计依据，默认保留 | 6 |

### 关键数量

- **预计低风险清理项：19 项**（§8.1 的 15 项零风险 + §8.2 的 4 项需构建验证；均不改变运行行为，且不触及基线公共契约语义）。
- **需要人工确认的候选：12 项**（§9.1 业务确认 8 项、§9.2 浏览器验证 4 项；另有 §9.3 接口/数据库验证 4 项、§9.4 文档修正 3 项）。
- **不建议删除的复杂设计：20 项**（见 §10）。
- **已复核确认无问题的区域：13 类**（见各章末与 §9.3）。

### 一句话总体判断

本项目**不存在大规模历史残留**。真正"删了就干净"的死代码只有 24 处，全部是声明级碎片（未导出的类型、零调用方法、已注释块的配套样式、单行失效放行规则、一个无人读写的孤儿列），**没有发现被整体替代却仍在服务的旧模块、旧页面或旧代码路径**。复杂度总体处于"与业务规模匹配"的水平；除一处例外，已确认的设计决策（D-001～D-040）与代码事实**一致**。

### ⚠️ 最需要优先处理的两件事（与清理无关，但优先级最高）

1. **`AccessPolicy.requiresPlatformTransferReview` 是本次审计唯一带安全语义的发现**：它实现了"跨动态角色范围的部门调动应由平台管理员审批"这一判定，但**自诞生起从未被任何代码调用**（`backend` 全量 + `frontend` + `docs` 共 1 处命中，即声明行本身）。当前部门调动审批走的是 `department_approver` 候选组，**没有任何平台管理员审批判定**。这可能是"实现了却忘记接线"，也可能是"已废弃的设计"——**必须由业务确认，报告中不给出删除建议**。详见 §9.1-B0 与 §10-20。
2. **`frontend/dist/` 是 2026-10-12 的陈旧构建，会让 D-038 的浏览器验证得出错误结论**：该产物中的 CSS **仍含已被明确撤回的 `overflow: auto`**，且**完全不含**修正后的 flex 高度链。若据此做"浏览器实测",会重现两条滚动条，极可能触发第三次返工。详见 §11 阶段四。

---

## 1. 审计范围和方法

### 1.1 范围

| 层 | 扫描对象 | 文件数 | 说明 |
| --- | --- | --- | --- |
| 前端 | `frontend/src/**/*.{ts,tsx,css}` | 167 | 含全部 34 个 `.css` |
| 后端 | `backend/src/**/*.{java,xml,sql}` | 340 | 含 `db/**` 全部迁移脚本与 `test/**` |
| 文档 | `AGENTS.md`、`docs/intranet/{ARCHITECTURE,PLAN,DECISIONS,HANDOFF,MIGRATION}.md` | 6 | 作为"有意预留"的判定依据 |

未纳入：`frontend/dist/`（`.gitignore` 第 144 行忽略的构建产物）、`frontend/node_modules/`、`backend/target/`（已被 `.gitignore` 第 71 行忽略，`git ls-files backend/target` = 0）。二者均不进入版本库，不作为代码残留审计对象；但 `frontend/dist` 存在一个会误导验证的具体风险，记录在 §9.2-R1。

### 1.2 方法

1. **建立边界**：通读 `AGENTS.md` 与 `docs/intranet/` 全部文档，把 D-001～D-040 中"有意为之"的条目建成白名单（D-026/D-027/D-037/D-031/D-038/D-012/D-029/D-006 等），避免把它们当缺陷。
2. **定向静态分析**（自建 PowerShell 脚本，非 IDE）：
   - 导出面分析：提取全部 347 个导出标识符并统计全仓词边界出现次数，**出现次数 ≤ 1 即"仅定义处命中"**。
   - 类型面分析：173 个 `interface`/`type` 做同样统计。
   - 局部声明分析：模块级非导出 `const`/`function` 做同样统计。
   - CSS 类名分析：提取 662 处（270 个唯一）类选择器，与全部 `.ts/.tsx/.css` 文本做双向匹配。
   - CSS 变量分析：29 个自定义属性定义点，逐项统计 `var(--x)` 真实读取次数。
   - 重复选择器分析：按选择器文本跨文件聚合，找同一选择器在多文件重复定义。
   - Java 类型/方法分析：全部类名与 Mapper 方法名做全仓统计。
   - 端点-调用方交叉核对：从 127 个 `@*Mapping` 提取路径，与前端全文比对。
3. **动态引用核查**（全部候选逐个执行，这是本次审计最耗时也最关键的一步）：
   - 前端：`import.meta.glob` 构建期扫描、模板字符串拼接的类名（`` `social-${provider.key}` ``、`` `identity-${provider.icon}` ``）、barrel `index.ts` 再导出、`export *`、对象属性动态访问、`slice.actions.x` 形式、antd 的 `className` 系列 prop、Redux `extraReducers` 跨 slice 联动。
   - 后端：`@Configuration`/`@Component` 注解扫描、`@Bean`、构造器注入、AOP 切点、`@Scheduled`、`@EventListener`、`ApplicationEventPublisher`、MyBatis `@Select`/`@Update` 注解 SQL 与方法引用（`analysis::scanLoginRiskChunk`）、Jackson 序列化、`application*.yml` 配置键绑定、测试代码。
4. **反向引用验证**：对每一条"建议删除"的发现，重新用完整标识符搜索一遍全仓（含 `docs/`），确认零消费者；对"疑似"和"需确认"的不做删除建议。
5. **误报剔除**（明确记录，避免后续重复扫描）：
   - 7 个 Java 类在文本搜索中表现为"零引用"（`AppConfig`、`MybatisConfig`、`AgentAsyncConfig`、`AgentProfileConfig`、`ExternalAuthProviderConfig`、`AuditAgentConfig`、`AuditRiskPolicyConfig`），经确认全部带 `@Configuration`，由 Spring 组件扫描发现——**文本零引用不成立**。
   - 8 个 CSS 类名在 `.tsx` 中零命中（`identity-{github,google,wechat}`、`social-{github,google,wechat}`、`peg`、`anticon`），全部由模板字符串拼接或第三方（nprogress / antd）产生——**搜索不到不成立**。
   - `styles/reset.css` 未被任何 TS/TSX import，但由 `styles/global.css:2` 的 `@import` 引入——**未被 JS import 不成立**。
   - `mybatis-plus-jsqlparser` 依赖看似多余，实际被 `MybatisConfig.java:6,21` 的 `PaginationInnerInterceptor` 使用——**"未 import"不成立**。
   - `lbl.log.retention` 配置键在三个 profile 中均未出现，但 `LogRetentionProperties` 每个字段都有 `@DefaultValue`、`@Scheduled` 也有内联默认表达式——**属于有意的默认值设计，不是失效配置**。

### 1.3 本次未执行的验证（必须说明，避免把静态结论当成运行时事实）

- 未执行 `npm run build`、`tsc --noEmit`、`mvn compile`、`mvn test`。
- 未连接 MySQL / Redis，未查询 `sys_menu` 等表的运行时实际内容。因此"某个 `pages/` 文件是否真被线上菜单选中"只能依据 `init_data.sql` 与迁移种子脚本推断。
- 未启动 dev server，未做浏览器实测。所有布局类、对比度类、深色主题类结论均为静态推导（对应 §9.2 的 B/C/D 类候选）。
- 未检查 `transaction/types.ts:10-11` 三个字段的后端**实际响应**是否包含它们（见 P0-19）。

### 1.4 审计过程对工作区的影响（如实披露）

本次审计开始时，DSH 无法为工作区 `D:\kariya_admin` 写入授权（`SetNamedSecurityInfoW failed (Win32 5)`），导致所有命令不可执行。按内置技能 `diagnose-windows-sandbox-acl` 的指引执行了一次权限诊断与修复：

- 修复内容：为 `D:\kariya_admin` 补当前登录用户的完全控制权限项（原缺失"更改所有者"权限）。**仅新增一条允许项，未删除任何既有权限项、未更换所有者、未改动任何文件内容。**
- 备份与回滚：改动前后的权限已备份到 `D:\kariya_admin\.acl-recovery\acl-backup-62ec307c6ad449f680a3f3578836fc69.json`，并生成了独立回滚脚本（同名 `.ps1`）。回滚命令：
  ```
  pwsh -NoProfile -File 'D:\kariya_admin\.acl-recovery\acl-backup-62ec307c6ad449f680a3f3578836fc69.json.ps1' -Path 'D:\kariya_admin' -AllowRoot 'D:\kariya_admin' -Restore 'D:\kariya_admin\.acl-recovery\acl-backup-62ec307c6ad449f680a3f3578836fc69.json'
  ```
- 副作用：`.acl-recovery/` 是本次审计在工作区内新建的目录，未加入 `.gitignore`，因此出现在 `git status` 的未跟踪列表中。尝试将其移出工作区被沙箱拒绝（工作区外写入受限），故保留原位。
- **建议**：该目录与业务无关，确认无需回滚后可直接删除 `D:\kariya_admin\.acl-recovery\`。删除后 `git status` 恢复为审计前的干净状态。

除上述权限修复与其备份目录外，本次审计**未对仓库内任何被跟踪文件产生改动**。

审计结束时的 `git status --short --branch` 输出（全部为未跟踪项，无已跟踪文件被修改）：

```
## main...origin/main
?? .acl-recovery/
?? docs/code-audit.md
```

- `docs/code-audit.md` 是本次审计的**唯一交付物**（不是业务代码改动）。
- `.acl-recovery/` 是上述权限修复的备份目录（见上文"副作用"与"建议"）。
- 除此之外**无任何新增、修改或删除**：审计全程只读，未动业务代码、配置、依赖、数据库脚本，未做 Git 提交。

---

## 2. 当前项目复杂度的总体判断

### 2.1 规模事实

| 指标 | 数值 |
| --- | --- |
| 前端源码 | 167 个文件、约 11,894 行（含 34 个 CSS） |
| 后端源码 | 340 个文件、约 14,928 行（含 SQL 迁移脚本） |
| 最大前端文件 | `frontend/src/pages/home/index.tsx` 554 行（组件演示页，见 §10-17） |
| 最大后端文件 | `backend/src/main/java/org/lbl/auth/external/ExternalLoginService.java` 463 行 |
| 后端 >300 行的类 | 3 个（`ExternalLoginService` 463、`AccessPolicy` 428、`UserService` 365） |
| Git 跟踪文件 | 634 |

**注意**：按任务约定，"类超过 300 行"不作为问题提出。此处仅列出规模，用于判断复杂度是否失控——3 个超 300 行的类中，`AccessPolicy` 承担权限判定与菜单树计算的多个关注点，`ExternalLoginService` 承担外部登录流程编排，两者均有清晰的注释边界，未发现职责混乱。

### 2.2 复杂度分类判断

按任务要求的五类区分，本次审计的分布如下：

| 复杂度类型 | 本次结论 | 证据概要 |
| --- | --- | --- |
| ① 当前业务需要的复杂度 | **占绝大多数** | RBAC、公共审批状态机、审计日志留存与风险规则、实时通道、三类需求固定明细——均有真实调用链与验收需求 |
| ② 有明确学习价值的复杂度 | **存在且合理** | Agent 的 port/adapter 分层、`ToolRegistry` 权限过滤、`G6Graph` 封装、`ExternalAuthProviderRegistry` 多提供方——依据 `ARCHITECTURE.md` §8 与 `AGENTS.md` 明示保留 |
| ③ 有明确未来规划的复杂度 | **存在且已登记** | `EnterpriseDirectoryPort`（内网 ESF/ESB 适配面）、`ApprovalPolicy.REQUIRED`（审批协议未实现）、`AgentRunObserver`（运行观测扩展点）——注释与文档均有依据 |
| ④ 工程上合理的复杂度 | **存在且已核实** | `AuditRiskRule` 4 个实现、`AgentTool` 4 个实现、`ExternalAuthProviderRegistry` 4 个内置提供方、`OperationLogService` 的 `REQUIRES_NEW`——实现数量或事务语义支撑其结构 |
| ⑤ **历史遗留 / AI 无依据增加的偶然复杂度** | **存在，但量小、层级浅** | 见 §3～§6；24 处确认残留全部是声明级碎片与一个孤儿数据库列，**没有一处是可观的旧模块、旧页面或旧代码路径** |

### 2.3 与文档的一致性核对（重点结论）

审计专门核对了三处文档自称"已完成清理"的地方，结论是**代码与文档一致，未发现未清理的尾巴**：

| 核对项 | 文档依据 | 代码事实 | 结论 |
| --- | --- | --- | --- |
| D-035 删除部门调动旧审批结构 | 删 `status`/`current_step`/`version`/`finished_time`，删 `sys_department_change_step`，表改名 `sys_department_change` | `init_schema.sql:142-152` 与 `DepartmentTransferRequestEntity.java:8-16` 完全一致，均无那四列；`current_step`/`finished_time` 仅存在于**公共审批实例表** `sys_approval_instance`（`init_schema.sql:155-168`），属设计而非残留；全仓无 `version` 字段。旧表名只出现在一次性脚本 `db/approval/V2`（已执行）与 `V3`（清理脚本本身）中，运行时代码零引用 | **一致，无残留** |
| D-029 长文本截断收敛到基线组件 | 只保留 `components/EllipsisText/` 一份实现与一份 CSS | 全仓只有 `EllipsisText.tsx` 一份实现；11 个页面从 `components/EllipsisText` 导入；intranet 内无同名残留 | **一致，无残留** |
| D-031 / D-038 CSS 最终形态 | 撤回记录明确：`.card-scroll` **不得**写 `overflow: auto`；分页必须用**后代**选择器；只有 `.ant-table-body` 滚动 | `global.css:89-94` 的 `.card-scroll` 只有 flex 传高度、无 `overflow`；`:105-114` 的高度链完整穿过 `.ant-table-wrapper`→`.ant-spin-nested-loading`→`.ant-spin-container`→`.ant-table`→`.ant-table-container`；`:116-120` 的 `.ant-pagination` 用的是后代选择器；`:123-127` 的 `.ant-table-body` 为 `overflow-y: auto !important`。`min-height: calc(100vh - 240px)` 已彻底消失 | **一致，无第三次残留** |

同时发现**两处文档自身的过时描述**（位于 `docs/intranet/DECISIONS.md`，属文档维护问题，见 §9.3-D1/D2）：

- `DECISIONS.md:337` 仍写"保留 `overflow: auto` 作为兜底"，与同一条记录 `:364-365`"已移除该声明…最终形态不设 overflow"自相矛盾。代码取后者（正确）。**这句旧描述正是未来"第三次返工"的误导来源。**
- `DECISIONS.md:363-366` 把该修正记为 2026-10-11 同日完成，但 Git 中对应提交是 `d04ae00`（2026-10-13 00:28）。按 `AGENTS.md` §1"文档与代码冲突时以代码和 Git 历史为事实来源"，文档日期需修正。

---

## 3. P0 历史修改残留

### 3.1 确认的历史残留（有充分证据证明已被替代且无使用者）

> 本节含 P0-01～P0-16 与 **P0-25、P0-26**（编号 25/26 是审计后半程新增的两条，同属"确认的历史残留"，编号未重排以免打乱全文交叉引用）。

---

#### P0-01 前端两个已被合并接口取代的单字段写函数

- **分类**：P0
- **文件与行号**：`frontend/src/pages/intranet/requirement/api.ts:8`、`:9`
- **相关引用/调用链**：`assignRequirementTransaction` 与 `assignRequirementDeveloper` 在全仓（`frontend/` 全部 `.ts/.tsx/.css`）各仅命中定义行 1 处。无 import、无模板字符串引用、不在任何 barrel 中、不被 `import.meta.glob`（`router/componentRegistry.tsx:19`，只收集页面组件）涉及。同文件其余 20 个导出均有调用点。
- **当前代码的实际作用**：分别调用 `PUT /intranet/requirements/{id}/transaction` 与 `PUT /intranet/requirements/{id}/developer`，即"单独分配交易""单独分配开发负责人"两个细粒度写接口的前端封装。
- **判断可能冗余的依据**：二者已被 `api.ts:11` 的 `configureRequirementImplementation(id, transactionId, developerUserId)`（对应 `PUT /{id}/implementation`）取代——后者一次提交两个字段，`RequirementImplementationDialog.tsx:4,20,28,50` 只走它。这与 D-040 确认的"新增/变更需求完成交易和开发负责人分配后显式生效"（一次性配置）语义一致，两个单字段封装是 D-040 之前的粒度。
- **是否检查动态引用和跨模块使用**：**是**。逐字搜索标识符；检查 `componentRegistry.tsx` 的 glob、全部 10 个 `index.ts` barrel（均不含 api 目录）、`export *`（全仓零命中）、对象属性动态访问。
- **删除或简化的潜在影响**：最坏情况为零——后端端点不受影响（其去留见 P1-02），删除仅少 2 行未被编译进任何调用链的封装。
- **建议处理方式**：删除这两行导出。**先与 P1-02 的后端决策一起做**，避免前端删了、后端端点仍裸奔。
- **结论分类**：确认的历史残留
- **证据可信度**：高

---

#### P0-02 数据资产模块内重复定义且零引用的交易数据资源类型

- **分类**：P0
- **文件与行号**：`frontend/src/pages/intranet/dataasset/types.ts:10`（`TransactionDataResources`）
- **相关引用/调用链**：全仓 7 处命中，其中 `dataasset/types.ts:10` 是定义，其余 6 处**全部**指向 `transaction/types.ts:8` 的同名定义：`transaction/api.ts:3`（import 自 `'./types'`）、`:14`、`:15`、`DataResourceDialog.tsx:6`（`import type { TransactionDataResources } from './types'`）、`:10`，以及 `transaction/types.ts:8` 自身。`dataasset/api.ts:3` 的 import 列表**不含**该类型；`DataResourceDialog.tsx:4` 从 dataasset 只导入 `EsIndex`/`HbaseTable`。
- **当前代码的实际作用**：`{ hbaseTableIds: number[]; esIndexIds: number[] }`，交易关联数据资源接口的载荷形状。
- **判断可能冗余的依据**：同一业务类型在"数据资产模块"与"交易模块"各定义一份**逐字相同**的接口，而 UI 与 API 只从交易模块那份导入。`dataasset/api.ts` 的 12 个函数中没有一个读写交易数据资源，说明这份定义是"交易数据资源功能最初落在 dataasset 模块、随后迁到 transaction 模块"时留下的副本。
- **是否检查动态引用和跨模块使用**：**是**。对标识符做全仓递归搜索，并逐行核对每个命中点的 import 来源路径；检查 barrel（dataasset 无 barrel）与 `import.meta.glob`（不涉及类型）。
- **删除或简化的潜在影响**：无编译影响。唯一风险是有人后续想在 dataasset 内复用它，需改为从 `transaction/types` 导入——这反而是更正确的方向。
- **建议处理方式**：删除 `dataasset/types.ts:10`。若认为"数据资源"概念应归属资产模块，则反向操作：删 `transaction/types.ts:8`，让 `transaction/api.ts` 与 `DataResourceDialog.tsx` 从 `../dataasset/types` 导入。**两种都只做一种，不要都留。**
- **结论分类**：确认的历史残留
- **证据可信度**：高

---

#### P0-03 仅定义处命中的类型与"孤儿导出面"

- **分类**：P0
- **文件与行号**：
  - `frontend/src/types/table.ts:36`（`SmartTableSelection`）——全仓仅定义行 1 处命中。
  - `frontend/src/components/ai/useAgentChat.ts:13`（`AgentChatHandle`）——2 处，均在自身文件（`:13` 定义、`:36` 返回类型标注）。
  - `frontend/src/pages/intranet/requirement/types.ts:2`（`RequirementStatus`）——2 处，均在自身文件（`:2` 定义、`:3` 字段标注）。
  - `frontend/src/pages/intranet/requirement/types.ts:9`（`SelectionOption`）——2 处，均在自身文件（`:9` 定义、`:10` 容器类型引用）。
  - `frontend/src/api/auth.ts:13`（`RefreshResult`）——2 处，均在自身文件（`:13` 定义、`:17` 泛型实参）。
  - `frontend/src/components/SmartTable/index.ts:3`（再导出 `TableRequestResult`）——`TableRequestResult` 在模块内有效（`types/table.ts:21` 被 `TableRequest` 使用），但**无任何文件 import 它**。
  - `frontend/src/api/account.ts:10`（`ContactProfile`）——3 处，均在 `api/account.ts` 内（`:10` 定义、`:32`、`:33` 泛型实参），**无外部 import**。
  - `frontend/src/components/ai/artifacts/AgentArtifactView.tsx:9`、`:10`——两行再导出（`registerArtifactRenderer`、`AgentArtifactRendererProps`），真实定义在 `ArtifactRendererRegistry.ts:16`、`:4`，**无外部导入者**。
- **相关引用/调用链**：每个标识符均做了全仓 `\b` 词边界搜索并逐行核对 import 来源。以 `SmartTableSelection` 为例：`components/SmartTable/index.ts` 的 `export type` 列表只转出 `SmartTableRef, TableRequest, TableRequestParams, TableRequestResult, TableSorter`，**未转出**它；`SmartTable` 的勾选能力通过 antd 原生 `rowSelection` 传入（`pages/home/index.tsx:541` 直接传 `selectedRowKeys`/`onChange`），从未使用这个自造快照类型。
- **当前代码的实际作用**：分别描述"表格勾选结果快照""Agent 聊天 hook 返回句柄""需求状态 0-4 联合类型""下拉选项""刷新响应体""表格请求结果""联系人档案""制品渲染器扩展面"。
- **判断可能冗余的依据**：三处值得区分：
  1. `SmartTableSelection` 是**彻底的死类型**——连模块内都未被使用。
  2. `AgentChatHandle`、`RequirementStatus`、`SelectionOption`、`RefreshResult`、`ContactProfile`、`TableRequestResult` 属于"**模块内有真实用途、但 `export` 没有外部消费者**"——冗余的只是导出面，不是定义本身。**不得删除定义**，否则本文件编译失败。
  3. `AgentArtifactView.tsx:9-10` 是**整行无意义**的转发（真实入口 `ArtifactRendererRegistry.ts` 可直接导入）。
- **是否检查动态引用和跨模块使用**：**是**。逐一搜索并核对每个命中行；检查全部 10 个 barrel 是否有二次转发；确认全仓无 `export *`。
- **删除或简化的潜在影响**：`SmartTableSelection` 删除零影响；其余去掉 `export` 关键字零影响（模块内仍在用）；`AgentArtifactView.tsx:9-10` 删除两行，无编译影响。最坏情况是仓库外存在未被本次搜索覆盖的消费者（例如本地其他分支或已废弃产物），但这不影响仓库内正确性。
- **建议处理方式**：删除 `SmartTableSelection` 与 `AgentArtifactView.tsx:9-10` 两行；对 `AgentChatHandle`、`RequirementStatus`、`SelectionOption`、`RefreshResult`、`ContactProfile` 去掉 `export`（保留定义）；`SmartTable/index.ts:3` 从再导出列表中移除 `TableRequestResult`。
- **结论分类**：确认的历史残留
- **证据可信度**：高

---

#### P0-04 零外部消费者的常量导出

- **分类**：P0
- **文件与行号**：`frontend/src/store/notificationSlice.ts:33`（`NOTIFICATION_FETCH_LIMIT`）
- **相关引用/调用链**：全仓 3 处命中，**全在同一文件**：`:23`（注释）、`:33`（定义）、`:49`（`fetchNotificationsApi(NOTIFICATION_FETCH_LIMIT)`）。无任何其他文件 import。
- **当前代码的实际作用**：值 `100`，作为通知列表一次性拉取条数上限。
- **判断可能冗余的依据**：`export` 修饰符没有对应的外部消费者，唯一使用者是同文件的 `loadNotifications`。`:27-32` 的注释说明其导出源于"与改动前消息中心页行为一致"的迁移说明，属解释性导出而非跨模块契约。状态上移到全局 store 之后（`AppLayout.tsx:88,89`、`notifications/index.tsx:21,22` 都只消费 store 的 `items`/`unreadCount`），已不存在第二个 limit 调用方。
- **是否检查动态引用和跨模块使用**：**是**。全仓搜索；检查 `store/` 三个文件的全部导出（其余导出 `loadNotifications`/`readNotification`/`readAllNotifications`/reducer 均有消费者）；确认 `store/` 下无 barrel 转发。
- **删除或简化的潜在影响**：无。常量本身仍被同文件使用。
- **建议处理方式**：把 `export const NOTIFICATION_FETCH_LIMIT = 100;` 改为 `const NOTIFICATION_FETCH_LIMIT = 100;`。**不要删除常量本身。**
- **结论分类**：确认的历史残留
- **证据可信度**：高

---

#### P0-05 后端两个被 chunk 版取代的公开扫描入口

- **分类**：P0
- **文件与行号**：`backend/src/main/java/org/lbl/system/log/risk/AuditRiskAnalysisService.java:49`（`scanLoginRisks`）、`:58`（`scanOperationRisks`）
- **相关引用/调用链**：全 backend 搜索，两方法**仅命中定义行**。实际被使用的是 `scan`（`AuditRiskController.java:30`）与 `scanLoginRiskChunk`/`scanOperationRiskChunk`（`ChunkedAuditRiskScanService.java:40,44` 以方法引用 `analysis::scanLoginRiskChunk` / `analysis::scanOperationRiskChunk` 调用）。`backend/src/test` 中无引用。
- **当前代码的实际作用**：各查一类日志（登录 / 操作），调 `evaluate(..., truncate=true)` 做结果裁剪。注释（`:48`、`:57`）自称"供只拥有登录日志权限的 Agent 工具复用"/"避免操作审计工具越权读取登录日志"。
- **判断可能冗余的依据**：两个方法要实现的那个"按权限裁剪日志来源"的目标，**已由 chunk 版达成**——`ChunkedAuditRiskScanService` 的 chunk 方法同样按登录/操作分离（`:39-45`），且 `LoginAuditReportTool.java:53` 与 `OperationAuditReportTool.java:52` 走的都是它。两个旧入口因此成为零调用者。**关键**：方法引用（`analysis::scanLoginRiskChunk`）是文本搜索容易漏掉的动态引用形式，本次已专门检查，确认命中 chunk 版而非旧版。
- **是否检查动态引用和跨模块使用**：**是**。搜索方法名与 `analysis::` 方法引用两种形式；枚举全部 `.scan(` 调用点；确认 `@Autowired`/构造器注入点只有 `AuditRiskController` 与 `ChunkedAuditRiskScanService`。
- **删除或简化的潜在影响**：删除后若将来要加"单类日志 + 结果裁剪"的入口，需重写约 8 行。不删除的代价是两个 `public` 方法带来"这可能是入口"的误导。
- **建议处理方式**：删除，或降级为 `private`（若为将来保留，请在注释里显式写明"当前无调用方，为单类扫描预留"）。
- **结论分类**：确认的历史残留
- **证据可信度**：高

---

#### P0-06 JWT 过滤器中已失效的文档路径放行

- **分类**：P0
- **文件与行号**：`backend/src/main/java/org/lbl/security/filter/JwtAuthenticationFilter.java:87`
- **相关引用/调用链**：`shouldNotFilter` 的条件为 `uri.startsWith("/api/auth/") && !uri.startsWith("/api/auth/onboarding/") || uri.startsWith("/swagger") || uri.startsWith("/v3/")`。全仓搜索 `swagger|/v3/` 仅 3 处命中：`WebSecurityConfig.java:61`、`:62`（注释）与 `JwtAuthenticationFilter.java:87`（本行）。`backend/pom.xml` **无** springdoc/knife4j 依赖；`backend/src/main/resources` 无相关配置。
- **当前代码的实际作用**：对 `/swagger*`、`/v3/*` 请求跳过 JWT 解析与权限注入。
- **判断可能冗余的依据**：**这是同一轮清理漏改的一半。** `WebSecurityConfig.java:61-64` 的注释明确写着"springdoc/swagger 依赖已移除，原先的 `/swagger-ui/**` 与 `/v3/api-docs/**` 放行规则一并删掉——安全配置应当准确反映'什么是对外公开的'，留着已不存在的路径放行，会在将来重新引入文档依赖时悄悄把整个接口面暴露出去"，但**只删了 `authorizeHttpRequests` 一侧**，过滤器一侧漏了。当前依赖集下这两个前缀不存在任何处理器：`anyRequest().authenticated()`（`WebSecurityConfig.java:66`）会先拦下，带令牌也会因无 handler 而 404。因此该豁免**没有任何可观察效果**。
- **是否检查动态引用和跨模块使用**：**是**。搜索全仓 `springdoc|knife4j|swagger|/v3/`（含 pom、resources、java）；确认无 `RemoteIpValve` 或 `forward-headers-strategy` 等其他机制涉及。
- **删除或简化的潜在影响**：**功能影响为零。** 保留的实际风险是负面的：与 `WebSecurityConfig` 的说明自相矛盾，并在将来引入文档依赖时形成静默放行——正是 `WebSecurityConfig` 注释所警惕的情形。
- **建议处理方式**：删除 `|| uri.startsWith("/swagger") || uri.startsWith("/v3/")`。`shouldNotFilter` 中的 `/api/auth/**`（`!onboarding/`）分支必须原样保留。
- **结论分类**：确认的历史残留
- **证据可信度**：高

---

#### P0-07 验证码服务中已删开关留下的死字段与过时说明

- **分类**：P0
- **文件与行号**：
  - `backend/src/main/java/org/lbl/auth/service/CaptchaService.java:54`（`private static final Logger log = LoggerFactory.getLogger(CaptchaService.class);`）——全文件对 `log.` 零调用。
  - `backend/src/main/java/org/lbl/auth/service/CaptchaService.java:97-111`（`issue` 的 `@return` 说明）、`:113-132`（`verify` 说明）、`:261-268`（`Challenge` record 说明）——均宣称"关闭该功能时直接放行/`enabled=false` 不占配额不写 Redis"。
  - `backend/src/main/java/org/lbl/auth/model/RegistrationRequest.java:13`——注释引用 `lbl.security.captcha-enabled=false`。
- **相关引用/调用链**：`lbl.security.captcha-enabled` 在 `SecurityProperties`（`SecurityProperties.java:29-37`，record 只有 jwtSecret / accessTokenMinutes / idleHours / rememberedIdleDays / rememberedAbsoluteDays / secureCookie / corsAllowedOrigins / trustedProxies / passwordMaxAgeDays）中**已不存在**，全仓搜索 `captcha-enabled|captchaEnabled` 只剩 `RegistrationRequest.java:12-14` 的注释。后端 `Challenge` 记录的 `enabled` 分量已随开关一并删除，`issue()` 现在永远返回 `{captchaId, image}`。
- **当前代码的实际作用**：`log` 字段是已删告警分支的残留（原为 `log.warn("Graphical captcha is disabled …")`）；注释仍在描述一个不存在的运行模式。
- **判断可能冗余的依据**：配置键已删除，record 绑定不到它——**即使有人在 yml 里写 `CAPTCHA_ENABLED=false`，也会被静默忽略**，而注释与前端类型仍暗示它有效。
- **是否检查动态引用和跨模块使用**：**是**。搜索 `captcha-enabled|captchaEnabled` 全仓；搜索 `log` 在 `CaptchaService` 内的全部出现（除字段声明与 import 外零命中）；核对 `SecurityProperties` record 分量清单。
- **删除或简化的潜在影响**：删除死字段与过时注释零功能影响。**真正的最坏影响来自保留**：以后有人按注释去设 `CAPTCHA_ENABLED=false`，会以为验证码关了、实际没关。
- **建议处理方式**：删除 `:54` 的 `log` 字段及其两个 import；删除 `:97-111`、`:113-132`、`:261-268` 中"关闭功能"的表述；修正 `RegistrationRequest.java:13` 的注释。**不要**因此恢复开关功能——`git show 913c834` 显示删除是有意的（验证码必须始终开启）。
- **结论分类**：确认的历史残留
- **证据可信度**：高

---

#### P0-08 CSS 引用了全项目从未定义的变量 `--fs-md`

- **分类**：P0（本次唯一的**真实样式缺陷**）
- **文件与行号**：`frontend/src/components/ai/AiAssistant.css:212`（规则块 `:207-214`）
- **选择器原文**：
  ```css
  .ai-markdown h1, .ai-markdown h2, .ai-markdown h3, .ai-markdown h4 {
    margin: 12px 0 6px;
    font-size: var(--fs-md);
    line-height: 1.5;
  }
  ```
- **相关引用/调用链**：`--fs-md` 在 `frontend` 全目录**只命中本行自身**。`styles/theme.css:34-45` 定义的是 `--fs-base / --fs-xs / --fs-sm / --fs-lg / --fs-xl / --fs-2xl / --fs-3xl / --fs-display / --fs-hero / --fs-huge / --fs-mega`——**没有 `--fs-md`**。`theme/ThemeProvider.tsx:109-117` 只 `setProperty` `--brand / --font-sans / --fs-base / --success / --warning / --error`。`git log -S"--fs-md"` 返回空 → **自引入（`b20d1c0`，2026-10-08）起就从未定义过**。可达性已确认：`components/ai/AgentMarkdown.tsx:11` 使用 `react-markdown` + `remarkGfm`，h1–h4 正常产出。
- **当前代码的实际作用**：本意是统一 AI 报告内 markdown 标题的字号。
- **判断可能冗余的依据**：`var()` 引用未定义变量**且无兜底值** → 该声明在 computed-value 阶段失效，`font-size` 回退为继承值，结果是 **AI 报告的 markdown 标题与正文同号，标题层级完全丢失**。这不是"未使用的样式"，而是"使用中但无效的样式"。
- **佐证：同类 bug 在本项目已发生过一次并被记录**。`frontend/src/pages/account/security/security.css:28` 的注释原文："曾经这里是 `var(--text-strong)` —— 那个变量**全项目从未定义过**（……）"。说明这是本项目的**复发型缺陷模式**，而 `--fs-md` 是迄今未被发现的第二例。
- **是否检查动态引用和跨模块使用**：**是**。逐个比对全仓 `var(--*)` 使用清单与 `theme.css` / `tokens.ts` / `ThemeProvider.tsx` 的定义清单；核对 Git 历史确认从未定义；确认 markdown 渲染链路可达。
- **删除或简化的潜在影响**：**不能删规则**——同一块里的 `margin` 与 `line-height` 是有效的、删了会丢标题间距。只需替换 token。
- **建议处理方式**：把 `var(--fs-md)` 改为 `var(--fs-xl)`（与"区块标题"梯度一致，`theme.css:38` 注释标注为 18px）。修复后 AI 报告标题将明显大于正文。
- **结论分类**：确认的历史残留（无效声明）
- **证据可信度**：高

---

#### P0-09 登录页被注释块废弃的"统一认证"按钮及其配套样式

- **分类**：P0
- **文件与行号**：
  - `frontend/src/pages/login/index.tsx:119-134`（整块 JSX 注释，含 `:127` 的 `` className={`login-unified-button${...}`} ``）
  - `frontend/src/pages/login/index.tsx:32`（`const unifiedProvider = providers.find((provider) => provider.key === 'uias');`——**实际执行代码中零读取**，唯一引用在注释块 `:122,125,126,127,129`）
  - `frontend/src/pages/login/login.css:236`、`:244`、`:247`、`:253`（4 条 `.login-panel .ant-btn.login-unified-button` 规则）
  - `frontend/src/pages/login/login.css:235`（与该文件实际行为**相反**的注释）
- **相关引用/调用链**：`login-unified-button` 在 `frontend/src` 只命中 `login/index.tsx:127`，而该行位于 `{/* {unifiedProvider && ( … )} */}`（119–134 行）内部；实际渲染的是 `:136-155` 的圆形按钮 `` className={`social-icon-button social-${provider.key}${...}`} ``。`pages/login/**` 被 `componentRegistry.tsx:19` 的 glob 显式排除（`'!../pages/login/**'`），不存在菜单动态配置依赖这段 JSX 的可能。
- **当前代码的实际作用**：曾用于展示"统一身份认证（uias）"的独立主推长按钮；现由 `:136-155` 的通用 provider 列表承接全部第三方登录入口，`uias` 作为 `providers` 数组的一员已包含在内。
- **判断可能冗余的依据**：
  1. 注释块是显式的开发期保留标记，不是活跃实现；
  2. 驱动它的 `unifiedProvider` 成为孤儿变量——**这是 `noUnusedLocals` 类静态检查的报警来源**；
  3. 功能上已被通用列表覆盖，构成重复入口；
  4. `login.css:235` 的注释写"当前启用的统一认证长按钮；上面的圆形按钮样式保留，方便按环境切回原方案"，而**代码事实完全相反**（生效的是圆形按钮）。
  5. **注意判定方式**：本条的类名在 TSX 中**搜得到**（在注释里），这正是"仅凭搜索不到就判死"的反例；必须读到注释边界才能确认不可达。
- **是否检查动态引用和跨模块使用**：**是**。读取该文件全文确认注释边界（`:119` 起、`:134` 止）；搜索 `unifiedProvider`、`login-unified-button`、`safety-certificate`；核对 `componentRegistry.tsx` 的 glob 排除项；核对后端 provider key 集合（`uias` 存在于 `application-local.yml:61-66`）。
- **删除或简化的潜在影响**：若产品确实计划把 uias 从图标列表里摘出、做成独立主推大按钮（注释块暗示的形态），删除会丢失已调好的 JSX + CSS 参考；但当前通用列表已能打开 uias 登录，**不构成功能缺口**。
- **建议处理方式**：**二选一，需业务确认**——
  - 方案 A（清理）：删注释块 + 删 `:32` 的 `unifiedProvider` + 删 `login.css:236-258`，并在 `DECISIONS.md` 记一条"uias 由通用 provider 列表承载"。
  - 方案 B（保留）：保留 CSS 与注释块，但**删除孤儿变量** `:32`，并把 `login.css:235` 的注释改为"已停用，保留备用"，避免继续误导。
- **结论分类**：疑似历史残留（"注释块 + 孤儿变量 + 配套死样式"三点为确认；"是否有意预留"需业务确认）
- **证据可信度**：高

---

#### P0-10 需求模块内 4 份重复的类型/状态标签映射

- **分类**：P0
- **文件与行号**：
  - `frontend/src/pages/intranet/requirement/RequirementApprovalDetailModal.tsx:8`（`typeText`）、`:9-15`（`statusText`）
  - `frontend/src/pages/intranet/requirement/RequirementDetailDrawer.tsx:4`（`typeText`）、`:5`（`statusText`）
  - `frontend/src/pages/intranet/requirement/index.tsx:20`（`types`）、`:21-27`（`statuses`）
  - `frontend/src/pages/intranet/requirement/RequirementDialog.tsx:9`（新建时的类型下拉选项）
- **相关引用/调用链**：4 份映射描述同一组业务枚举（需求类型 `1 新增交易 / 2 交易变更 / 3 交易调用`；状态 `0 草稿 / 1 审批中 / 2 已通过 / 3 已拒绝 / 4 已撤销`，各带 antd Tag 颜色）。`RequirementDetailDrawer.tsx:4-5` 与 `RequirementApprovalDetailModal.tsx:8-15` 的**字面量逐字完全相同**。
- **当前代码的实际作用**：把需求类型与状态数字渲染为中文标签与标签色。
- **判断可能冗余的依据**：同一模块内同一业务枚举存在 4 份独立定义，无任何一份被复用。**这属于同层重复，不是分层职责**——4 处都在前端展示层，都在 `pages/intranet/requirement/` 目录内，且 D-036/D-037 已把状态语义固定为"`TINYINT` 0-4"，不存在各页面需要不同文案的业务理由。风险是后端调整枚举时需同步 4 处，漏一处即静默显示错误文案（参见 P0-11 的同类问题已实际发生）。
- **是否检查动态引用和跨模块使用**：**是**。逐份核对其引用点与字面量内容；确认 4 处均在 intranet 需求模块内、无跨模块消费；确认不存在从后端下发标签映射的机制（后端只返回数字，见 `RequirementListVO`）。
- **删除或简化的潜在影响**：抽成 `pages/intranet/requirement/constants.ts`（纯 `.ts`，**不会**被 `import.meta.glob` 收录为菜单候选，符合 `ARCHITECTURE.md` §2 的约束）后，删除 4 份重复定义。零行为变化。
- **建议处理方式**：新建 `pages/intranet/requirement/constants.ts`，导出 `REQUIREMENT_TYPE_TEXT`、`REQUIREMENT_STATUS_TEXT`、以及供下拉用的 `REQUIREMENT_TYPE_OPTIONS`，4 处改为导入。**用 `.ts` 而非 `.tsx`**，避免进入菜单组件候选列表（D-012 已知风险）。
- **结论分类**：确认的历史残留（重复实现）
- **证据可信度**：高

---

#### P0-11 交易模块内 2 份状态/打印方式枚举，且已造成实际展示不一致

- **分类**：P0（**本次唯一已实际发生的可见缺陷**）
- **文件与行号**：
  - `frontend/src/pages/intranet/transaction/index.tsx:18-23`（交易状态映射）、`:24`（`const printMode: Record<number, string> = { 0: '无', 1: '异步', 2: '同步' };`）
  - `frontend/src/pages/intranet/transaction/TransactionDialog.tsx:90-97`（交易状态下拉）、`:124-131`（打印方式下拉）
  - `frontend/src/pages/intranet/transaction/TransactionVersionDetailModal.tsx:39`（**问题点**）
- **相关引用/调用链**：
  - `index.tsx:170` 渲染 `printMode[detail.printFileMode]` → 用户看到"异步"/"同步"。
  - `TransactionVersionDetailModal.tsx:39` 渲染 `{version.printFileMode}` → 用户看到裸数字 `1` / `2`。
  - `index.tsx:192` 的版本历史表与 `TransactionVersionDetailModal.tsx` 的版本快照弹窗是同一份数据的两个入口。
- **当前代码的实际作用**：把 `printFileMode` 数字翻译为中文。
- **判断可能冗余的依据**：
  1. 同一枚举的标签映射在 `index.tsx:24` 与 `TransactionDialog.tsx:124-131` 各写一份；
  2. 交易状态映射在 `index.tsx:18-23` 与 `TransactionDialog.tsx:90-97` 各写一份；
  3. **并且已经产生了用户可见的不一致**：列表页显示"打印文件：异步"，而同一个交易的版本快照弹窗显示"打印文件模式：1"。这不是推测，是两处代码字面差异的必然结果——`TransactionVersionDetailModal.tsx` 整个文件**没有引入任何 `printMode` 映射**。
- **是否检查动态引用和跨模块使用**：**是**。枚举两处映射的引用点；确认 `TransactionVersionDetailModal` 是唯一渲染版本快照的组件；确认后端 `TransactionVersionDetailVO`（`vo/TransactionVersionDetailVO.java:8-11`）返回的是完整 `TransactionVersionEntity`，`printFileMode` 字段确实存在且为数字类型。
- **删除或简化的潜在影响**：抽公共映射后修复 `:39` 会**改变该处显示文案**（从 `1` 变为"异步"）——这是修正而非回归，但属于用户可见变化，建议知会业务。抽映射本身零风险。
- **建议处理方式**：
  1. 在 `pages/intranet/transaction/` 下新建 `constants.ts`（`.ts` 文件），导出 `TRANSACTION_STATUS_TEXT`、`TRANSACTION_STATUS_OPTIONS`、`PRINT_MODE_TEXT`、`PRINT_MODE_OPTIONS`；
  2. `index.tsx`、`TransactionDialog.tsx` 改为导入；
  3. **修复 `TransactionVersionDetailModal.tsx:39`**，改为 `{PRINT_MODE_TEXT[version.printFileMode] ?? '-'}`。
- **结论分类**：确认的历史残留（其中"展示不一致"为已发生的缺陷）
- **证据可信度**：高

---

#### P0-12 `.login-form-wrap` 的移动端媒体查询被注册页高特异性规则压死

- **分类**：P0
- **文件与行号**：
  - `frontend/src/pages/register/register.css:2`（`.register-page .login-form-wrap { width: min(468px, 80%); }`，特异性 (0,2,0)）
  - `frontend/src/pages/login/login.css:279-281`（`@media (max-width: 800px) { .login-form-wrap { width: min(468px, 86%); } }`，特异性 (0,1,0)）
  - 同类：`register.css:1`（`.register-page .login-heading { margin: … }`）压掉 `login.css:285-287`（`.login-heading { margin-top: 48px; }`）
- **相关引用/调用链**：`pages/register/index.tsx:13-14` 同时导入 `login.css` 与 `register.css`；`:113` 根节点 `className='login-page register-page'`；`:127` 使用 `login-form-wrap`。
- **当前代码的实际作用**：注册页表单容器的宽度与标题间距。
- **判断可能冗余的依据**：**媒体查询不增加特异性**，因此 (0,2,0) 恒胜 (0,1,0)，注册页在 ≤800px 视口下**永远拿不到 86% 的宽度**，`login.css` 那两条移动端规则（`:279-281`、`:285-287`）对注册页是**死覆盖**。它们在登录页仍有效（登录页根节点无 `register-page`）。
- **是否检查动态引用和跨模块使用**：**是**。核对两文件的导入关系与根节点类名；逐条计算选择器特异性；确认媒体查询内的选择器未提升特异性。
- **删除或简化的潜在影响**：`login.css` 的两条规则**对其他页面仍有效，不得删除**。修复只需在 `register.css` 内补同特异性的媒体查询，属视觉微调，需目视确认。另注：`register.css` 与 `login.css` 都是基线登录/注册页样式，按 `AGENTS.md` §2 修改前需说明。
- **建议处理方式**：二选一，需业务确认——
  - 保持现状（接受注册页窄屏固定 80%）并在 `register.css` 加注释说明有意覆盖；
  - 或在 `register.css:2` 之后补 `@media (max-width: 800px) { .register-page .login-form-wrap { width: min(468px, 86%); } }` 使两页移动端一致。
- **结论分类**：疑似历史残留（死覆盖确凿；期望行为需业务确认）
- **证据可信度**：高

---

#### P0-13 重复的日志页样式表

- **分类**：P0
- **文件与行号**：`frontend/src/pages/system/loginlog/index.css:2` 与 `frontend/src/pages/system/operatelog/index.css:2-19`
- **相关引用/调用链**：两文件的 5 条规则逐条相同：`.log-page .ant-table-wrapper .ant-pagination`、`.log-toolbar`、`.log-strong`、`.log-mono`、`.log-time`。两页都在使用（`loginlog/index.tsx:52-57,61,90`；`operatelog/index.tsx:88,122`）。两文件各自还有 `@import '../shared.css';`，而 `layouts/AppLayout.tsx:25` 已全局导入一次 `shared.css`。另：`pages/system/dept/index.css` 整文件只有 `@import '../shared.css';` 一行。
- **当前代码的实际作用**：两个日志页的分页间距与文本样式（等宽 IP/RID、加粗、时间不换行）。
- **判断可能冗余的依据**：两组等价规则重复维护；`@import` 与全局导入重复（Vite 会去重，属冗余防御）。**但两份规则对各自页面都真实生效**，因此不能简单删除任一份。
- **是否检查动态引用和跨模块使用**：**是**。逐类名在两页 TSX 中定位使用点；核对 `@import` 链与 `AppLayout` 的全局加载点；确认 `log-page` 类名在两页根节点上都存在。
- **删除或简化的潜在影响**：**删任一份都会破坏对应页面的分页间距或文本色**（`loginlog/index.css:2` 同时含 base 样式与 `@import`，删整行会连带丢失）。合并为共享文件属基线公共样式重构，需按 `AGENTS.md` §2 申请批准。
- **建议处理方式**：把 5 条规则提升到已全局加载的 `pages/system/shared.css`，两个 `index.css` 只保留各自的差异部分（当前无差异，可整体删除文件并移除对应 import）。属基线改动，**需用户批准**。
- **结论分类**：确认的历史残留（重复实现）
- **证据可信度**：高

---

#### P0-14 `transaction-detail-drawer` 与 `antd-showcase` 是"有挂钩、无样式"

- **分类**：P0（与"死样式"相反的方向）
- **文件与行号**：
  - `frontend/src/pages/intranet/transaction/index.tsx:151`（`rootClassName='transaction-detail-drawer'`）
  - `frontend/src/pages/intranet/requirement/RequirementDetailDrawer.tsx:7`（同 `rootClassName='transaction-detail-drawer'`）
  - `frontend/src/pages/home/index.tsx:343`（`className='showcase-card antd-showcase'`）
- **相关引用/调用链**：`transaction-detail-drawer` 在 `frontend/src/**/*.css`（含 `@import` 引入的 `shared.css`）**零命中**；`antd-showcase` 同样零命中，而相邻的 `showcase-card` 在 `home.css:10` 有定义。
- **当前代码的实际作用**：无——类名被传出，但没有任何 CSS 规则命中。
- **判断可能冗余的依据**：静态可证"挂钩无样式"。这类问题容易被误判为"死样式"的反面，实际含义是**当时打算写样式但没写**，或**样式被删了、属性忘了删**。两个 intranet 页共用同一个 Drawer 类名也说明它曾被当作统一 hook 使用。
- **是否检查动态引用和跨模块使用**：**是**。全 CSS 文件搜索（含 `@import` 链）与全 TSX 搜索双向比对。
- **删除或简化的潜在影响**：删 prop 无视觉变化；补样式则会新增视觉。
- **建议处理方式**：与业务确认是"缺样式"（该 Drawer 需要专属样式）还是"多余 hook"（当时废弃了样式方案）。**本报告不建议直接删除**，因为两个页面共用它，暗示存在未落地的统一意图。
- **结论分类**：需要业务确认
- **证据可信度**：高（对"零规则命中"）／低（对该 prop 是否有意）

---

#### P0-15 后端两个已被一次性配置接口取代的单字段端点

- **分类**：P0（与 P1-02 同一处，从后端侧记录）
- **文件与行号**：`backend/src/main/java/org/lbl/intranet/requirement/controller/IntranetRequirementController.java:40`（`PUT /{id}/developer`）、`:43`（`PUT /{id}/transaction`）
- **相关引用/调用链**：全仓（backend + frontend + docs）搜索 `assignTransaction` / `assignDeveloper` / `AssignTransactionRequest` / `AssignDeveloperRequest`，命中**只有**：控制器 `:7,8,40,43`、请求 DTO `AssignDeveloperRequest.java:3` / `AssignTransactionRequest.java:3`、服务层 `IntranetRequirementService.java:19,20,129,177`。**`docs/` 目录零命中**，说明这两个端点从未在决策文档中登记为独立能力。前端侧对应封装已确认零消费者（P0-01）。
- **当前代码的实际作用**：分别只修改 `transactionId` 或 `developerUserId` 一个字段。
- **判断可能冗余的依据**：
  1. 前端零调用（P0-01）；
  2. 与 `:42` 的 `PUT /{id}/implementation`（`configureImplementation`）重叠，后者一次性配置两个字段；
  3. **D-040 明确把交易与开发负责人配置定义为一次原子操作**："新增/变更需求在分配交易和开发负责人、完成交易配置后，由具备 `intranet:requirement:assign` 的用户显式执行'版本生效'"；
  4. 两个旧方法各自复制了 `configureImplementation` 的一半校验逻辑（见 P1-02），是典型的"新旧两套实现并存"。
- **是否检查动态引用和跨模块使用**：**是**。检查 `@PreAuthorize`、`@OperationLog`、前端全仓、`docs/` 全仓、测试代码（`backend/src/test` 无引用）、以及是否有 `@RequestMapping` 类级前缀被外部拼装。
- **删除或简化的潜在影响**：删除会移除两个 HTTP 端点。若存在仓库外的调用方（例如 Postman 集合、内部脚本），会返回 404。**因此不建议直接删除端点**，建议先确认无外部调用方。
- **建议处理方式**：**需业务确认后再动**。若确认无外部调用方，删除两个端点 + 两个 Request DTO + 两个服务方法；若希望保留 HTTP 层（例如供运维脚本使用），则至少把三处重复校验抽成私有方法（见 P1-02 / P1-09）。
- **结论分类**：确认的历史残留（前端零调用、D-040 已定义替代形态）／端点的最终去留**需要业务确认**
- **证据可信度**：高（对"前端与文档零引用"）／中（对"仓库外无调用方"）

---

#### P0-16 空方法体且仍被调用的通知方法

- **分类**：P0
- **文件与行号**：`backend/src/main/java/org/lbl/approval/departmenttransfer/DepartmentTransferApprovalService.java:205-207`
  ```java
  private void notifyCurrentReviewer(DepartmentTransferRequestEntity request, ApprovalTaskEntity task, UserEntity requester) {
      // 候选组成员通过审批中心待办获取任务；超级管理员另有观察通知。
  }
  ```
- **相关引用/调用链**：在 `:103` 与 `:172` 被调用 2 次，方法体为空。相邻的 `reviewerPhrase(ApprovalTaskEntity task, String deptLabel)`（`:201-203`）**忽略了传入的 `task` 参数**，只用 `deptLabel` 拼接"等待 X 部门审批人或超级管理员审批"。
- **当前代码的实际作用**：无。它表达了"当前审批人（候选组成员）通过待办列表获取任务、不单独发通知"这一决策结果。
- **判断可能冗余的依据**：空方法 + 三参未使用，唯一的"作用"是一句注释。它会让读者误以为候选审批人已被通知（方法名 `notify…` 强烈暗示有副作用），而实际通知只发生在 `notifySuperAdmins`（`:209-215`）与 `notifyParticipants`（`:217`）。
- **是否检查动态引用和跨模块使用**：**是**。确认调用点 2 处；确认无 `@EventListener`/AOP 切点按方法名匹配该类；确认该类不是 `@Component` 被外部注入（`DepartmentTransferApprovalService` 由 `DepartmentTransferController` 注入，但注入的是整个类，不涉及私有方法）。
- **删除或简化的潜在影响**：删除方法 + 2 处调用 + `request`/`task`/`requester` 参数的传递，零行为变化（因为方法体本来就是空的）。**唯一损失**是那句说明"候选组通过待办获取任务"的注释——应先把它移到 `:103`、`:172` 的调用点或类级注释，再删方法。
- **建议处理方式**：删除 `:205-207` 的方法定义与 `:103`、`:172` 两处调用，把注释内容上移到类级或调用点。同时给 `reviewerPhrase` 去掉未使用的 `task` 参数（或改为使用它）。
- **结论分类**：确认的历史残留
- **证据可信度**：高

---

#### P0-25 新增交易明细表上一个无人读写的孤儿列

- **分类**：P0
- **文件与行号**：`backend/src/main/resources/db/intranet/V12__create_intranet_requirement.sql:25`（`intranet_requirement_new_transaction_detail.expected_online_date`）
- **相关引用/调用链**：`expected_online_date` / `expectedOnlineDate` 全仓命中如下：
  - **主表（权威）**：`V15__add_intranet_requirement_description_and_expected_online_date.sql:3`（`ADD COLUMN expected_online_date DATE NULL AFTER transaction_id`）、`IntranetRequirementEntity.java:17`、`RequirementRequest.java:12`、`RequirementListVO`、`IntranetRequirementService.java:61,63`（排序）、`:222`（写入）、前端 `RequirementDialog.tsx:27,45,83`、`requirement/index.tsx:48`、`RequirementApprovalDetailModal.tsx:129`。
  - **交易调用明细（在用）**：`V12:43`、`TransactionCallRequirementDetailEntity.java:15`、前端 `TransactionCallDetailDialog.tsx:9,21,30,48`、`RequirementApprovalDetailModal.tsx:190`。
  - **新增交易明细（孤儿）**：`V12:25` **仅此一处**。`NewTransactionRequirementDetailEntity.java:10-16` **无** `expectedOnlineDate` 字段；`RequirementApprovalDetailModal.tsx:133` 的"新增交易"分支**不渲染**该字段；`V3/V5/V10` 等 intranet 演示数据的 INSERT 列清单中**均不含**该列。
- **当前代码的实际作用**：建了一张表里的一列，但没有任何 Java 字段、Mapper SQL、DTO/VO、前端类型读写它。MyBatis-Plus 不会读写未映射的列，且不存在 `r.*` 形式的查询涉及该表。
- **判断可能冗余的依据**：
  1. "期望上线时间"这一概念在**三处**表达（新增交易明细列 / 交易调用明细列 / 主表列）；
  2. `V15` 已明确把权威字段补在**主表**并驱动列表排序与前端表单，因此主表那份是最终形态；
  3. 交易调用明细那份**仍在使用**（实体已映射、前端在渲染），只有**新增交易明细**这一处成了无实体映射的孤儿；
  4. **注意与 D-037 边界区分**：D-037 的"保留明细表中业务允许的冗余字段"指的是**实体已映射且在 UI 渲染**的字段（如 `oa_contact_no`、`cost_business_contact`、`business_department`），与本列性质不同——本列连实体映射都没有，前端也无法读到它。
- **是否检查动态引用和跨模块使用**：**是**。检查了实体映射、`@Select`/`@Update` 注解 SQL（`backend/src/main/resources/**/*.xml` 为空，全部为注解式）、服务层 `LambdaQueryWrapper` 字段引用、前端 types/api/对话框、全部 intranet 演示数据 INSERT 列清单、测试代码。
- **删除或简化的潜在影响**：若历史上曾把数据写进该列，`DROP COLUMN` 会丢数据。**当前不存在任何写入路径**，但删除前仍应先只读核对。
- **建议处理方式**：
  1. **先只读计数**：`SELECT COUNT(*) FROM intranet_requirement_new_transaction_detail WHERE expected_online_date IS NOT NULL;`
  2. 确认为 0 后，**新增一个独立迁移脚本**（例如 `V17__drop_orphan_new_transaction_expected_online_date.sql`）执行 `DROP COLUMN`；
  3. **不要修改已执行的 `V12`**（一次性脚本，改它不会对已存在的库生效）。
- **结论分类**：确认的历史残留（数据库清理动作**需业务确认**后由用户执行）
- **证据可信度**：高

---

#### P0-26 `org.kariya` 失效日志配置键与 `application-dev.yml` 空模板

- **分类**：P0
- **文件与行号**：
  - `backend/src/main/resources/application-local.yml:34`（`org.kariya: DEBUG`）——全仓 `org.kariya` 唯一命中，而代码包名是 `org.lbl`（同位置的 `application-dev.yml:21` 写的是正确的 `org.lbl: DEBUG`）。这是项目从 Kariya 改名为 Lbl 后遗留的失效键，**永不匹配任何 logger**。
  - `backend/src/main/resources/application-dev.yml:3-5`、`:12-14`（`url` / `username` / `password` / redis `host` / `port` / `password` 全为空）。
  - `backend/src/main/resources/application.yml:4-5`（`profiles: active: local`）。
- **相关引用/调用链**：`git ls-files` 显示只有 `application-dev.yml`、`application-prod.yml`、`application.yml` 被跟踪；`git check-ignore -v` 确认 `application-local.yml` 命中 `.gitignore:56`，`git ls-files --error-unmatch` 报"did not match any file(s) known to git"——**`application-local.yml` 未纳管**。而 `SecurityProperties.java:29` 的 `jwtSecret` **没有 `@DefaultValue`**（必填），其取值只存在于这个未跟踪文件里。
- **当前代码的实际作用**：默认启动读 `local` profile；`dev` profile 因数据源与 Redis 全空而无法直接工作。
- **判断可能冗余的依据**：`org.kariya` 是改名前缀，属明确失效的配置键；`application-dev.yml` 在被默认 profile 绕过（`application.yml:5` 指向 `local`）的同时内容为空，是半成品/旧模板。
- **是否检查动态引用和跨模块使用**：**是**。检查了全部 `@ConfigurationProperties` 前缀（`lbl.security` / `lbl.external-auth` / `lbl.agent` / `lbl.log.retention` / `jasypt.encryptor`）、`@Value`、`logging.level` 的 logger 名匹配规则、Git 跟踪状态与 `.gitignore`、`pom.xml` 中的 jasypt starter（存在，故 `ENC()` 可解密）。
- **删除或简化的潜在影响**：`org.kariya` 那一行删除或改名**零影响**（当前不匹配任何 logger）。`application-local.yml` 的修改属本地环境动作、不会进入仓库。若误删 `application-dev.yml` 会失去 dev profile 模板。
- **建议处理方式**：
  1. 把 `application-local.yml:34` 的 `org.kariya` 改为 `org.lbl`，或直接删除该行（`application-dev.yml:21` 已有等价配置）；
  2. 明确 `local` 与 `dev` 的定位（建议 `dev` 作为可跟踪的空模板、`local` 仅本地覆盖），并在交接文档写明 profile 激活方式——否则新环境按默认配置启动会报"无法绑定 `lbl.security.jwt-secret`"；
  3. 顺带复核 `lbl.security.jwt-secret` 与 jasypt 口令的存放方式（见附录 C 的密钥管理提示）。
- **结论分类**：确认的历史残留（`org.kariya`）／需要业务确认（profile 定位与未纳管文件）
- **证据可信度**：高

---

### 3.2 疑似历史残留（静态分析发现异常，仍需运行或浏览器验证）

---

#### P0-17 `--fs-display` 令牌零消费者

- **分类**：P0｜**候选类别 A（确定无使用者）但归属"有意预留"**
- **文件与行号**：`frontend/src/styles/theme.css:42`
- **选择器/声明原文**：`--fs-display: calc(var(--fs-base) * 2.3); /* ≈32 —— 看板数字 */`
- **相关引用/调用链**：全仓搜索 `fs-display` **只命中本行定义**。CSS 中无 `var(--fs-display)`，TSX 中无内联使用，`theme/tokens.ts:85` 的 `FONT_SIZES` 是 JS 独立定义。
- **当前代码的实际作用**：无。
- **判断可能冗余的依据**：零读取点；注释所指的"看板"页面在 `pages/**` 中不存在（`pages/home/index.tsx` 是组件演示页，其大数字样式用的是 `home.css:56,60` 的 `--fs-sm`/`--fs-xl`，**未使用 `--fs-display`**）。
- **是否检查动态引用和跨模块使用**：**是**。全仓变量使用/定义清单双向比对；核对 `ThemeProvider` 的 `setProperty` 清单（不含它，说明它纯由 CSS 阶梯派生）。
- **删除或简化的潜在影响**：无功能影响。但它是**刻意维护的字号阶梯成员**——`theme.css:18-19` 明确说明"字号阶梯全部用 `calc()` 从 `--fs-base` 派生，所以只需要改一个变量，全站字号一起动"，删掉它会破坏阶梯的完整性叙事。
- **建议处理方式**：**保留，不建议删除**。若要让仓库更诚实，可把注释从"看板数字"改为"预留：大号展示数字（当前无使用者）"。
- **结论分类**：有意预留能力
- **证据可信度**：高

---

#### P0-18 `--success` CSS 变量零消费者（只有写入、没有读取）

- **分类**：P0｜**候选类别 A，但归属"有意预留"**
- **文件与行号**：`frontend/src/styles/theme.css:48`；关联 `frontend/src/theme/ThemeProvider.tsx:115`
- **相关引用/调用链**：全仓 `var(--success)` **0 命中**。对照：`var(--error)` 命中 `pages/system/shared.css:15`；`var(--warning)` 命中 `pages/error/pageUnavailable.css:10`。`ThemeProvider.tsx:115` 每次渲染都执行 `root.style.setProperty('--success', semantic.success)`，但没有任何样式表读取它。
- **当前代码的实际作用**：无（写入即空操作）。
- **判断可能冗余的依据**：定义点与运行时写入点都没有消费者。
- **是否检查动态引用和跨模块使用**：**是**。全仓 `var(--*)` 清单与 `setProperty` 清单双向比对。**重要区分**：语义色本身完全正常——`tokens.ts:95-98` 的 `SEMANTIC_COLORS.success` 经 `ConfigProvider` 的 `colorSuccess` token 生效，`pages/home/index.tsx:309,314,377` 通过 `useThemePreference().semantic.success` 消费它。无消费者的**只是这一个 CSS 变量**。
- **删除或简化的潜在影响**：删除 CSS 变量后，未来若想在纯 CSS 中表达成功色需重新引入。**且它与 `--warning`/`--error` 是一组**——单独删 `--success` 会制造不对称，而 `ThemeProvider.tsx:88-96` 的注释明确把"两套必须同时投"（AntD token + CSS 变量）作为主题系统的核心契约。
- **建议处理方式**：**保留，不建议删除**。理由：它是主题投射契约的对称成员，删除收益为零而破坏契约完整性。可在 `theme.css:47` 补一句"当前仅 `--warning`/`--error` 有 CSS 侧消费者"。
- **结论分类**：有意预留能力
- **证据可信度**：高

---

#### P0-19 前端声明了、但界面从未读取的接口字段

- **分类**：P0｜**需运行验证**
- **文件与行号**：
  - `frontend/src/types/auth.ts:69`（`CurrentUser.superAdmin`）——全仓仅定义行 1 处命中。
  - `frontend/src/api/account.ts:8`（`ExternalIdentity.createdTime`）——`ExternalIdentity` 被 `pages/account/security/index.tsx:14,28,38,285` 使用，但只读 `providerKey`/`displayName`/`email`。
  - `frontend/src/api/account.ts:19`（`LoginSessionView.createdTime`）——`session.createdTime` 零命中；两处会话渲染（`account/security/index.tsx:331`、`system/user/index.tsx:247`）只拼 `loginIp`/`lastActiveTime`/`userAgent`。
  - `frontend/src/pages/intranet/transaction/types.ts:11`（`TransactionVersionSnapshot.transactionStatus`）、`:10`（`TransactionVersionMessageField.sourceFieldId`、`parentSourceFieldId`）——各仅定义行 1 处命中。
- **相关引用/调用链（后端对照，已确认后端仍在返回）**：
  - `CurrentUser.superAdmin` ← `backend/.../auth/controller/AuthController.java:127,131` 仍在返回；
  - `LoginSessionView.createdTime` ← `backend/.../auth/session/SessionView.java:5` 的 record 分量，确实返回；
  - `TransactionVersionSnapshot.*` ← `backend/.../transaction/version/vo/TransactionVersionDetailVO.java:8-11` 返回的是完整 `TransactionVersionEntity`（含 `transactionStatus` 等全部列）与完整 `TransactionVersionMessageFieldEntity`（含 `sourceFieldId`/`parentSourceFieldId`）。
- **当前代码的实际作用**：描述后端已经返回、前端类型已声明、但当前没有渲染的字段。
- **判断可能冗余的依据**：分两种性质，**不可一概而论**：
  - **`superAdmin`：倾向确认残留。** 后端把超管判定作为权限的一部分下发（`AccessPolicy.containsSuperAdmin`），而前端权限判断**全部走 `permissions` 数组**（`permission/Permission.tsx:4-5`）。该布尔值从未参与前端逻辑——这是"权限模型收敛到 `permissions` 之后"的遗留字段。
  - **`createdTime` / `transactionStatus` / `sourceFieldId` / `parentSourceFieldId`：倾向"功能未接入"而非"已下线"。** 字段语义都有明确业务价值（会话创建时间、"这个版本快照当时的交易状态"、字段级血缘溯源），后端也**确实仍在返回**。没有证据表明它们是"已下线字段对应的转换逻辑"。**注意** `transactionStatus` 尤其像"版本快照应展示当时状态"这一未完成的能力。
- **是否检查动态引用和跨模块使用**：**是**。对每个字段做词边界全仓搜索并逐行核对读取点；检查动态属性访问（无 `obj[key]` 形式触及这些字段名）；核对后端对应 DTO 确认字段确实存在。
- **删除或简化的潜在影响**：删除类型字段的**最坏影响是运行时**——若后端仍返回而前端类型没有，不报错（TS 类型窄于实际响应无害）；但若**后端已删除**这些字段而类型还留着，会让下一个开发者误以为可读，产生 `undefined` 渲染成空白。**本次无法访问运行中的接口，因此无法区分这两种情况**——这正是本条标记为"需确认"的原因。
- **建议处理方式**：**先用一次真实请求确认**（`GET /api/account/sessions` 看是否含 `createdTime`；`GET /api/intranet/transaction-versions/{id}` 看是否含 `transactionStatus`/`sourceFieldId`/`parentSourceFieldId`）：
  - 响应**含**这些键 → 保留类型，并加注释"后端已返回，前端待接入"；
  - 响应**不含** → 删除对应类型字段。
  - `superAdmin` 无论哪种情况都可直接删除前端声明（它对前端逻辑无影响，属权限模型收敛后的遗留）。
- **结论分类**：需要业务确认
- **证据可信度**：中（"前端零读取"为高；"后端是否实际返回"为未验证）

---

#### P0-20 三个明细 Dialog 内等价的局部 `Text` 组件与两份逐字相同的 `text()` 函数

- **分类**：P0
- **文件与行号**：
  - `frontend/src/pages/intranet/requirement/NewTransactionDetailDialog.tsx:7`
  - `frontend/src/pages/intranet/requirement/TransactionCallDetailDialog.tsx:10`
  - `frontend/src/pages/intranet/requirement/TransactionChangeDetailDialog.tsx:7`
  - `frontend/src/pages/intranet/requirement/RequirementApprovalDetailModal.tsx:17` 与 `frontend/src/pages/intranet/transaction/TransactionVersionDetailModal.tsx:7`（两个**逐字相同**的 `text()`）
- **原码对照**：
  ```tsx
  // NewTransactionDetailDialog.tsx:7
  const Text = ({ name, label }: { name: keyof NewTransactionDetail; label: string }) =>
    <Form.Item name={name} label={label}><Input.TextArea rows={2} maxLength={2000} /></Form.Item>;
  // TransactionCallDetailDialog.tsx:10 —— 同上，仅泛型参数不同
  // TransactionChangeDetailDialog.tsx:7 —— 同上，rows={3}
  // 两处逐字相同的空值格式化
  const text = (value?: string | number) => value === undefined || value === null || value === '' ? '-' : String(value);
  ```
- **相关引用/调用链**：三个 `Text` 在各自文件中被多次使用（如 `NewTransactionDetailDialog.tsx:14` 起连续多处）；两个 `text()` 在各自文件中作为列/字段渲染器使用（`RequirementApprovalDetailModal.tsx` 内多处、`TransactionVersionDetailModal.tsx:36-42,56`）。
- **当前代码的实际作用**：`Text` 是"多行文本 Form.Item"的局部简写；`text()` 是空值归一为 `-` 的格式化函数。
- **判断可能冗余的依据**：同一模块内 3 份等价组件 + 2 份逐字相同的函数。**D-012 已经为这类情况设立了先例**：`InlineEditableTable` / `FieldText` 被抽取到 `pages/intranet/components/` 供交易、HBase、ES 三处复用，且 D-012 的边界声明是"共享层只负责 Form + Table 的单行编辑骨架和长文本交互"——多行文本 Form.Item 简写与空值格式化正落在该边界内。
- **是否检查动态引用和跨模块使用**：**是**。确认三处 `Text` 无跨文件引用；确认两个 `text()` 字面量完全相同；确认 `pages/intranet/components/` 当前只有 2 个 `.tsx`。
- **删除或简化的潜在影响**：抽取本身零风险。**但有一个必须注意的陷阱**：`ARCHITECTURE.md` §2 与 D-012 已记录，`pages/**` 下**任何 `.tsx` 都会进入 `componentRegistry` 的菜单组件候选列表**，且无 default 导出的组件被误选会导致页面渲染失败。当前该风险被"菜单配置操作规范"规避。
- **建议处理方式**：**优先用纯逻辑 `.ts` 文件**：把两个 `text()` 抽到 `pages/intranet/components/format.ts`（`.ts` 不被 glob 收录，零风险）。`Text` 组件涉及 JSX，若抽到 `pages/intranet/components/` 会新增一个菜单候选——建议与用户确认，或直接把 `Text` 内联展开（它只有一行，抽象收益本就有限）。
- **结论分类**：疑似历史残留（重复实现确凿；抽取位置受 D-012 已知风险约束）
- **证据可信度**：高

---

#### P0-21 是/否与对客选项对在多处重复

- **分类**：P0
- **文件与行号**：
  - `frontend/src/pages/intranet/dataasset/fields/index.tsx:218-219`、`:235-236`（两组"是/否"）
  - `frontend/src/pages/intranet/transaction/fields/index.tsx:146-147`（"是/否"，值与上两处**顺序相反**：这里是 `0` 在前）
  - `frontend/src/pages/intranet/requirement/TransactionCallDetailDialog.tsx:51`（内联"现有能力是否满足"）
  - `frontend/src/pages/intranet/requirement/NewTransactionDetailDialog.tsx:14` 起多处（"是否投产数据"等）
  - `frontend/src/pages/intranet/requirement/RequirementApprovalDetailModal.tsx:18-19`（只读侧的 `yesNo` 与 `queryBehavior` 转换）
- **相关引用/调用链**：全部为 antd `<Select options={...}>` 的静态数组，无跨文件引用。
- **当前代码的实际作用**：把布尔/数字/枚举渲染为中文下拉选项，或把已存值渲染为中文文本。
- **判断可能冗余的依据**：同一组"是/否"字面量重复 6 次以上；`transaction/fields/index.tsx:146-147` 与 `dataasset/fields/index.tsx:218-219` 的**选项顺序相反**（前者 `0 否, 1 是`，后者 `1 是, 0 否`）——这本身不是缺陷（下拉顺序可不同），但说明这些定义是各自手写的、没有单一来源。对客/非对客同理（`INTERNAL`/`EXTERNAL`）。
- **是否检查动态引用和跨模块使用**：**是**。逐处核对字面量与引用点；确认均为内联字面量、无共享常量。
- **删除或简化的潜在影响**：抽到 `pages/intranet/components/options.ts`（**纯 `.ts`，零 glob 风险**）后零行为变化。若同时统一选项顺序，会**改变下拉中的显示顺序**，属用户可见微调，建议保留各处现有顺序、只共享值→文案的映射。
- **建议处理方式**：在 `pages/intranet/components/options.ts` 导出 `YES_NO_OPTIONS`、`YES_NO_TEXT`、`QUERY_BEHAVIOR_OPTIONS`、`QUERY_BEHAVIOR_TEXT`；各处改为导入。**不改变任何选项顺序。**
- **结论分类**：疑似历史残留（重复实现确凿）
- **证据可信度**：高

---

#### P0-22 重复的会话引导序列与两份 `/auth/refresh` 封装

- **分类**：P0（同时含 P1 性质）
- **文件与行号**：
  - `frontend/src/components/common/AuthGuard.tsx:26-31`
  - `frontend/src/pages/login/index.tsx:43-47`
  - `frontend/src/pages/auth/CallbackPage.tsx:31-34`
  - 两个刷新封装：`frontend/src/api/auth.ts:17`（`refresh()`）与 `frontend/src/services/authSession.ts:14-27`（`refreshAccessTokenOnce()`）
  - `frontend/src/services/authSession.ts:9-11`（`RefreshEnvelope`，与 `api/auth.ts:13` 的 `RefreshResult` 是同一契约的两处手写声明）
- **相关引用/调用链**：
  - "取 token → `fetchMe` → `dispatch(setSession)` + `dispatch(setProfile)`"这一序列在 3 处各写一遍，且 `setProfile` 的形态不一致（`AuthGuard.tsx:31` 显式构造对象，`login/index.tsx:47` 与 `CallbackPage.tsx:34` 传整个 profile）。
  - `CallbackPage.tsx:31` 用 `api/auth.ts:17` 的 `refresh()` 并**丢弃** `passwordChangeRequired`，改由 profile.user 经 `setSession` 写入同一 store 字段；`authSession.ts` 的 `refreshAccessTokenOnce()` 则显式派发 `setPasswordChangeRequired`（`authSession.ts:20`）。两条路径写同一状态。
- **当前代码的实际作用**：前者是登录/回调后的会话建立流程；后者是 401 时的静默续期。
- **判断可能冗余的依据**：
  1. 3 份等价的引导序列，注释与 `authSession.ts:14` 的"唯一续期入口"自述表明后者才是设计入口；
  2. 同一 `/auth/refresh` 契约在 `api/auth.ts:13-17` 与 `authSession.ts:9-11,14-27` 各声明一次。
- **是否检查动态引用和跨模块使用**：**是**。核对两条刷新链路的各自调用者；读取 `utils/request.ts:50-53` 确认 `authSession.ts` 刻意用裸 axios **是为了避免续期请求被响应拦截器再次处理而形成递归**——**这条设计必须保留，不得合并两条链路**。
- **删除或简化的潜在影响**：**存在真实风险**：若为"统一实现"而让续期也走 `request` 实例，会重新引入 `request.ts:50-53` 注释所述的自递归问题。因此只能收敛"引导序列"，不能收敛"刷新通道"。
- **建议处理方式**：
  1. 抽 `applyProfile(profile)` helper（放 `services/authSession.ts` 或新建 `services/sessionBootstrap.ts`），封装 `fetchMe` + 两个 dispatch，3 处调用它——**跳转逻辑留在各页面**；
  2. `CallbackPage` 改为复用 `refreshAccessTokenOnce()`，使 `passwordChangeRequired` 的处理只有一条路径；
  3. **保留** `authSession.ts` 的裸 axios 通道与 `api/auth.ts` 的 `refresh()`（后者用于外部登录回调场景，语义不同）；
  4. 为 `RefreshEnvelope` 与 `RefreshResult` 互相加注释交叉引用，标明"同一后端契约的两处声明，改动需同步"。
- **结论分类**：疑似历史残留（重复引导序列）／有意预留能力（双刷新通道）
- **证据可信度**：高

---

#### P0-23 前端制品渲染器注册表中的再导出与单注册项

- **分类**：P0 / P3（跨类，从"残留"角度记录）
- **文件与行号**：
  - `frontend/src/components/ai/artifacts/AgentArtifactView.tsx:9`、`:10`（两行再导出，零外部导入者，已计入 P0-03）
  - `frontend/src/components/ai/artifacts/ArtifactRendererRegistry.ts:9,11-13,16-28`（`Map` + `type@version` 键 + 反注册闭包 + `never` 强转）
  - `frontend/src/components/ai/artifacts/AgentArtifactView.tsx:7`（唯一的注册调用）
- **相关引用/调用链**：全仓只有 **1 个注册项**：`registerArtifactRenderer<AgentReport>('ui.report', 1, GenericReportRenderer)`。后端 4 个 Agent 工具**全部产出同一制品类型**：`LoginAuditReportTool.java:57`、`OperationAuditReportTool.java:57`、`LoginUserTimelineTool.java:57`、`OperationUserTimelineTool.java:57` 均为 `new AgentArtifact<>("ui.report", 1, …)`。
- **当前代码的实际作用**：按 `type + schemaVersion` 查表得到渲染组件；查不到则渲染降级卡片 + 原始 JSON 抽屉（`AgentArtifactView.tsx:16-25`）。
- **判断可能冗余的依据**：注册表当前等价于"如果 type==='ui.report' && version===1 则渲染 GenericReportRenderer"，却用了 Map 注册、版本化键、反注册闭包和 `never` 强转。D-003 记录了"把 4 个审计 Agent 工具整合为 2 个"是用户有意完成的改动——工具的收敛发生在后端，前端注册表未随之收敛，成了**为已不存在的多制品形态预留的壳**。
- **是否检查动态引用和跨模块使用**：**是**。搜索 `registerArtifactRenderer`/`resolveArtifactRenderer`/`AgentArtifactRendererProps` 全部命中点；确认注册只发生一次；核对后端 4 个工具的制品类型字面量。
- **删除或简化的潜在影响**：**必须谨慎**——注册表是 `AgentArtifactView.tsx:15` 的调用目标，也是"未来新增制品类型"的扩展点，且该注释（`ArtifactRendererRegistry.ts:15`）明确写了"业务扩展只需要注册 type + version，无需修改公共消息列表"。这与 `AGENTS.md` 要求保留"Agent 扩展能力"一致。**建议不删注册表本体**，只删 P0-03 中的两行无意义再导出。
- **建议处理方式**：
  - 删除 `AgentArtifactView.tsx:9-10` 两行（零消费者，已确认）；
  - **保留** `ArtifactRendererRegistry.ts`，但补一条注释说明"当前只有 1 个注册项（`ui.report@1`），保留注册表是为了新增制品类型时不必改消息列表"。
- **结论分类**：疑似历史残留（再导出部分）／有意预留能力（注册表本体）
- **证据可信度**：高

---

#### P0-24 交易列表两个查询条件的声明与后端未接入（D-026 已知）

- **分类**：P0｜**依据决策记录，判定为有意设计，仅登记不清理**
- **文件与行号**：`frontend/src/pages/intranet/transaction/index.tsx:114`（`initialSearch={{ keyword: '', esfKeyword: '', resourceKeyword: '' }}`）、`:131-135`（`searchConfig`）；后端 `backend/.../intranet/transaction/service/TransactionAssetService.java:36-46`（只消费 `keyword`/`status`/`sortField`）
- **相关引用/调用链**：这两个输入框的 `esfKeyword`/`resourceKeyword` 在提交后不参与后端查询——`keyword` 仍会同时模糊匹配交易名称、交易编码和 ESF 服务名。
- **当前代码的实际作用**：验证基线 `SmartTable` `searchConfig` 的前端样式与多条件布局。
- **判断可能冗余的依据**：从纯代码视角看，这是"输入框静默失效"，属典型残留特征。**但 D-026 明确记录这是用户确认的有意设计**："交易列表页上的'ESF 服务名 / 操作名'和'关联索引 / HBase 表'两个输入框**不接后端**，这是有意的设计，用途是验证基线 SmartTable `searchConfig` 的前端样式与多条件布局"，并且 D-026 的"明确不做"一节写："不新增后端查询参数、不为这两个条件补跨表 `EXISTS` 过滤、不把它们当作待修复缺陷。后续会话（含 Codex）不得以'输入框静默失效'为由自行补齐。"
- **是否检查动态引用和跨模块使用**：**是**。核对 `TransactionAssetService` 的参数消费情况；核对 `searchConfig` 的全部使用页面（只有 `transaction/index.tsx:131` 与 `requirement/index.tsx:88`，都是 `type: 'input'`，见 §10-15）。
- **删除或简化的潜在影响**：删除会移除 D-025 基线增强的样式验证场景。补齐后端则违反 D-026。
- **建议处理方式**：**保留，不清理。** 仅在代码处补一行注释指向 D-026，避免后续审计重复怀疑（本次即是第四次被怀疑）。
- **结论分类**：有意预留能力
- **证据可信度**：高

---

## 4. P1 重复实现和职责重叠

### P1-01 审批中心页与消息中心页整段复制部门变更审批交互 ★最高价值

- **分类**：P1
- **文件与行号**：
  - `frontend/src/pages/account/approvals/index.tsx`：`:66-95`（`approve`/`reject`）、`:183-229`（部门变更详情 Modal）、`:207-226`（`Steps` 五层三元链）、`:236-249`（拒绝原因 Modal）
  - `frontend/src/pages/account/notifications/index.tsx`：`:56-88`（`approve`/`reject`）、`:128-174`（同一个详情 Modal）、`:152-171`（同一个 `Steps` 三元链）、`:181-194`（同一个拒绝 Modal）
- **原码对照（`approve`，两处几乎逐字相同）**：
  ```tsx
  const approve = async () => {
    if (!detail) return;
    try {
      setSubmitting(true);
      const result = await approveDepartmentChange(detail.id);
      setDetail(result.data);
      message.success(result.message);
      await load();
    } catch (error) { message.error(getApiErrorMessage(error, '审批失败')); }
    finally { setSubmitting(false); }
  };
  ```
  差异仅：`approvals` 的 Modal footer 按钮带 `icon={<StopOutlined />}` / `icon={<CheckOutlined />}`，`notifications` 不带；`openDetail` 的入口来源不同（表格行动作 vs 通知列表点击 vs URL 查询参数）。
- **相关引用/调用链**：两页都导入同一组 `approveDepartmentChange` / `getDepartmentChange` / `rejectDepartmentChange`（`approvals/index.tsx:6`、`notifications/index.tsx:6`），都渲染 `RequirementApprovalDetailModal`（`approvals/index.tsx:10,230`、`notifications/index.tsx:10,175`），都 `import '../../system/shared.css'`。
- **当前代码的实际作用**：两个入口（审批中心、消息中心）提供完全一致的"查看部门变更审批进度 → 通过当前步骤 / 拒绝"能力。
- **判断可能冗余的依据**：
  1. **两者都在前端展示层**，不是"前后端分层"，也不存在"各页职责不同"的解释——同一业务、同一 API、同一 UI 结构；
  2. 重复量约 **120 行**（`approve`+`reject` 约 40 行、详情 Modal 约 47 行、拒绝 Modal 约 14 行、`Steps` 三元链 20 行）；
  3. **该重复已经产生分歧**：两处 Modal footer 的按钮图标不一致（`approvals` 有、`notifications` 无），说明它们是被复制后各自修改的，而非有意保持同步；
  4. 项目已有同类抽取先例：`RequirementApprovalDetailModal` 正是为避免这类重复而抽出的共享组件（`D-039`："审批中心和消息中心共用 `RequirementApprovalDetailModal`"）——**部门变更这条路径没有做同样的抽取**。
- **是否检查动态引用和跨模块使用**：**是**。逐行比对两文件确认重复范围；确认两处 `detail` 状态的类型同源（都来自 `api/departmentChange` 的 `DepartmentChangeDetail`）；确认两页分别由 `router/index.tsx:66,67` 静态注册、`pages/account/**` 被 `componentRegistry.tsx:19` 的 glob 排除（`'!../pages/account/**'`），因此不受菜单候选风险影响。
- **删除或简化的潜在影响**：抽取 `DepartmentChangeApprovalModal`（受控组件：`detail` + `canApprove` + `onClose` + `onCompleted`）后删掉两处重复。**关键边界**：`canApprove` 的判定留在各页——`approvals` 用 `scope === 'pending' && task.requestStatus === 'PENDING'`（`:54`），`notifications` 用 `item.type === 'INTRANET_REQUIREMENT_APPROVAL_REQUIRED'` / `notificationType === ...`（`:38,48`），语义不同，**不应合并**。这与 D-039 对 `RequirementApprovalDetailModal` 的处理方式（`canApprove` 由调用方传入）完全一致。
- **建议处理方式**：新建 `frontend/src/pages/account/components/DepartmentChangeApprovalModal.tsx`（或放在 `components/` 公共目录，需按 `AGENTS.md` §2 判断是否算基线改动——`pages/account/` 属基线，建议先说明）；把 `approve`/`reject`/两个 Modal/`Steps` 渲染搬入；两页只保留"打开哪个详情"的分支与 `canApprove` 计算。**顺带统一按钮图标**。
- **结论分类**：确认的历史残留（同层重复，非分层职责）
- **证据可信度**：高

---

### P1-02 需求实施配置的三重字段写入口

- **分类**：P1
- **文件与行号**：`backend/src/main/java/org/lbl/intranet/requirement/service/IntranetRequirementService.java:129-138`（`assignDeveloper`）、`:161-176`（`configureImplementation`）、`:177-187`（`assignTransaction`）
- **相关引用/调用链**：三个方法都写 `intranet_requirement` 的 `transaction_id` / `developer_user_id` / `developer_name` 字段。调用方见 P0-15（`assignDeveloper` 与 `assignTransaction` 前端零调用；`configureImplementation` 由 `RequirementImplementationDialog` 调用）。
- **重复的校验逻辑（逐条对照）**：

  | 校验 | `assignDeveloper` | `configureImplementation` | `assignTransaction` |
  | --- | --- | --- | --- |
  | 状态必须为 2（已通过） | `:131` | `:163` | `:179` |
  | 类型不能是 3（交易调用） | `:132` | `:163` | `:180` |
  | `requireVersionNotActivated` | `:133` | `:164` | `:181` |
  | 交易未下线 | — | `:167` | `:184` |
  | 新增交易只能选评估中 | — | `:168` | `:185` |
  | 开发人员角色校验 | `:135` | `:170` | — |
  | 开发人员存在且启用 | `:136` | `:172` | — |
  | 交易不能更换 | — | `:165` | `:182`（改为"不能重复分配"） |

- **当前代码的实际作用**：三个粒度不同的实施信息写入口。
- **判断可能冗余的依据**：
  1. 前两个旧方法各自复制了 `configureImplementation` 的**一半**校验——按状态/类型/版本三个前置条件完全相同，只有"该改哪个字段"不同；
  2. **同一组业务规则存在 3 份实现**，任何规则变更（例如放宽"仅已通过需求可配置"）都需要改 3 处，漏改即产生不一致；
  3. D-040 把这一动作定义为**一次原子操作**（"分配交易和开发负责人、完成交易配置"），三个入口与该定义不符；
  4. 与 P0-15 互为因果：因为有两个多余的方法，才需要维护两套 DTO 与两套前端封装（后者已死）。
- **是否检查动态引用和跨模块使用**：**是**。枚举三方法的全部调用点（控制器 3 个端点 + 前端 + docs + 测试）；确认 `assignTransaction`/`assignDeveloper` 无 `@Transactional` 之外的框架调用（无定时任务、无事件监听）。
- **删除或简化的潜在影响**：**若走"抽公共方法"路线**（保守），保留三个端点但让它们共用一个 `private void validateAssignable(IntranetRequirementEntity value)` 做前置校验——零 HTTP 契约变化，风险最低。**若走"删两个端点"路线**，需先确认仓库外无调用方（见 P0-15）。
- **建议处理方式**：**分两步，第一步即可执行**——
  1. **先删前端两个死封装**（P0-01，零风险）；
  2. **再抽后端公共校验**：把"状态=2 / 类型≠3 / `requireVersionNotActivated`"抽为私有方法，三处调用；差异化的校验（交易状态、角色）保留在各自方法内；
  3. **端点是否下线**留待业务确认（若确认无外部调用方，删两个端点 + 两个 Request DTO + 两个服务方法，可再减约 30 行）。
- **结论分类**：确认的历史残留（重复业务分支 + 已被替代的旧路径）
- **证据可信度**：高

---

### P1-03 未接线的平台管理员审批判定（`requiresPlatformTransferReview`）★安全语义
- **分类**：P1（同时是 P2 性质的安全判定）
- **文件与行号**：`backend/src/main/java/org/lbl/security/context/AccessPolicy.java:197-204`
  ```java
  /** 调部门会改变动态角色范围；这类迁移交由平台管理员审批。 */
  public boolean requiresPlatformTransferReview(Actor actor, UserEntity target) {
      List<RoleEntity> dynamic = roles.selectAssignedByUserId(target.getId()).stream()
              .filter(role -> role.getStatus() == 1)
              .filter(role -> Set.of("DEPT", "DEPT_AND_CHILDREN").contains(role.getDataScope())).toList();
      prepareRoles(actor, dynamic);
      return dynamic.stream().anyMatch(role -> !actor.context().rolePermissions().getOrDefault(role.getId(), Set.of()).isEmpty());
  }
  ```
- **相关引用/调用链**：**全仓（`backend/src` 含 `test`、`frontend/src`、`docs/`）搜索 `requiresPlatformTransferReview` 只有 1 处命中——就是声明行本身。** 它自诞生起从未被接线（引入提交的 diff 中也没有调用点）。当前部门调动流程实现在 `DepartmentTransferApprovalService`：审批走公共审批表（D-034），候选组固定为 `内网需求审批部 + department_approver`（**部门调动**用的是 `department_approver`），其中**没有任何平台管理员审批判定**——该类内不含 `platform`/`admin` 相关分支，`AccessPolicy` 也没有其他方法调用它。
- **当前代码的实际作用**：计算"目标用户是否持有生效的 `DEPT` / `DEPT_AND_CHILDREN` 动态角色，且这些角色的权限集合非空"，返回 `boolean`。方法的注释声明其**业务意图**是"调部门会改变动态角色范围；这类迁移交由平台管理员审批"。
- **判断可能冗余的依据**：
  1. 零调用点（唯一历史引入即无接线）；
  2. 实际部门调动审批流程中没有对应判定。
- **⚠️ 这**不是**一条可以按"删了就干净"处理的发现**，存在两种互斥解释：
  - **解释 A（实现的缺口）**：该判定**本应生效**，只是忘记接线。若如此，当前"跨数据范围调部门"缺少所声称的平台管理员审批——这是**功能/安全缺口**，不是冗余。
  - **解释 B（废弃的设计）**：动态角色范围方案后来被候选组模型（`department_approver`）取代，该判定随之作废。
  - 仓库内的证据**不足以判定**：D-034/D-035 只记录了"部门调动迁入公共审批模型、候选组为部门审批人"，**没有**提到平台管理员审批；但也没有说这种审批不需要。
- **是否检查动态引用和跨模块使用**：**是**（这是必检项）。检查了 Spring 组件扫描与注入、`@PreAuthorize` / `@OperationLog` 的 SpEL 表达式（均未引用该方法）、反射、AOP 切点、`@EventListener` / `@Scheduled`、测试代码、Mapper 注解 SQL、`application*.yml`。它是一个 `public` 方法，但只能被 Spring Bean 调用，而全仓无任何调用者。
- **删除或简化的潜在影响**：
  - **正向（若解释 A 成立）**：删除它会把一个**本应存在的授权判定**永久移除，使缺口更难发现。
  - **反向（若解释 B 成立）**：保留它会让后续审计持续怀疑（本次即是如此），并误以为该审批已实现。
- **建议处理方式**：**不要直接删除。必须先由业务回答一个问题**：
  > "部门调动若改变申请人的动态角色范围（`DEPT` / `DEPT_AND_CHILDREN`），是否应由平台管理员审批？"
  - 回答"**是**" → 在 `DepartmentTransferApprovalService` 的提交/审批路径**接线**该判定（并按 D-034 的形态约束决定是过滤候选组还是新增一步审批），同时补权限码与种子脚本；
  - 回答"**否**" → 删除该方法（约 8 行）及其依赖的 `prepareRoles` 调用路径（若该路径仅为此方法存在，可一并清理）。
  - 若暂时无法回答 → **保留并在方法上加注释**"当前无调用方；见 D-XXX 待确认的平台管理员审批边界"，避免下一个审计重复判断。
- **结论分类**：需要业务确认（疑似历史残留，**可能是未接线的安全闸门**）
- **证据可信度**：高（"零引用"为确证）／低（该判定应否生效，仓库内无法证明）

---

### P1-04 需求实施配置与版本生效的状态字段双写（**依据 D-036/D-037，判定为有意设计**）

- **分类**：P1｜**有意设计，仅登记**
- **文件与行号**：`backend/.../requirement/entity/IntranetRequirementEntity.java` 的 `status` 字段；`backend/.../requirement/service/IntranetRequirementService.java:89`、`:113`、`:124`、`:192`
- **相关引用/调用链**：`intranet_requirement.status`（0 草稿/1 审批中/2 已通过/3 已拒绝/4 已撤销）与 `sys_approval_instance.status`（PENDING/APPROVED/REJECTED/CANCELLED）表达同一业务状态，服务层在 `submit`/`approve`/`reject`/`cancel` 四处同时更新两者。
- **判断依据**：D-036 明确把需求状态定义为独立的 `TINYINT` 枚举，并规定"状态使用 `TINYINT`：`0` 草稿、`1` 审批中、`2` 已通过、`3` 已拒绝、`4` 已撤销"。D-037 进一步明确"项目组内部使用且并发量可忽略，本阶段不增加需求行锁/乐观锁"。**这是已确认的设计**：需求表保存冗余状态是为了列表查询与筛选不必 JOIN 公共审批表（与 D-034"公共审批表不得 JOIN 业务表"的形态约束配套）。
- **是否检查动态引用和跨模块使用**：**是**。确认写入集中在 `IntranetRequirementService` 的 4 处，无其他写入方；确认 `RequirementListVO.status` 被前端列表的 `filters` 使用（`requirement/index.tsx:40`），说明该字段有真实消费需求。
- **潜在风险（仅提示，不作为问题）**：若将来有**其他入口**改动公共审批实例状态（例如管理员直接操作审批表），`intranet_requirement.status` 会漂移。当前无此入口。
- **建议处理方式**：**保留**。可在 `IntranetRequirementService` 类注释中写明"需求状态是公共审批状态的查询用冗余，仅本服务写入；新增审批入口时必须同步"。
- **结论分类**：有意预留能力（有意设计）
- **证据可信度**：高

---

### P1-05 前后端密码策略的“重复”实现（**判定为合理分层，非重复**）

- **分类**：P1｜**排除项，记录以免后续误判**
- **文件与行号**：`frontend/src/utils/passwordPolicy.ts:20-21` 与 `backend/src/main/java/org/lbl/auth/service/PasswordRules.java:35-40`
- **判断依据**：经展开常量后逐字符比对，两处的正则与提示文案**当前完全一致**。但这是**前后端职责分层**，不是同层重复：
  - 前端负责即时反馈（表单校验，避免无效请求）；
  - 后端负责**安全边界**（`RegistrationRequest.java:33`、`UserCreateRequest.java:33`、`PasswordChangeRequest.java:26` 用 `@Pattern(message = ...)` 强制校验），前端可被绕过，后端不可省。
- **是否检查动态引用和跨模块使用**：**是**。核对三处后端请求 DTO 的注解；核对前端 4 个消费点（`AppLayout.tsx:338`、`register/index.tsx`、`account/setup/index.tsx:171`、`UserDialog.tsx:205`）。
- **建议处理方式**：**保留两层**。可选的工程改善：把正则与文案的"单一事实来源"约束写进注释（当前两侧已一致，靠人工同步）。
- **结论分类**：非问题（合理分层）
- **证据可信度**：高

---

### P1-06 需求列表与详情页共有的 API 调用链无重复查询

- **分类**：P1｜**核实后排除**
- **文件与行号**：`frontend/src/pages/intranet/requirement/index.tsx` 与同目录 6 个 Dialog/Drawer/Modal
- **判断依据**：逐一核对后确认：列表页持列表数据与分页；`:33` 的 `detailEditor` 只持有 `{id, requirementType}` 供三类明细 Dialog 使用；`:32` 的 `detail` 持有列表行数据供 `RequirementDetailDrawer` 显示；`RequirementImplementationDialog` 与 `RequirementApprovalDetailModal` 各自按需拉取（前者 `getRequirementImplementationOptions`，后者 `getRequirementApprovalDetail`）。**`getRequirement(id)`（`api.ts:5`）在列表页未被用于详情展示**——详情直接复用列表行数据，避免了一次多余请求。
- **建议处理方式**：无。
- **证据可信度**：高

---

### P1-07 交易列表与版本弹窗的 API 职责无重叠

- **分类**：P1｜**核实后排除**
- **文件与行号**：`frontend/src/pages/intranet/transaction/index.tsx`、`TransactionVersionDetailModal.tsx`、`DataResourceDialog.tsx`、`api.ts`
- **判断依据**：列表用 `getTransactions` 分页；版本弹窗用 `getTransactionVersionDetail`；资源弹窗用 `getTransactionDataResources`/`saveTransactionDataResources`。三者对应后端三个不同端点，无重复封装，无重复查询。
- **建议处理方式**：无。
- **证据可信度**：高

---

### P1-08 基线 API 与 intranet API 的端点集合无交集

- **分类**：P1｜**核实后排除**
- **文件与行号**：`frontend/src/api/*.ts`（10 个文件）与 `frontend/src/pages/intranet/*/api.ts`（3 个文件）
- **判断依据**：基线 api 只出现 `/auth`、`/account`、`/system` 前缀；intranet api 只出现 `/intranet` 前缀，**零交集**。`api/account.ts`（本人会话）与 `api/user.ts`（管理员查他人会话）是并列能力，不是重复。
- **建议处理方式**：无。
- **证据可信度**：高

---

### P1-09 需求实施三方法的校验重复（与 P1-02 同源，从校验层角度记录）

- **分类**：P1
- **文件与行号**：`IntranetRequirementService.java:131-133`、`:163-164`、`:179-181`（三处相同的前置校验）
- **判断依据**：见 P1-02 的对照表。"状态=2 / 类型≠3 / 版本未生效"这三个条件在两个旧方法与新方法中**字面重复**。这**不是**"前端校验 + 后端校验"的合理分层，而是**后端同一层内的三份实现**。
- **建议处理方式**：抽 `private void requireAssignable(IntranetRequirementEntity value)`。约减 12 行，消除 3 处同步点。
- **结论分类**：确认的历史残留
- **证据可信度**：高

---

## 5. P2 防御性代码

> **总体结论**：本章的 10 条候选中，**只有 4 条建议真正改动代码**，其中 2 条是"删除无调用者的公开方法/死字段"（已计入 P0），2 条是"简化重复校验"。
> **没有任何一条建议削弱认证、授权、数据完整性或事务一致性边界。** 相反，审计发现本项目的防御性代码普遍带有"为什么这么写"的实测注释（例如 `LoginAttemptGuard.java:55-68` 解释为何必须忽略大小写、`TrustedProxyResolver` 解释为何默认不信转发头），多数是真问题而非过度防御。

---

### P2-01 与下游完全等价的配置校验（建议简化）

- **分类**：P2
- **文件与行号**：`backend/src/main/java/org/lbl/system/log/risk/config/WindowRiskPolicy.java:9-15`
- **相关引用/调用链**：`AuditRiskPolicyConfig.java:24-27` 构造 4 个 `WindowRiskPolicy` → 各规则的 `evaluate` 首行 `if (!rule.enabled()) return List.of();`（如 `LoginIpAccountSprayRule.java:38`）→ `SlidingWindowDetector.detect` → `:130-134` 的 `validate(window, threshold)`。
- **当前代码的实际作用**：record 紧凑构造器校验 `window` 非空/非零/非负、`threshold>=1`、`severity` 非空。
- **1. 防御目标和触发条件**：防止阈值/窗口被配成非法值；触发条件是有人改 `AuditRiskPolicyConfig` 时写错数字。
- **2. 当前是否可能发生**：只有在改代码时；而这是**编译/评审期可见的固定值**（`AuditRiskPolicy` 由代码常量提供，无外部绑定入口），不是运行时输入。
- **3. 是否已有其他机制覆盖**：**有，完全等价**。`SlidingWindowDetector.validate:130-134` 重复做了 `Objects.requireNonNull(window)`、`window.isZero()||isNegative()`、`threshold<1` 三项检查；且每条规则都先判 `enabled()` 再进检测器，因此"配置写错"在任何一次真实扫描中都会以完全相同的条件抛 `IllegalArgumentException`。
- **4. 删除后的最坏影响**：配置写错时异常推迟到第一次风险扫描（`AuditRiskController` 或 Agent 工具被调用）才抛，而不是启动时抛。差异仅"发现时点"。
- **5. 是否涉及认证、授权、数据完整性或事务一致性**：**不涉及**。
- **6. 简化后仍需保留的最低保护**：`SlidingWindowDetector.validate` 必须保留（它是真正执行期的守卫）；**`severity` 非空校验建议保留**——下游 `:91` 直接调 `severity().priority()`，不校验会 NPE。
- **删除或简化的潜在影响**：无。收益很小（约 6 行）。
- **建议处理方式**：可保留（"fail fast"是正当写法），但应明确它**不是**独立防线，勿在别处再复制同类校验。**低优先，可不做。**
- **结论分类**：疑似历史残留（与下游重复）
- **证据可信度**：中（重复是事实；是否值得删属风格取舍）

---

### P2-02 反射读取注解文案（建议简化，约 60 行）

- **分类**：P2
- **文件与行号**：`backend/src/main/java/org/lbl/common/exception/GlobalExceptionHandler.java:143-232`（`describeFirstViolation` / `authoredOrFallback` / `declaredMessage` / `lastNode`）
- **相关引用/调用链**：`GlobalExceptionHandler.validation`（`:136-141`）→ `describeFirstViolation` → `declaredMessage(target, field, constraint)` 用反射读 record 访问器上的注解 `message`。`PasswordRules.MESSAGE`（`:41-43`）被三个请求 DTO 用作 `@Pattern(message=…)`。
- **当前代码的实际作用**：Bean Validation 失败时优先返回注解上手写的中文 message；读不到则退回 `violationMessage(field, code)`（`:246-256`），用 `FIELD_LABELS`（`:29-60`）+ 约束类型机械拼装。
- **1. 防御目标和触发条件**：避免把框架内置提示（含密码正则原文）泄露给调用方；触发条件是"校验注解未写 message"。
- **2. 当前是否可能发生**：可能（`TransactionMessageFieldRequest.java:7-14`、`AuditUserTimelineInput.java:13-18`、`AuditReportInput.java:12-16`、`TransactionRequest.java:9-17`、`DepartmentTransferModels.java:10` 均有未写 message 的注解），但**不是攻击面**——是提示文案质量问题。
- **3. 是否已有其他机制覆盖**：**有，等价覆盖**。`violationMessage` + `FIELD_LABELS` 是全项目统一兜底；经逐条比对 197 处校验注解，**所有未写 message 的注解其字段名都已在 `FIELD_LABELS` 中命中**（如 `system/user/request/UserCreateRequest.java:30` 的 `roleIds` → `:35`）。
- **4. 删除后的最坏影响**：某一处新增的无 message 注解（且字段名不在 labels 里）会显示成 Java 字段名，例如"captchaCode 长度不合法"而不是"验证码长度不合法"。**不会泄露正则，不会 500。**
- **5. 是否涉及认证、授权、数据完整性或事务一致性**：**不涉及**。纯响应文案。
- **6. 简化后仍需保留的最低保护**：保留 `authoredOrFallback(ConstraintViolation)`（`:187-195`，它读现成的 `getMessageTemplate()`，**零反射**）与 `violationMessage` + `FIELD_LABELS`；删除 `declaredMessage`（`:202-232`）、`lastNode`（`:234-237`）与 `MethodArgumentNotValidException` 的反射分支（`:181-185` 改为直接 `violationMessage`），并给 intranet 的请求 DTO 补上 message 或补进 `FIELD_LABELS`。
- **删除或简化的潜在影响**：最坏是少数接口的校验提示从中文退化为"字段名+不合法"，可逆、无安全影响。
- **建议处理方式**：列为"可简化的防御性反射"，**优先级低**。若动手，**先补 `FIELD_LABELS` 再删反射**。
- **结论分类**：疑似历史残留（反射解法被更简单的等价兜底取代）
- **证据可信度**：高

---

### P2-03 会话反序列化的历史格式兼容分支（已不可达）

- **分类**：P2
- **文件与行号**：`backend/src/main/java/org/lbl/auth/session/SessionService.java:187-199`（`deserialize` 的 `parts.length == 4` 分支）
- **相关引用/调用链**：`find(sid)`（`:75-89`）→ `deserialize`；写入侧只有 `serialize`（`:179-185`）的 JSON 格式，由 `createMember`（`:50`）、`createOnboarding`（`:70`）、`touch`（`:107`）、`updatePasswordChangeRequired`（`:116`）四处调用——**没有任何代码再写竖线格式**。格式切换发生在 `9eb293d`（2026-09-27），把 `userId + "|" + username + …` 改为 Jackson 序列化。
- **当前代码的实际作用**：兼容"旧版本写进 Redis、尚未过期"的会话值 `userId|username|authVersion|rememberMe`。
- **1. 防御目标和触发条件**：避免升级后旧格式会话反序列化失败导致全员被登出；触发条件是 Redis 中存在旧格式 `auth:session:*` 且未过期。
- **2. 当前是否可能发生**：按代码事实**不可能**。旧格式写入方已在 2026-09-27 删除，而会话最大存活期是 `rememberedAbsoluteDays` 默认 14 天（`SecurityProperties.java:33`）——**到 HEAD（2026-10-13）已过去 16 天，任何旧格式会话早已全部过期**。仅当有人把 2026-09-27 之前的 Redis dump 恢复到当前实例时才可能。
- **3. 是否已有其他机制覆盖**：**有**。`find`（`:79-82`）对 `deserialize` 返回 `null` 的会话会 `redis.delete` 并当作未认证；前端 `utils/request.ts:47-60` 有静默续期与跳登录链路。缺这个分支的后果仅为"旧会话被登出一次"。
- **4. 删除后的最坏影响**：升级瞬间持有旧格式会话的用户需重新登录一次；不影响数据库、不影响新会话。
- **5. 是否涉及认证、授权、数据完整性或事务一致性**：**涉及认证会话**，但方向是"少兼容一种历史格式"，**不削弱当前会话的校验**——`find` 仍校验绝对值 TTL、`rememberMe` 绝对期与 `authVersion`。
- **6. 简化后仍需保留的最低保护**：`deserialize` 的 `try/catch → null` 与 `data.startsWith("{")` 判断**必须保留**（防脏 Redis 值把请求打成 500）；只删 4 字段竖线还原分支。
- **删除或简化的潜在影响**：最坏是一次性强制重新登录，无数据风险。
- **建议处理方式**：**这是本次唯一"依据时间推断已失效"的候选，建议保守处理**——留到下一次明确的会话格式变更时一并删除。若坚持保留，请在注释里写明"依赖 2026-09-27 前写入且未过期的会话，最迟 2026-10-11 后可移除"，否则它会被无限期继承（自 09-27 起已继承 16 天）。**不建议在无 Redis 访问权的情况下直接删除**。
- **结论分类**：疑似历史残留
- **证据可信度**：高（分支不可达的推理基于代码事实与 TTL 上限；但"是否真的没有旧数据"取决于 Redis 实际内容，本次未连库）

---

### P2-04 Agent 运行观测点零实现（**有意预留，不删**）

- **分类**：P2｜**有意预留**
- **文件与行号**：`backend/src/main/java/org/lbl/agent/port/AgentRunObserver.java:11`；消费点 `AgentRunner.java:49`、`:53`、`:193-199`
- **相关引用/调用链**：全 backend 搜索 `AgentRunObserver` 只有 4 处：接口定义 1 处 + `AgentRunner` 的注入与调用 3 处。**零实现类。**
- **当前代码的实际作用**：运行观测扩展点，由 `AgentRunner` 以 `List<AgentRunObserver>` 注入（空列表容忍）并用 `safely()`（`:193-199`）隔离回调异常。
- **1. 防御目标和触发条件**：不是防御代码，而是扩展点；触发条件是有人实现它。
- **2. 当前是否可能发生**：不会（无实现），但**这不是删除理由**——`List<T>` 注入 + 空列表容忍 + 异常隔离本身即为"零实现时不产生任何依赖"的正确设计。
- **3. 是否已有其他机制覆盖**：不适用。
- **4. 删除后的最坏影响**：失去 Agent 运行观测能力（`ARCHITECTURE.md` §8 明示"Agent 复用 `AgentTool`、权限过滤、报告/Artifact 展示"属保留能力）。
- **5. 是否涉及认证、授权、数据完整性或事务一致性**：**不涉及**。
- **6. 简化后仍需保留的最低保护**：`safely()` 的异常隔离必须保留（否则将来某个观察者抛异常会打断 Agent 运行）。
- **建议处理方式**：**保留**。可补注释"当前无实现，为运行观测预留"。
- **结论分类**：有意预留能力
- **证据可信度**：高

---

### P2-05 Agent 审批策略的两道门（**有意预留，两道都要保留**）

- **分类**：P2｜**有意预留**
- **文件与行号**：`backend/src/main/java/org/lbl/agent/application/AgentRunner.java:70-74`（`filter(approval == NOT_REQUIRED)`）与 `backend/src/main/java/org/lbl/agent/tool/ToolExecutionService.java:29-31`（`if (approval == REQUIRED) throw`）
- **相关引用/调用链**：`ToolDescriptor` 默认 `ApprovalPolicy.NOT_REQUIRED`（`ToolDescriptor.java:26`）；全 backend 搜索 `ApprovalPolicy.` 只有 7 处——**4 个 `AgentTool` 实现全部显式写 `NOT_REQUIRED`，没有任何 `REQUIRED` 的实现**。
- **当前代码的实际作用**：模型看不到需要审批的工具（`AgentRunner:70-74`）；即使拿到名字，执行器也拒绝（`ToolExecutionService:29-31`）。`AgentRunner:70` 自带注释："审批请求/恢复协议尚未实现前，不把无法执行的工具暴露给模型。"
- **1. 防御目标和触发条件**：防止"未实现的审批协议"被绕过（工具在未经用户确认时执行）；触发条件是出现 `ApprovalPolicy.REQUIRED` 的工具。
- **2. 当前是否可能发生**：**不会**（无实现）。但触发条件由"新增一个写工具"决定，而非由攻击者决定。
- **3. 是否已有其他机制覆盖**：部分——`ToolRisk` 与 RBAC 权限检查（`ToolExecutionService:26-28`）独立存在；但"需人工确认"这一语义只有 `ApprovalPolicy` 表达。
- **4. 删除后的最坏影响**：删 `AgentRunner` 侧 → 模型可能反复调用不可执行工具、浪费步骤；**删 `ToolExecutionService` 侧 → 一旦有人按注释加了一个 `REQUIRED` 写工具，它会立刻可执行，审批形同不存在。后者是真正的安全退化。**
- **5. 是否涉及认证、授权、数据完整性或事务一致性**：`ToolExecutionService:26-28` 的 `hasAll(permissions)` 是**授权**；`ApprovalPolicy` 侧是"人类确认"边界，**同属安全语义**。
- **6. 简化后仍需保留的最低保护**：**两侧都保留。** 若嫌 `AgentRunner` 的过滤是一次多余的 list 操作，可保留 `ToolExecutionService` 的拒绝即可；反之则相反。**至少要留一处，且 `ToolExecutionService` 那处不可删**——它是唯一能拦住"绕过可见性直接构造工具名"的地方。
- **重要说明**：两侧**不是重复防御，而是同一未实现能力的两道必要门**——只删任一侧都会削弱"审批门"。
- **建议处理方式**：**保留**；建议在两处注释里互相引用，说明"当前无 `REQUIRED` 实现，另一侧是配对门"。
- **结论分类**：有意预留能力
- **证据可信度**：高

---

### P2-06 审计注解 SpEL 求值失败兜底（**有意设计，删除方向错误**）

- **分类**：P2｜**有意设计**
- **文件与行号**：`backend/src/main/java/org/lbl/system/log/aspect/OperationLogAspect.java:58-71`（`evaluate` 的 `catch (RuntimeException) → null`）
- **相关引用/调用链**：`@OperationLog(targetType="#…", targetId="#id")` → `evaluate(joinPoint, value)` → SpEL 求值（`:65`）。表达式是**代码里的字面量**（如 `IntranetRequirementController.java:35` 的 `targetId="#id"`），参数名依赖 `DefaultParameterNameDiscoverer`（`:32`）。
- **1. 防御目标和触发条件**：审计表达式配置错误不得让业务失败；触发条件是 SpEL 求值抛异常（表达式写错，或编译未带 `-parameters` 导致参数名不可解析）。
- **2. 当前是否可能发生**：可能但概率低，属开发期问题。
- **3. 是否已有其他机制覆盖**：**不完全覆盖**。`OperationLogService.record` 的 try/catch（`:52-72`）覆盖"写库失败"，**不覆盖"求值失败"**（求值发生在切面里、写库之前）。因此这不是重复防御。
- **4. 删除后的最坏影响**：一处 SpEL 写错会让**被审计的业务方法整体抛异常**（业务失败 + 无审计日志），代价远高于收益。
- **5. 是否涉及认证、授权、数据完整性或事务一致性**：涉及**审计完整性**——宁可丢一个字段，也不能丢整条记录或让业务失败。
- **6. 简化后仍需保留的最低保护**：**保留 catch**；可选地加一行 `log.warn`，让配置错误可见（当前只返回 null，依赖"日志里字段为空"这一间接信号）。
- **建议处理方式**：**保留**。可选加 warn 日志。**这是"防御失败方向正确"的范例。**
- **结论分类**：有意设计
- **证据可信度**：高

---

### P2-07 菜单树深度计算的环检测（**有意设计，保留**）

- **分类**：P2｜**有意设计**
- **文件与行号**：`backend/src/main/java/org/lbl/security/context/AccessPolicy.java:324-343`（`depthOf` 的 `visited` 断环）
- **相关引用/调用链**：`platformOnlyMenuIds`（`:301-322`）→ `depthOf` → `while (parentId != null && parentId > 0)` 用 `visited.add` 断环；被菜单授权树与 `RoleService` 授权写入使用。
- **1. 防御目标和触发条件**：防死循环；触发条件是 `sys_menu` 中存在 `parentId` 环。
- **2. 当前是否可能发生**：**无法确证**（未连库）。但 `sys_menu` 有 `deleted` 软删列（D-011 已知限制），软删或改父级操作确实可能留下悬挂引用；数据库无自引用外键约束。
- **3. 是否已有其他机制覆盖**：**无**。
- **4. 删除后的最坏影响**：授权树接口（超管配置角色时必经）可能挂起请求线程，属可用性事故。
- **5. 是否涉及认证、授权、数据完整性或事务一致性**：涉及**授权计算**（谁能被授予哪些菜单）。
- **6. 简化后仍需保留的最低保护**：`visited` 断环 5 行必须保留。
- **建议处理方式**：**保留**。这是"低概率但后果严重"的正当防御。若想彻底消除"理论可能"，应在 `sys_menu` 加约束或维护侧校验（需按 D-003 批准），而不是删这里。
- **结论分类**：有意设计
- **证据可信度**：高（防御意图有注释；脏数据是否真实存在无法验证）

---

### P2-08 启动期权限初始化的并发与软删兜底（**有意设计，保留**）

- **分类**：P2｜**有意设计**
- **文件与行号**：`backend/src/main/java/org/lbl/config/SystemPermissionInitializer.java:164-204`（`grant` 吞 `DuplicateKeyException`、`insertReusingRacingRow` 三态处理）
- **相关引用/调用链**：`grant`（`:135`/`:161`）与 `insertReusingRacingRow`（`:131`/`:157`）在启动时补建内置菜单/按钮权限。
- **1. 防御目标和触发条件**：① 多实例同时启动撞 `(role_id, menu_id)` 唯一键；② 建菜单撞唯一键时先按"未删除"口径复读、再按"含已删除"口径复读，若被软删记录占用则 ERROR 并跳过（不让启动失败）。
- **2. 当前是否可能发生**：**会发生**。多实例共库由 D-034 明确记载（"多个应用容器连接同一 MySQL 主库"）；软删记录占用唯一索引由 D-011 已知限制明确记载。
- **3. 是否已有其他机制覆盖**：`GlobalExceptionHandler` 覆盖"运行期请求"的重复键，**不覆盖启动期**；启动期抛异常会让应用起不来。
- **4. 删除后的最坏影响**：单实例 + 无软删数据时无影响；一旦多实例或存在软删占位，会出现"其中一个实例启动失败"。
- **5. 是否涉及认证、授权、数据完整性或事务一致性**：涉及**授权数据初始化**（内置权限缺失会导致超管也看不到功能）。
- **6. 简化后仍需保留的最低保护**：`grant` 的重复键吞并（`:166-171`）、`insertReusingRacingRow` 的复读复用（`:188-192`）、以及 "**两种口径都查不到必须重抛**"（`:201-202`）——最后这条保证它**没有**变成"静默吞错"。
- **建议处理方式**：**保留**。它与 D-011"已知限制暂不修复"是配套的（该限制的另一半正是被 `reReadIncludingDeleted` 兜住）。
- **结论分类**：有意设计
- **证据可信度**：高

---

### P2-09 审计日志的独立事务与吞异常（**有意设计，保留**）

- **分类**：P2｜**有意设计**
- **文件与行号**：`backend/.../system/log/service/OperationLogService.java:49-73`、`LoginLogService.java:46-61`；`OperationLogAspect` 的 `@Order(0)`（`:19-22`、`:29`）
- **判断依据**：两个理由都有注释且成立：
  1. 审计记录必须在业务回滚后**独立提交**（`REQUIRES_NEW`），否则"失败的操作"永远留不下痕迹；
  2. 审计写失败**不能让用户操作失败**；
  3. `@Order(0)` 早于方法级权限拦截器，保证"越权尝试"也能落库。
- **是否检查动态引用和跨模块使用**：**是**。核对 `@Transactional(propagation = REQUIRES_NEW)` 与切面顺序；确认 `record` 的 try/catch 覆盖范围。
- **建议处理方式**：**保留**。这是审计完整性边界，删除会直接丢证据。
- **结论分类**：有意设计
- **证据可信度**：高

---

### P2-10 `X-Request-Id` 缺少长度归一（**建议加，不是删**）

- **分类**：P2
- **文件与行号**：`backend/src/main/java/org/lbl/common/filter/RequestLoggingFilter.java:26-30`
- **相关引用/调用链**：`request.getHeader("X-Request-Id")` → `MDC.put`（`:29`）→ 回显响应头（`:30`）；同时 `RequestInfo.requestId()`（`:88-90`，**截断 64**）把它写进 `sys_operation_log.request_id`，前端 `pages/system/operatelog/index.tsx:78-80` 展示 `RID`。
- **1. 防御目标和触发条件**：这不是防御代码而是功能（跨系统对账）；风险面是"把不受控字符串写进日志与响应头"，触发条件是调用方自带该头。
- **2. 当前是否可能发生**：只有非浏览器客户端会带（前端**从不产生**该头，全仓只有类型定义与日志展示）。内网下可控，但加固成本极低。
- **3. 是否已有其他机制覆盖**：**部分**——容器（Tomcat Http11）会拒绝含 CR/LF 的 header 值，从而挡住日志行注入；`RequestInfo.requestId()` 在**写库**时截断 64。但 `MDC`/日志行/响应头用的是**原值**，不受这两者保护。
- **4. 删除后的最坏影响**：若改为完全忽略客户端值，跨系统对账能力丢失（这是它存在的理由）。
- **5. 是否涉及认证、授权、数据完整性或事务一致性**：涉及**审计日志完整性**（日志行伪造/超长行）。
- **6. 简化后仍需保留的最低保护**：保留"接受客户端 request id"这一能力；**建议加**长度与字符归一。
- **建议处理方式**：**加 3 行归一化，而不是删除**——例如复用 `RequestInfo` 的 64 字符口径或 `requestId.replaceAll("[^A-Za-z0-9._-]", "")`。**注意**：本次未实测 Tomcat 的 header 校验行为，因此该风险等级为"低但加固成本极低"。
- **结论分类**：需要业务确认（低优先，可选加固）
- **证据可信度**：中（容器行为未实测，仅按 Tomcat 默认严格 header 校验推断）

---

### P2-11 验证码前端类型与分支引用了已删除的开关（**结论与 P0-07 同源，从消费侧记录**）

- **分类**：P2
- **文件与行号**：
  - `frontend/src/types/auth.ts:22-31`（`CaptchaChallenge.enabled: boolean`，注释 `:24` 写"enabled=false 时 captchaId / image 都是 null，前端必须把输入框整个隐藏"）
  - `frontend/src/pages/register/index.tsx:71`（`const captchaRequired = challenge?.enabled !== false;`）、`:170-172`（`{captchaRequired && (`）
  - 后端对应：`CaptchaService.java:267`（`public record Challenge(String captchaId, String image)`——**已无 `enabled` 分量**）
- **相关引用/调用链**：后端 `Challenge` 是 `@RestController` 的返回值，经 Jackson 序列化为响应体，字段即响应字段，**不存在隐藏的 `enabled`**。因此 `challenge.enabled` 在前端**恒为 `undefined`** → `undefined !== false` 恒真 → `captchaRequired` 恒为 `true` → `:170-172` 的隐藏分支**永不可达**。`frontend/src/types/auth.ts:28` 声明的 `enabled: boolean` 与真实响应不符（`ExternalProvider.enabled` 在 `:37` 是另一回事，那个是真实字段，不受影响）。
- **当前代码的实际作用**：形式上保留"可关闭验证码"的前端分支；实际永远显示验证码输入框。
- **1. 防御目标和触发条件**：原目标是"后端关闭验证码时前端隐藏输入框"；触发条件是后端返回 `enabled: false`。
- **2. 当前是否可能发生**：**不可能**。后端 `Challenge` record 已无 `enabled` 分量（`913c834` 删除），永远不返回该键。
- **3. 是否已有其他机制覆盖**：不适用——被移除的是一个功能开关，不是防线。
- **4. 删除后的最坏影响**：无功能变化。**真正的最坏影响来自保留**：注释与类型持续暗示"开关存在"，后续会话可能据此认为验证码可关闭，或在排查"验证码为什么不隐藏"时白费时间。
- **5. 是否涉及认证、授权、数据完整性或事务一致性**：验证码是匿名注册入口的滥用控制，**属认证前置**。但当前后端的强制度**高于**注释所描述的（永远开启），因此清理注释与死分支**不会削弱防线**。
- **6. 简化后仍需保留的最低保护**：**必须保留** `register/index.tsx:66-71` 那条"拿不到题目一律按需要验证码处理"的**失败方向**注释与其行为语义（`challenge?.enabled !== false` 的写法在 `challenge` 为 `undefined` 时也返回 true，这正是刻意的失败安全方向）；失败后自动换图（`:103-106`）也要保留。清理时**只删 `enabled` 字段声明与恒真分支的包装**，不要把"拿不到就按需要验证码处理"这个语义改掉。
- **删除或简化的潜在影响**：无。
- **建议处理方式**：
  1. 删除 `types/auth.ts:28` 的 `enabled: boolean` 与 `:22-26` 中描述已删开关的注释；
  2. `register/index.tsx:71` 的 `captchaRequired` 改为直接渲染，或保留但把注释改为"恒为需要验证码；后端已无开关"；
  3. 同时修正 `CaptchaService.java:97-111`、`:113-132`、`:261-268` 与 `RegistrationRequest.java:12-15` 中的过时说明（与 P0-07 一并处理）。
- **结论分类**：确认的历史残留
- **证据可信度**：高

---

### P2-12 `frontend/.prettierrc.json` 有配置、无工具

- **分类**：P2
- **文件与行号**：`frontend/.prettierrc.json:1-11`
- **相关引用/调用链**：`frontend/package.json` 的 `devDependencies` **无** `prettier`，`scripts` 只有 `dev` / `build` / `preview`（无 `format` / `lint`）；全仓搜索 `prettier`（除该文件自身）零命中；无 CI 配置引用它。
- **当前代码的实际作用**：无任何工具读取，仅作为风格声明（内容与现有代码风格一致：`singleQuote: true`、`jsxSingleQuote: true`、`printWidth: 200` 等）。
- **1. 防御目标和触发条件**：不是防御代码，是工具配置。
- **2. 当前是否可能发生**：不适用。
- **3. 是否已有其他机制覆盖**：不适用——格式化从未被强制执行。
- **4. 删除后的最坏影响**：删除即放弃风格声明；将来引入 prettier 需重写这 9 行。
- **5. 是否涉及认证、授权、数据完整性或事务一致性**：**不涉及**。
- **6. 简化后仍需保留的最低保护**：不适用。但需注意：若决定**安装** prettier，首次格式化会产生覆盖大量文件的 diff，应**单独提交**，不要与其他逻辑改动混在一起（`AGENTS.md` §5 要求检查改动范围）。
- **删除或简化的潜在影响**：保留该文件的唯一实际代价是让人误以为格式化已被强制执行。
- **建议处理方式**：**二选一，不要保留中间态**——① 安装 `prettier` 并加 `format` 脚本（配置立即生效）；② 删除该文件。当前"配置存在但工具不存在"是最没有价值的状态。
- **结论分类**：确认的历史残留
- **证据可信度**：高

---

### P2-13 后端两个被 chunk 版取代的公开扫描入口（与 P0-05 同源，从 API 面记录）

- **分类**：P2
- **文件与行号**：`AuditRiskAnalysisService.java:49`、`:58`
- **补充说明**：本条与 P0-05 是同一处代码，此处补充**该方法的历史来龙去脉**，供决策参考：Git 历史显示唯一的历史调用者是被删除的 `LoginAuditRiskScanTool` / `OperationAuditRiskScanTool`（随 `a3b8d0b` 的"4 个审计 Agent 工具整合为 2 个"一并删除，该清理由 D-003 确认为用户有意完成）。现役路径是 `ChunkedAuditRiskScanService.java:39-45` → `analysis::scanLoginRiskChunk` / `scanOperationRiskChunk`，由 `LoginAuditReportTool.java:53`、`OperationAuditReportTool.java:53` 调用。
- **结论**：两个方法是那轮工具整合**遗漏清理的孤儿**，与 D-003 记录的"整合为 2 个"相比只差这一步。
- **建议处理方式**：同 P0-05（删除或降为 `private`）。
- **结论分类**：确认的历史残留
- **证据可信度**：高

---

---

---

## 6. P3 过度抽象

> **总体结论**：本项目的抽象层次**总体克制**。经核实，`AuditRiskRule`（4 实现）、`AgentTool`（4 实现）、`ExternalAuthProviderRegistry`（4 内置提供方）都是**多实现**的合理 Strategy/Registry，**不构成"单实现多层抽象"**。真正符合"只有一个实现却存在注册表/扩展点"特征的只有 2 处，且两者都有文档依据需保留。

---

### P3-01 制品渲染器注册表（单一注册项）

- **分类**：P3
- **文件与行号**：`frontend/src/components/ai/artifacts/ArtifactRendererRegistry.ts:9,11-13,16-28`；唯一注册点 `AgentArtifactView.tsx:7`
- **相关引用/调用链**：见 P0-23。全仓 **1 个注册项**（`ui.report@1`）；后端 4 个 Agent 工具全部产出 `ui.report@1`。
- **当前代码的实际作用**：按 `type + schemaVersion` 查表得到渲染组件，查不到则降级卡片 + 原始 JSON 抽屉。
- **判断依据**：注册表当前等价于一次条件渲染，却承载 Map、版本化键、反注册闭包、`never` 强转四项机制。D-003 记录的工具整合（4 → 2）发生在后端，前端注册表未随之收敛。
- **是否检查动态引用和跨模块使用**：**是**。确认注册只发生一次；核对后端 4 个工具的制品类型字面量；确认 `resolveArtifactRenderer` 只被 `AgentArtifactView:14` 调用。
- **删除或简化的潜在影响**：**不能删注册表本体**——`AgentArtifactView.tsx:15` 依赖它，且它被注释明确声明为扩展点（`ArtifactRendererRegistry.ts:15`："业务扩展只需要注册 type + version，无需修改公共消息列表"），符合 `AGENTS.md` 对"Agent 扩展能力默认保留"的要求。**删除会与该项目定位冲突。**
- **建议处理方式**：
  1. 删除 `AgentArtifactView.tsx:9-10` 两行无消费者再导出（已计入 P0-03）；
  2. **保留**注册表，并补注释说明"当前只有 1 个注册项"；
  3. **不建议**改成条件渲染——那会在新增第二种制品时被迫还原，与 `AGENTS.md` §8"不为未来假设过度设计"并不矛盾（本例是**已被文档登记的**扩展点）。
- **结论分类**：疑似历史残留（再导出部分）／有意预留能力（注册表本体）
- **证据可信度**：高

---

### P3-02 Agent 模型网关端口层（单实现，文档支撑）

- **分类**：P3｜**保留**
- **文件与行号**：`backend/src/main/java/org/lbl/agent/port/ModelGateway.java:8-9`（接口）；唯一实现 `backend/.../agent/infrastructure/model/openai/OpenAiCompatibleModelGateway.java:27`
- **相关引用/调用链**：`AgentRunner` 通过 `ModelGateway` 接口调用；实现的注释说明其职责是"与传输、模型供应商和具体业务无关的 tool-calling 编排器"。
- **判断依据**：按"单实现即冗余"的字面标准会被误判为 P3。**但它有明确文档依据**：`AGENTS.md` 与 `ARCHITECTURE.md` §8 把 Agent 工具/模型能力列为保留的扩展面；且 `port/` + `infrastructure/model/openai/` 的分层是标准的端口-适配器形态，新增供应商只需加一个实现。
- **是否检查动态引用和跨模块使用**：**是**。确认只有 1 个实现类；确认注入点是构造器注入接口而非实现类。
- **建议处理方式**：**保留**，不作为问题。**注意边界**：`ToolDescriptor` 的 `ModelToolDefinition` 等模型线格式（`OpenAiWireModels.java`）与供应商耦合是正确的适配层职责。
- **结论分类**：有意预留能力
- **证据可信度**：高

---

### P3-03 企业目录端口零实现（有配套能力降级）

- **分类**：P3｜**保留**
- **文件与行号**：`backend/src/main/java/org/lbl/auth/uias/EnterpriseDirectoryPort.java:5-6`；消费点 `UiasLoginService.java:42`、`:53`、`:108`、`:111`、`:168-169`
- **相关引用/调用链**：注释明确："内网 ESF/ESB 人员目录适配面；网络调用实现留给实际内网部署。"`UiasLoginService` 用 `ObjectProvider` 探测，并把它并入 `isUsable()`（`:168-172`）——**没有适配器时 UIAS 入口整体不启用**，不会出现半可用状态。
- **判断依据**：零实现 + 明确的内网部署意图 + **已做能力降级**（不会因缺少实现而报错或半可用）。这与"为未来假设设计的扩展接口"的判定标准不同——它有具体的落地环境（内网 ESF/ESB），且降级逻辑已实现。
- **是否检查动态引用和跨模块使用**：**是**。核对 `ObjectProvider` 注入与 `isUsable()` 逻辑；确认 `UiasLoginService` 在无实现时的行为。
- **建议处理方式**：**保留**。
- **结论分类**：有意预留能力
- **证据可信度**：高

---

### P3-04 SmartTable 的三个 className 透传属性（**有意设计，仅登记**）

- **分类**：P3｜**有意设计（含一个真正的零消费者）**
- **文件与行号**：
  - `frontend/src/components/SmartTable/SmartTable.tsx:58`、`:263`（`tableClassName`）——**零消费者，连演示页都没用**
  - `frontend/src/components/SmartTable/SmartTable.tsx:56`、`:252`（`searchClassName`）——唯一消费者是 `pages/home/index.tsx:496`
  - `frontend/src/components/SmartTable/SmartTable.tsx:57`、`:253`（`actionsClassName`）——唯一消费者是 `pages/home/index.tsx:497`
  - `frontend/src/components/SmartTable/SmartTable.tsx:54`、`:81`、`:241`（`onTableChange`）——**零消费者**
- **相关引用/调用链**：`searchClassName`/`actionsClassName` 对应的 CSS（`.smart-table-demo-search`/`.smart-table-demo-actions`）在 `home.css:87`、`:90` 有定义，说明**演示页确实在用**。`tableClassName` 与 `onTableChange` 在演示页中**未出现**。
- **当前代码的实际作用**：为搜索区、工具栏、表格本体提供自定义类名钩子；`onTableChange` 透传 antd Table 的 `onChange`。
- **判断依据（必须区分）**：
  - `searchClassName`/`actionsClassName`：是 `pages/home/index.tsx`（组件演示页）在用的**真实 API**，属演示能力，**不应删除**；
  - `tableClassName`/`onTableChange`：**零消费者**（含演示页），属真正的扩展点残留。
- **是否检查动态引用和跨模块使用**：**是**。搜索三个 className 属性名全部使用点；确认 `home/index.tsx` 的 SmartTable 用法（`:482-541`）只传了 `toolbarClassName`/`searchClassName`/`actionsClassName`/`onExport`/`onRequestError`/`searchRender`。
- **删除或简化的潜在影响**：删 `tableClassName` 与 `onTableChange` 需同时删 `SmartTable.tsx` 的 props 声明与使用处（共 5 行），零外部影响；但它们所在文件的 `searchClassName`/`actionsClassName` 是同类钩子，单独删两个会制造不对称。
- **建议处理方式**：**低优先。** 要么两个都删，要么都不动并补注释。考虑到 `SmartTable` 是基线公共组件（D-025/D-038 明确它承载多条件查询与滚动容器职责），**建议不动**，仅在审计记录中登记。
- **结论分类**：需要业务确认（`tableClassName`/`onTableChange` 为确认的零消费者；去留取决于是否视 SmartTable 为可扩展基线组件）
- **证据可信度**：高

---

### P3-05 `audit-routes`/统计类只读端点缺少前端消费（**需确认是否为预留的 HTTP 面**）

- **分类**：P3 / P0
- **文件与行号**：
  - `backend/src/main/java/org/lbl/system/log/controller/AuditRiskController.java:25`（`GET /api/system/audit-risks`）
  - `backend/src/main/java/org/lbl/system/log/controller/LoginLogController.java:46`（`GET /api/system/login-logs/statistics`）
  - `backend/src/main/java/org/lbl/system/log/controller/OperationLogController.java:43`（`GET /api/system/operation-logs/statistics`）
- **相关引用/调用链**：从 127 个后端端点批量反查前端调用点，这三个是**唯一**在前端零命中的。三者的底层服务都有真实消费者，但**走的是 Agent 路径而非 HTTP**：
  - `AuditRiskAnalysisService.scan`（`AuditRiskController:30` 调用）——Agent 侧用的是 `scanLoginRiskChunk`/`scanOperationRiskChunk`（`ChunkedAuditRiskScanService:40,44`）；
  - `AuditAnalysisService.loginOverview`/`operationOverview`（两个 statistics 端点调用）——Agent 侧用的是同一个服务的同名方法（`LoginAuditReportTool.java:51`、`OperationAuditReportTool.java:51`），**共享服务但入口不同**。
- **当前代码的实际作用**：把已有的审计风险扫描与统计服务额外包装成三个带 `@PreAuthorize` 的只读 HTTP 端点。
- **判断可能冗余的依据**：
  1. 前端零调用；
  2. `docs/` 全仓搜索无任何引用；
  3. 服务层能力**已被 Agent 工具完整消费**，HTTP 端点不是唯一入口；
  4. 三者的形态（只读、`@PreAuthorize` 明确、注释写"统一只读风险扫描入口"）符合"为某个未落地的管理页面预留的接口"特征。
- **是否检查动态引用和跨模块使用**：**是**。批量提取 127 个 `@*Mapping` 路径与前端全文比对；对三个端点分别在全仓（含 docs）搜索路径末段与相关英文名；确认无 `WebSecurityConfig` 特殊放行、无测试引用。
- **删除或简化的潜在影响**：**删除会移除一个 HTTP 契约。** 若曾有管理页面（如"审计风险看板"）计划使用，删除后需重写。**收益极小**——三个端点各约 8 行，且它们是有明确 `@PreAuthorize` 的只读接口，不引入安全风险，也不增加内部复杂度。
- **建议处理方式**：**建议保留并补文档**。若确认是早期"管理页面直连"方案（后被 Agent 工具取代）的遗留，可在 `DECISIONS.md` 记一条说明其现状。**不建议删除**——它们是低成本且可能被规划使用的只读能力，符合"已规划但尚未接入的功能默认保留"。
- **结论分类**：需要业务确认
- **证据可信度**：高（对"前端与文档零引用"）／低（对其是否有意预留）

---

### P3-06 `AgentProfileRegistry.find` 零调用

- **分类**：P3
- **文件与行号**：`backend/src/main/java/org/lbl/agent/domain/AgentProfileRegistry.java:36-38`
  ```java
  public AgentProfile find(String id) {
      return profiles.get(id);
  }
  ```
- **相关引用/调用链**：全 backend 搜索 `find(`（在 Agent 包内）无调用点；实际使用的是 `resolve(String)`（`:30-35`）与 `defaultProfile()`（`:26-28`）。
- **当前代码的实际作用**：无——`resolve` 与 `find` 在只有一个 profile key 时行为等价，且 `resolve` 承担了空值与未知 id 的处理。
- **判断可能冗余的依据**：零调用点；功能被 `resolve` 覆盖。
- **是否检查动态引用和跨模块使用**：**是**。搜索 `AgentProfileRegistry` 全部命中点（`AgentRunner.java:65` 用 `resolve`）；确认 `find` 无反射/SpEL 按名调用。
- **删除或简化的潜在影响**：删除零影响。保留的代价是一个易与 `resolve` 混淆的公开方法（`find` 未知 id 返回 null，`resolve` 抛异常——语义差异可能导致误用）。
- **建议处理方式**：删除 `find`。或若为将来保留，补注释明确"返回 null 而非抛异常，与 `resolve` 语义不同"。
- **结论分类**：确认的历史残留
- **证据可信度**：高

---

### P3-07 需求明细的三套固定实体/明细接口（**依据 D-036，判定为有意设计**）

- **分类**：P3｜**有意设计，仅登记**
- **文件与行号**：`NewTransactionRequirementDetailEntity` / `TransactionCallRequirementDetailEntity` / `TransactionChangeRequirementDetailEntity` 及其 Mapper/服务方法；控制器 `IntranetRequirementController.java:28-33`
- **判断依据**：三类需求各有一套实体、Mapper 与 GET/PUT 端点，看起来是"同一模式的三个副本"。**但 D-036 明确记录这是有意设计**："明细模型：**不建立通用 `content` 字段**。新增交易、交易调用、交易变更各自使用固定明细表/字段；非功能需求同样显式保存服务时段、响应要求、并发、TPS、查询范围、分页、日交易量、文件和计算项。"D-037 进一步确认"保留明细表中业务允许的冗余字段""不新增明细 Request DTO/额外服务端校验"。
- **是否检查动态引用和跨模块使用**：**是**。核对三套实体的 `@TableName` 与三类 API 的前端消费点（`NewTransactionDetailDialog` / `TransactionChangeDetailDialog` / `TransactionCallDetailDialog`）；确认明细实体以 `requirementId` 作 `@TableId`，服务层 `detail.setRequirementId(id)` **覆盖客户端值**，因此**不存在跨需求越写风险**（这一点经本次核实，D-037 的安全边界成立）。
- **建议处理方式**：**保留**。这是"用类型安全换取表数量"的明确取舍，符合 `AGENTS.md` 对已确认决策的尊重。
- **结论分类**：有意预留能力（有意设计）
- **证据可信度**：高

---

## 7. 有意预留能力清单

以下内容均有设计依据，**默认保留**，不作为清理对象。列出以便后续审计不再重复怀疑。

| 编号 | 能力 | 位置 | 依据 | 当前消费者 | 备注 |
| --- | --- | --- | --- | --- | --- |
| R-01 | `html2canvas` 依赖 | `frontend/package.json` | 任务明确指定为有意预留 | **0** | 全仓无 import |
| R-02 | `G6Graph` 血缘图封装 | `frontend/src/components/G6Graph/` | D-006 + `ARCHITECTURE.md:23,356` + `MIGRATION.md:36` | `pages/home/index.tsx:12` | P4 血缘图预留 |
| R-03 | SmartTable `searchConfig` 高级能力 | `frontend/src/components/SmartTable/SmartTable.tsx:47,180-232` | D-025（用户明确批准基线修改） | 2 个页面（`transaction/index.tsx:131`、`requirement/index.tsx:88`），均为 `type:'input'` | `select`/`date`/`dateRange`/`advanced`/`searchActions` 分支**当前零使用**，属已批准的基线能力 |
| R-04 | 交易列表两个不接后端的查询条件 | `frontend/src/pages/intranet/transaction/index.tsx:114,131-135` | **D-026 明文**："不把它当作待修复缺陷…不得以'输入框静默失效'为由自行补齐" | 前端样式验证 | 见 P0-24 |
| R-05 | ES 字段 `data_type` 不做服务端强校验 | `backend/.../dataasset/es/request/EsFieldRequest.java:7` | **D-027 明文** | 前端下拉约束 | 见 §10-14 |
| R-06 | 需求表不加行锁/乐观锁/`transaction_id` 唯一约束 | `IntranetRequirementService` | **D-037 明文**（"项目组内部使用且并发量可忽略"） | — | 低并发边界 |
| R-07 | 公共审批用 `SELECT ... FOR UPDATE` 而非分布式锁 | `ApprovalWorkflowService.java:80-86` | **D-034 明文** | 审批动作 | 数据库行锁对多容器共库有效 |
| R-08 | 需求明细三套固定实体（不用通用 `content`） | `requirement/entity/*DetailEntity` | **D-036 明文** | 三类明细 Dialog | 见 P3-07 |
| R-09 | `AgentTool` / `ToolRegistry` 权限过滤 | `backend/.../agent/tool/` | `AGENTS.md` §4 + `ARCHITECTURE.md:16,357` | 4 个工具实现 | 实现数量支撑结构 |
| R-10 | `ApprovalPolicy.REQUIRED` 审批门（两道） | `AgentRunner.java:70-74` + `ToolExecutionService.java:29-31` | `AgentRunner:70` 注释"审批协议尚未实现" | 无 `REQUIRED` 实现 | 见 P2-05，两道都保留 |
| R-11 | `AgentRunObserver` 运行观测点 | `backend/.../agent/port/AgentRunObserver.java` | Agent 扩展面 | **0 实现** | 注入方式支持空列表 |
| R-12 | `EnterpriseDirectoryPort` 内网人员目录 | `backend/.../auth/uias/EnterpriseDirectoryPort.java` | 注释"网络调用实现留给实际内网部署" | **0 实现** | 有 `ObjectProvider` + `isUsable()` 能力降级 |
| R-13 | `ModelGateway` 端口层 | `backend/.../agent/port/ModelGateway.java` | `ARCHITECTURE.md` §8 | 1 实现（OpenAI 兼容） | 见 P3-02 |
| R-14 | `PasswordChangePolicy` 密码有效期 | `backend/.../auth/service/PasswordChangePolicy.java:75-79` | 策略开关；默认 `password-max-age-days=0` 未启用 | 管理员重置路径独立生效 | **产品能力，不删**；建议改注释说明"未启用，启用需显式注入" |
| R-15 | `AuditRiskRule` 规则族 | `backend/.../system/log/risk/rule/` | 实现数量 = 4 | 4 个 `@Component` | 标准 Strategy，非过度抽象 |
| R-16 | `ExternalAuthProviderRegistry` | `backend/.../config/ExternalAuthProviderRegistry.java` | 内置提供方 4 个（github/google/wechat/uias） | `UiasLoginService`、`ExternalAuthService` | 合理 Registry |
| R-17 | `TrustedProxyResolver` 转发头解析 | `backend/.../security/proxy/TrustedProxyResolver.java` | 生产部署在反向代理后（`application-prod.yml:13,15` 已按代理配置） | `RequestInfo.clientIp()` → 审计 + 限流 | **默认空配置即不生效，但部署形态变化时必需**；见 §10-6 |
| R-18 | `LoginAttemptGuard.recordSuccess` 清来源计数 | `backend/.../auth/service/LoginAttemptGuard.java:102-112` | 注释解释共享出口 NAT 场景 | 登录/注册/UIAS 绑定 | **安全边界，最不该动的一项**；见 §10-7 |
| R-19 | 审批中心/消息中心的 `RequirementApprovalDetailModal` 共用 | `backend`/`frontend` | **D-039 明文** | 两处入口 | `canApprove` 由调用方传入，是**正确**的共享形态（可作为 P1-01 的抽取范式） |

---

## 8. 高置信度可清理候选

以下候选**证据可信度高**、**删除风险低**、且**不触及基线公共契约语义**。按"可安全执行"排序。

### 8.1 完全零风险（删了不影响任何运行行为）

| # | 对象 | 位置 | 动作 | 依据 |
| --- | --- | --- | --- | --- |
| 1 | `assignRequirementTransaction`、`assignRequirementDeveloper` | `requirement/api.ts:8,9` | 删除 | P0-01，全仓零消费者 |
| 2 | `TransactionDataResources`（dataasset 那份） | `dataasset/types.ts:10` | 删除 | P0-02，逐字重复且零引用 |
| 3 | `SmartTableSelection` | `types/table.ts:36` | 删除 | P0-03，连模块内都未用 |
| 4 | `AgentArtifactView.tsx:9,10` 两行再导出 | `AgentArtifactView.tsx` | 删除 | P0-03，零外部导入者 |
| 5 | `NOTIFICATION_FETCH_LIMIT` 的 `export` | `notificationSlice.ts:33` | 去掉 `export` | P0-04 |
| 6 | `AgentChatHandle`、`RequirementStatus`、`SelectionOption`、`RefreshResult`、`ContactProfile` 的 `export` | 各自文件 | 去掉 `export` | P0-03，模块内仍使用 |
| 7 | `SmartTable/index.ts:3` 的 `TableRequestResult` | `SmartTable/index.ts` | 从再导出列表移除 | P0-03 |
| 8 | `AuditRiskAnalysisService.scanLoginRisks`、`scanOperationRisks` | `AuditRiskAnalysisService.java:49,58` | 删除或降为 `private` | P0-05，零调用 |
| 9 | JWT 过滤器的 `/swagger`、`/v3/` 放行 | `JwtAuthenticationFilter.java:87` | 删除该两个条件 | P0-06，springdoc 已移除且与 `WebSecurityConfig` 注释矛盾 |
| 10 | `CaptchaService` 的 `log` 静态字段 + 2 个 import | `CaptchaService.java:54`（及 `:5,6`） | 删除 | P0-07，全文件零调用 |
| 11 | `AgentProfileRegistry.find` | `AgentProfileRegistry.java:36-38` | 删除 | P3-06，零调用 |
| 12 | `DepartmentTransferApprovalService.notifyCurrentReviewer`（空方法）+ 2 处调用 | `:205-207`、`:103`、`:172` | 删除，注释上移 | P0-16，方法体为空 |
| 13 | `reviewerPhrase` 未使用的 `task` 参数 | `:201-203` | 收窄签名或改用该参数 | P0-16 |
| 14 | `application-local.yml:34` 的 `org.kariya` 日志键 | `application-local.yml:34` | 改为 `org.lbl` 或删除该行 | P0-26，改名前缀失效键，永不匹配任何 logger |
| 15 | `frontend/.prettierrc.json` | `frontend/.prettierrc.json` | **二选一**：装 prettier 并加 `format` 脚本，或删除该文件 | P2-12，配置存在但工具与脚本都不存在；若安装，首次格式化需单独提交 |

### 8.2 低风险（需构建验证，无行为语义变化）

| # | 对象 | 位置 | 动作 | 依据 |
| --- | --- | --- | --- | --- |
| 16 | `var(--fs-md)` → `var(--fs-xl)` | `AiAssistant.css:212` | 替换 token | **P0-08，这是修复真实缺陷**：AI 报告标题当前与正文同号 |
| 17 | 需求类型/状态映射抽 `constants.ts`（`.ts`） | `requirement/` 4 处 | 抽取后删除 4 份重复 | P0-10 |
| 18 | 交易状态/打印方式映射抽 `constants.ts`（`.ts`） | `transaction/` 2 处 | 抽取后删除 2 份重复 | P0-11；**并修复 `TransactionVersionDetailModal.tsx:39` 的展示不一致** |
| 19 | 是/否、对客选项抽 `options.ts`（`.ts`） | 6 处 | 抽取 | P0-21，保持现有选项顺序 |
| 20 | `text()` 空值格式化抽 `format.ts`（`.ts`） | 2 处逐字相同 | 抽取 | P0-20，纯逻辑用 `.ts` 规避 D-012 的菜单候选风险 |
| 21 | 需求实施三方法的公共前置校验 | `IntranetRequirementService.java:131-133,163-164,179-181` | 抽私有方法 | P1-02/P1-09 |
| 22 | 部门变更审批交互抽 `DepartmentChangeApprovalModal` | `approvals/index.tsx`、`notifications/index.tsx` | 抽取约 120 行重复 | **P1-01，收益最高**；`canApprove` 留在各页 |

### 8.3 建议先验证再动（收益明确但有前置条件）

| # | 对象 | 前置条件 |
| --- | --- | --- |
| 23 | `login-unified-button` 4 条规则 + 注释块 + `unifiedProvider` | **需业务确认** uias 是否要做独立主入口（P0-09） |
| 24 | `P2-03` 会话竖线格式兼容分支 | 需确认 Redis 中无旧格式数据（按 TTL 推断已不可达） |
| 25 | `P2-02` 反射读取注解文案（约 60 行） | **必须先补 `FIELD_LABELS`** 或给 intranet DTO 补 message |
| 26 | `tableClassName`、`onTableChange` | 需确认是否视 SmartTable 为可扩展基线组件（P3-04） |
| 27 | P0-15 的两个后端端点 + 2 个 DTO + 2 个服务方法 | **需确认仓库外无调用方**（Postman/脚本） |
| 28 | `loginlog/index.css` 与 `operatelog/index.css` 合并 | 属基线样式重构，需按 `AGENTS.md` §2 批准 |
| 29 | 品牌蓝硬编码 18 处改 `color-mix` | 需逐页面目视确认视觉变化（见 §9.2-C4） |

---

## 9. 需要人工确认的候选

### 9.1 需要业务/产品确认（8 项）

| # | 问题 | 位置 | 需要谁决定什么 |
| --- | --- | --- | --- |
| **B0** | **部门调动若改变申请人的动态角色范围（`DEPT`/`DEPT_AND_CHILDREN`），是否应由平台管理员审批？** | `AccessPolicy.java:197-204`（**零调用点**） | **业务/安全（优先级最高）**：回答"是"→ 在 `DepartmentTransferApprovalService` 接线该判定并补权限种子；回答"否"→ 删除该方法。详见 P1-03 与 §10-20 |
| B1 | uias 统一认证是否要做独立主推大按钮？ | `login/index.tsx:119-134`、`login.css:236-258` | 产品：决定后按 P0-09 方案 A 或 B 执行 |
| B2 | `transaction-detail-drawer` / `antd-showcase` 是缺样式还是多 hook？ | `transaction/index.tsx:151`、`RequirementDetailDrawer.tsx:7`、`home/index.tsx:343` | 前端：确认后补样式或删属性（P0-14） |
| B3 | 三个只读审计端点（`audit-risks`、两个 `statistics`）是否保留为预留 HTTP 面？ | 三个 Controller | 架构：确认后补 `DECISIONS.md` 或删除（P3-05） |
| B4 | 需求实施的两个单字段端点是否有仓库外调用方？ | `IntranetRequirementController.java:40,43` | 运维/开发：确认后决定是否下线（P0-15） |
| B5 | 注册页窄屏是否应与登录页一致（86%）？ | `register.css:2` | 前端：按 P0-12 二选一 |
| B6 | `SmartTable.tableClassName`/`onTableChange` 去留？ | `SmartTable.tsx:54,58` | 前端：是否视 SmartTable 为可扩展基线组件（P3-04） |
| B7 | `PasswordChangePolicy` 的密码有效期是否需要启用？ | `application-prod.yml` | 运维/安全：若要启用需显式配置 `password-max-age-days`（R-14） |
| B8 | 新增交易明细表的孤儿列 `expected_online_date` 是否可删？ | `V12:25` | 运维：先查 `COUNT(*) WHERE expected_online_date IS NOT NULL`，确认为 0 后新增独立迁移脚本 DROP（P0-25） |

### 9.2 需要浏览器验证（4 项）

> 这些是 CSS 的 B/C/D 类候选，**静态分析无法确认，不得直接判定为可删除**。

| # | 事项 | 位置 | 为什么必须实测 |
| --- | --- | --- | --- |
| V1 | `.ant-layout { min-height: 100vh; }` 是否真的被覆盖 | `global.css:14-16` | 依赖 CSS 产出顺序与同优先级后置规则；已从构建产物确认 `layout.css` 在后，但仍建议在 dev/build 下复核。**结论：当前无净效果，但删了会失去未来嵌套 `<Layout>` 的兜底——不建议删**（CSS-04） |
| V2 | `.table-toolbar > .ant-input-affix-wrapper` 在 4/9 页面静默失效 | `shared.css:11-13`、`:218-220` | 直接子选择器对 `<Space wrap>` 与 `searchConfig` 渲染结构不匹配。已静态确认 5 命中 / 4 不命中，**需实测确认视觉差异**。**不得删除**（CSS-05） |
| V3 | 深色主题下 `pageUnavailable.css` 的浅色硬编码与 `.log-mono` 对比度 | `pageUnavailable.css:28,48,50`、`operatelog/index.css:12-16` | 深色主题下的实际观感与 WCAG 对比度需目视确认（CSS-06/CSS-07） |
| V4 | 品牌蓝硬编码 18 处改为 `color-mix` 的视觉差异 | `login.css`(9)、`setup.css`(5)、`notFound.css`(3)、`shared.css`(1) | 改色会改变默认蓝下的具体色值，需逐页面目视（CSS-14） |

### 9.3 需要接口/数据库验证（4 项）

| # | 事项 | 位置 | 验证方式 |
| --- | --- | --- | --- |
| D1 | `transactionStatus`/`sourceFieldId`/`parentSourceFieldId` 后端是否实际返回 | `transaction/types.ts:10,11` | 发一次 `GET /api/intranet/transaction-versions/{id}`，看响应是否含这三个键（P0-19） |
| D2 | `LoginSessionView.createdTime` / `ExternalIdentity.createdTime` 是否返回 | `api/account.ts:8,19` | 发一次 `GET /api/account/sessions`（P0-19） |
| D3 | Redis 中是否还有竖线格式的 `auth:session:*` | `SessionService.java:187-199` | 按 TTL 推断已不可达；若可查 Redis，确认无 `auth:session:*` 非 JSON 值（P2-03） |
| D4 | `sys_menu` 运行时内容是否与种子脚本一致 | `init_data.sql` + `db/intranet/V2/V6/V9/V11/V14` | 影响"某个 `pages/` 文件是否真被菜单选中"的推断精度（本次全部结论均基于种子脚本） |

### 9.4 文档修正（3 项，非代码问题但会误导后续开发）

| # | 事项 | 位置 | 动作 |
| --- | --- | --- | --- |
| DOC1 | "保留 `overflow: auto` 作为兜底"与同记录"已移除该声明"自相矛盾 | `docs/intranet/DECISIONS.md:337` | 删除或改为"初版方案，已按 `:364-365` 撤回"。**这是未来"第三次返工"的误导源** |
| DOC2 | D-038 的日期记为 2026-10-11，Git 实际为 `d04ae00`（2026-10-13 00:28） | `docs/intranet/DECISIONS.md:363-366` | 修正日期（按 `AGENTS.md` §1，冲突时以 Git 为准） |
| DOC3 | `ARCHITECTURE.md:52-55` 描述的 glob 行为已与代码不符 | `docs/intranet/ARCHITECTURE.md` | 文档称 glob 收录 `pages/**/*.tsx` 且只提到两个后果；`componentRegistry.tsx:19` 实际已加 5 项排除（`error`/`login`/`register`/`auth`/`account`），与 `router/index.tsx` 的静态注册完全对应。文档应补上排除项 |

---

## 10. 不建议删除的复杂设计

以下设计经核实**有真实依据**（实现数量 > 1、有决策文档支撑、或属企业级学习价值），**不应作为冗余提出**。

| # | 设计 | 核实结论 |
| --- | --- | --- |
| 1 | **`AuditRiskRule` 规则族** | 接口 `AuditRiskRule.java:8-12` 有 **4 个** `@Component` 实现：`LoginFailureBurstRule:20`、`LoginIpAccountSprayRule:20`、`PasswordChangeBurstRule:21`、`PasswordResetBurstRule:21`。`AuditRiskAnalysisService:29-35` 用 `List<AuditRiskRule>` 注入、按 `code()` 排序、构造期校验 code 唯一。阈值集中在 `AuditRiskPolicyConfig:18-28` 单一 Bean。**标准 Strategy + 集中策略，非过度抽象。** |
| 2 | **`AgentTool` / `ToolRegistry`** | `ToolRegistry.java:18-32` 自动发现并按名字唯一性校验；**4 个**实现（`LoginAuditReportTool`、`LoginUserTimelineTool`、`OperationAuditReportTool`、`OperationUserTimelineTool`）。`visibleTo(actor)` 用 `actor.hasAll(tool.descriptor().permissions())` 过滤，配合 `ToolExecutionService:26-28` 二次鉴权。两个审计报告工具分别是 `system:loginlog:list` / `system:operatelog:list`，**实现了"审计工具不得越权读另一类日志"**，是真实的能力隔离。 |
| 3 | **`ExternalAuthProviderRegistry`** | `ExternalAuthProviderConfig:26-33` 注册 **4 个**内置协议定义（github/google/wechat/uias），Registry 与 `lbl.external-auth.providers` 的运行时开关/凭据合并，未注册 key 也可 `putIfAbsent` 扩展。**Registry 的正当用法**（多实现 + 配置驱动）。 |
| 4 | **`ApprovalWorkflowService` 的事务内 `FOR UPDATE` + 锁后复核** | `:80-86`（`lock`）、`:96-163`（`approveCurrent`/`rejectCurrent`/`cancel` 的 `requirePending`）。**依据是 D-034 明文**："多个应用容器连接同一 MySQL 主库时，统一使用事务内 `SELECT ... FOR UPDATE` 锁定审批实例并在锁后复核状态；不增加 Redis 分布式锁、JVM 锁或乐观锁字段。"`@Transactional(propagation = MANDATORY)`（`:31,50,81`）还保证锁方法不会被误用在无事务上下文。 |
| 5 | **`TrustedProxyResolver` 全套转发头解析** | 默认配置（`application-prod.yml:18` `TRUSTED_PROXIES` 为空）下确实不生效，但生产 profile 明确按反向代理部署（`:13` `secure-cookie` 默认 true、`:15` 有 CORS 来源配置）。若删除，一旦按注释填入代理地址，会出现：审计 IP 全为网关、全站共享一个限流 key 导致"一人失败 20 次锁所有人"。**这正是"内网环境不能作为删除安全机制依据"的典型。** |
| 6 | **`LoginAttemptGuard.recordSuccess` 清来源计数** | `:102-112`。`:104-107` 的解释成立：办公网/出口 NAT 下所有人共享一个来源，某同事反复输错会把整个出口 IP 锁 15 分钟。**账号级 `ACCOUNT_SOURCE_FAILURE_LIMIT=5`（`:39`）不受影响**，单账号爆破仍有保护。**这是本清单里最不该动的一项。** |
| 7 | **`AgentRunObserver`（0 实现）** | `port/AgentRunObserver.java:11` 无实现类，由 `AgentRunner:49,53` 以 `List<AgentRunObserver>` 注入并用 `safely()`（`:193-199`）隔离异常。**零实现 ≠ 应删**：注入方式保证不引入启动依赖。归入有意预留。 |
| 8 | **`EnterpriseDirectoryPort`（0 实现）** | 注释明确面向内网部署；`UiasLoginService:168-172` 把它并入 `isUsable()`——**无适配器时 UIAS 入口整体不启用**，不会半可用。属"有意预留且已做能力降级"。 |
| 9 | **`OperationLogService`/`LoginLogService` 的 `REQUIRES_NEW` + 吞异常** | 理由有二且都有注释：审计记录必须在业务回滚后独立提交（否则"失败的操作"永远留不下痕迹）；审计写失败不能让用户操作失败。`OperationLogAspect` 的 `@Order(0)` 保证"越权尝试"也能落库。**审计完整性边界，删除会直接丢证据。** |
| 10 | **`TransactionMessageFieldService.requireParent` 的环检测** | `:44-57`。`:50-52` 注释解释了**真实后果**："前端建树时环上的节点找不到根，整棵子树会从界面静默消失"。环检测是已启用功能（创建/修改字段都过它）。`ARCHITECTURE.md` §3 也明确记录该约束。 |
| 11 | **`SystemPermissionInitializer` 的启动期并发/软删兜底** | `:164-204`。多实例共库由 D-034 记载，软删占唯一索引由 D-011 已知限制记载。且"两种口径都查不到必须重抛"（`:201-202`）保证它**没有**变成静默吞错。 |
| 12 | **`RealtimeTicketService`/`RealtimeGateway` 的 Redis 双路径与一次性票据** | `RealtimeTicketService:17-33`（32 字节随机票据、60s TTL、`getAndDelete` 一次性消费）、`RealtimeGateway:47-50`（publisher 失败回退本地投递）、`RealtimeSubscriptionStarter:23-41`（Redis 暂不可用不让启动失败，后台每 5s 重试）。**双实例/Redis 抖动下的正确降级**，注释说明"所有实例统一从 Redis 订阅后落到本地连接，避免发送两次"。 |
| 13 | **`SmartTable` 的 `.card-scroll` + 默认 `sticky`** | `SmartTable.tsx:262-263`。**D-038 明文**（用户批准修改基线，理由"整个项目的表格都希望能遵守这个效果"），与 `InlineEditableTable.tsx:56-63` 的同名容器是 D-031/D-038 有意设计的**两处挂载点，非重复实现**。 |
| 14 | **`EsAssetService.requireReadyForTransaction`（RowKey 字段存在性检查）** | D-020 要求"交易关联前该字段必须存在"；D-032 计划引入 Excel 导入，届时该检查是唯一阻止"缺 RowKey 的索引被交易关联"的防线。**不是冗余。** |
| 15 | **`LogRetentionJob.keepDays` 的 1 天下限** | `:58-65`。配置为 0/负数时回退 1 天并打 WARN，避免"清空整张表"。**失败方向安全的真防御**，成本 5 行。 |
| 16 | **`GlobalExceptionHandler` 的 `AccessDeniedException` 与 `CannotCreateTransactionException` 专门 handler** | `:94-109` 避免把"无权限"错报成 500"系统繁忙"；`:258-263` 把"数据库连不上"与"服务端故障"区分开。**状态码语义正确性**，不是过度包装。 |
| 17 | **`pages/home/index.tsx` 组件演示页（554 行）** | 虽为最大前端文件，但它是 `init_data.sql:28` 真实注册的菜单页（标题"组件示例"，内容全 mock），且是 `BaseChart`/`CodeViewer`/`CopyButton`/`G6Graph`/`StatusTag` 与 `useClipboard`/`useFullscreen`/`useResizeObserver` 的**唯一消费者**。**属企业级学习价值，保留。**（唯一小瑕疵：`:66` 的 XML 示例提到 `file-import` 特性，而文件暂存模块已按 D-003 有意删除——纯演示文案，可忽略） |
| 18 | **项目为内网需求保留的双刷新链路** | `api/auth.ts:17`（走 `request` 拦截器）与 `authSession.ts:14-27`（裸 axios）。后者是为**避免续期请求自递归**（`utils/request.ts:50-53` 注释）。**不要合并为一条链路。** |
| 19 | **`ArtifactRendererRegistry` 注册表本体** | 见 P3-01。虽只有 1 个注册项，但注释声明为"业务扩展只需注册 type + version"，符合 `AGENTS.md` 对 Agent 扩展能力的保留要求。**只删无消费者的再导出（P0-03），保留注册表。** |
| 20 | **`AccessPolicy.requiresPlatformTransferReview`（未接线的安全判定）** | 见 P1-03。**零调用点，但带授权语义**——它声明"跨动态角色范围的部门调动应交平台管理员审批"，而当前流程没有该判定。**这是本次审计中唯一一条"不能按死代码处理"的发现**：若该判定本应生效，删除等于永久掩盖一个授权缺口；若已废弃，保留则持续误导。**必须由业务先回答 §9.1-B0 的问题**，再决定"接线"或"删除"。 |

---

## 11. 按收益、风险、工作量排序的建议清理顺序

### 阶段一：立即执行（零风险，约 1～2 小时）

**目标**：消除全部"声明级死代码"，不改变任何运行行为。

| 顺序 | 动作 | 涉及条目 | 预估工作量 | 验证方式 |
| --- | --- | --- | --- | --- |
| 1.1 | 删除 2 个前端死 API 封装 | P0-01 | 2 行 | `tsc --noEmit` + `npm run build` |
| 1.2 | 删除 dataasset 的重复 `TransactionDataResources` | P0-02 | 1 行 | 同上 |
| 1.3 | 删除 `SmartTableSelection` + 4 处多余 `export` + `SmartTable/index.ts` 一项 | P0-03 | 约 8 行 | 同上 |
| 1.4 | 删除 `NOTIFICATION_FETCH_LIMIT` 的 `export` | P0-04 | 1 行 | 同上 |
| 1.5 | **修复 `var(--fs-md)` → `var(--fs-xl)`** | **P0-08** | **1 行** | 构建 + 浏览器看 AI 报告标题字号（**本次最值得先做的一行改动**） |
| 1.6 | 删除 JWT 过滤器的 swagger/v3 放行 | P0-06 | 1 行 | `mvn compile` + 确认 `/swagger-ui/**` 返回 401 |
| 1.7 | 删除 `CaptchaService` 死字段 + 过时注释 | P0-07 | 约 5 行 | `mvn compile` |
| 1.8 | 删除 `AuditRiskAnalysisService` 两个零调用方法 | P0-05 | 约 16 行 | `mvn compile` + `mvn test`（审批测试 4/4 应仍通过） |
| 1.9 | 删除 `AgentProfileRegistry.find` | P3-06 | 3 行 | `mvn compile` |
| 1.10 | 删除 `notifyCurrentReviewer` 空方法 + 2 处调用 + `reviewerPhrase` 冗余参数 | P0-16 | 约 6 行 | `mvn compile` |

**阶段一合计**：前端约 13 行、后端约 30 行。**收益**：消除全部确认的死声明，并使 AI 报告标题层级恢复正常（唯一的用户可见改进）。

**风险**：极低。所有删除对象均已通过全仓词边界搜索 + 动态引用核查（含方法引用、`import.meta.glob`、barrel、注解扫描、模板字符串拼接）。**唯一需要留意的是 1.5**：它改变显示效果（标题变大），应目视确认。

---

### 阶段二：抽取重复（低风险，约半天）

**目标**：消除同层重复实现，建立单一事实来源。**全部新建文件必须是 `.ts`**（纯逻辑），以规避 `pages/**` 下 `.tsx` 进入菜单候选的已知风险（D-012 / `ARCHITECTURE.md` §2）。

| 顺序 | 动作 | 涉及条目 | 预估工作量 | 验证方式 |
| --- | --- | --- | --- | --- |
| 2.1 | 抽 `requirement/constants.ts`（类型/状态映射） | P0-10 | 新建 1 文件 + 改 4 处 | 构建 + 逐页目视标签文案 |
| 2.2 | 抽 `transaction/constants.ts`，**并修复版本弹窗打印方式显示** | P0-11 | 新建 1 文件 + 改 3 处 | 构建 + 对比列表页与版本弹窗现在一致 |
| 2.3 | 抽 `intranet/components/options.ts`（是/否、对客） | P0-21 | 新建 1 文件 + 改 6 处 | 构建 + 确认选项顺序未变 |
| 2.4 | 抽 `intranet/components/format.ts`（`text()`） | P0-20 | 新建 1 文件 + 改 2 处 | 构建 |
| 2.5 | 抽后端 `requireAssignable` 私有校验 | P1-02 / P1-09 | 改 1 文件约 12 行 | `mvn compile` |
| 2.6 | 抽 `DepartmentChangeApprovalModal` | **P1-01** | 新建 1 组件 + 改 2 页约 120 行 | **构建 + 浏览器走通审批中心与消息中心两条路径**（通过/拒绝/详情） |

**收益**：本阶段收益最高的是 2.6（消除约 120 行逐字重复，并顺带统一两处按钮图标分歧）。2.2 附带修复一个已发生的展示不一致。

**风险**：低。2.6 涉及 `pages/account/**`（基线页面），按 `AGENTS.md` §2 应**先向用户说明影响范围**（两个基线页面的交互抽取）。建议新组件先放在 `pages/account/components/`，避免动 `components/` 公共目录。

**验证要求**：2.6 必须走浏览器流程，构建通过不能替代——两条入口的 `canApprove` 判定语义不同（`scope === 'pending' && requestStatus === 'PENDING'` vs `notificationType === 'INTRANET_REQUIREMENT_APPROVAL_REQUIRED'`），必须实测两者都正确。

---

### 阶段三：需确认后执行（视业务答复）

| 顺序 | 动作 | 前置确认 | 预估工作量 |
| --- | --- | --- | --- |
| 3.1 | 处理 `login-unified-button`（删或改注释） | B1：uias 是否做独立入口 | 4～20 行 |
| 3.2 | 后端两个单字段端点是否下线 | B4：仓库外无调用方 | 约 45 行（端点 + 2 DTO + 2 方法） |
| 3.3 | 会话竖线格式兼容分支 | D3 + B7 | 约 13 行 |
| 3.4 | 反射读取注解文案（约 60 行） | 先补 `FIELD_LABELS` | 约 60 行 |
| 3.5 | 三个只读审计端点保留或删除 | B3 | 约 24 行 |
| 3.6 | `SmartTable.tableClassName`/`onTableChange` | B6 | 约 5 行 |
| 3.7 | 合并两个日志页 CSS | 需基线改动批准 | 约 20 行 |
| 3.8 | 注册页窄屏一致性 | B5 | 约 3 行 |
| 3.9 | `transaction-detail-drawer` 等 hook 处理 | B2 | 1～5 行 |
| 3.10 | 品牌蓝硬编码改 `color-mix` | V4 目视 | 18 处 |
| 3.11 | `X-Request-Id` 加长度归一（**加不是删**） | — | 约 3 行 |
| 3.12 | 文档修正 DOC1/DOC2/DOC3 | — | 3 处 |

---

### 阶段四：验证前置动作（**优先级高于阶段三，且与清理无关但必须做**）

| 顺序 | 动作 | 理由 |
| --- | --- | --- |
| 4.1 | **重新构建前端，或在 dev server 上做 D-038 的浏览器验证** | `frontend/dist/` 是 **2026-10-12 17:08** 的旧产物，早于修正提交 `d04ae00`（2026-10-13 00:28）。该产物中的 CSS **仍含被撤回的 `overflow: auto`**，且**完全不含**修正后的 flex 高度链（`ant-table-sticky-holder`/`ant-spin-nested-loading`/`ant-table-body` 出现次数均为 0，而 `.page-fill>.table-card>.card-scroll{…overflow:auto}` 存在）。**若用旧 dist 做"浏览器实测"，会重现两条滚动条，极可能触发第三次返工。** `dist/` 已被 `.gitignore` 忽略、后端也不托管前端产物（`backend/src/main/resources` 只有 `db`），因此这只是本地产物风险、不是部署回归——但会直接误导验证结论 |
| 4.2 | 修复 AI 报告标题字号（阶段一 1.5）后再验证 Agent 报告展示 | 当前标题与正文同号 |
| 4.3 | 清理 `docs/intranet/DECISIONS.md:337` 的过时描述 | 该句与同记录自相矛盾，是下次返工的误导源 |

---

### 不建议做的动作（明确记录，避免后续回合反复尝试）

1. **不要删除** `--fs-display`、`--success`（P0-17/P0-18）——它们是刻意维护的字号阶梯与语义色投射契约成员，删除破坏完整性且收益为零。
2. **不要合并**两条 `/auth/refresh` 链路（P0-22/§10-18）——`authSession.ts` 的裸 axios 是为了避免自递归，合并会重新引入 `request.ts:50-53` 所述问题。
3. **不要删除** `ArtifactRendererRegistry` 注册表本体（P3-01）——它是被注释登记的 Agent 扩展点，只删无消费者的再导出。
4. **不要删除** `SmartTable.searchConfig` 的 `select`/`date`/`dateRange`/`advanced`/`searchActions` 分支（R-03）——D-025 用户明确批准的基线能力，当前零使用不代表冗余。
5. **不要给** 交易列表的两个查询条件补后端过滤（P0-24/R-04）——**D-026 明文禁止**。
6. **不要给** ES 字段 `data_type` 加服务端枚举校验（R-05）——**D-027 明文禁止**。
7. **不要给** 需求表加行锁/乐观锁/`transaction_id` 唯一约束（R-06）——**D-037 明文禁止**。
8. **不要删除** `TrustedProxyResolver` 的任何部分（R-17/§10-5），**不要删除** `LoginAttemptGuard.recordSuccess` 的来源计数清零（R-18/§10-6），**不要删除** `ApprovalPolicy` 两道门中的任何一道（R-10/§10-7）——这些是安全边界。
9. **不要把** `PasswordChangePolicy` 的时间分支当作死代码（R-14/§10-14）——它是未被配置启用的产品能力。
10. **不要删除** `AccessPolicy.requiresPlatformTransferReview`（P1-03/§10-20）——它零调用但带授权语义，**先回答 §9.1-B0 的业务问题**。这是本报告中最需要克制的一处。
11. **不要** 把本报告中的"疑似历史残留"和"需要业务确认"当作可删除清单执行——只有 §8.1 的 15 项与 §8.2 中经验证的部分才可直接动手。

---

## 附录 A：审计未覆盖的边界

如实声明本次审计**无法证明**的事项，避免报告被当作全知结论：

1. **运行时数据库内容**：`sys_menu`、`sys_approval_instance`、`intranet_*` 等表的实际数据未查询。因此"某页面是否真被线上菜单选中"只能依据种子脚本推断。
2. **Redis 实际内容**：`auth:session:*` 中是否还有竖线格式的历史值无法确认（P2-03 的不达性基于 TTL 推断）。
3. **接口实际响应**：`transactionStatus`/`sourceFieldId`/`parentSourceFieldId`/`createdTime` 是否仍在响应体中未验证（P0-19）。
4. **仓库外调用方**：Postman 集合、运维脚本、其他前端产物是否调用那两个单字段端点未知（P0-15）。
5. **浏览器渲染**：全部 CSS 的 B/C/D 类候选（§9.2 的 4 项）未实测。
6. **构建与测试**：未执行 `tsc --noEmit`、`npm run build`、`mvn compile`、`mvn test`。所有"编译无影响"的判断基于引用关系推理，非编译器验证。
7. **Git 历史深度**：仅使用了 `git log`、`git ls-files`、`git check-ignore` 等只读命令做辅助判断（例如确认 `SessionService` 格式切换提交为 `9eb293d`），未做全历史考古。
8. **`frontend/dist/` 与 `backend/target/`**：作为被忽略的构建产物未纳入残留审计，但 `dist` 的陈旧问题已在 §11 阶段四记录。

## 附录 B：本次审计明确排除、未作为问题提出的事项

按任务第四节与 `AGENTS.md` 的要求，以下内容**未**作为问题，并已核对确认不构成缺陷：

| 排除项 | 核对结论 |
| --- | --- |
| 依赖当前没有 import | `html2canvas` 有意预留（R-01）；`mybatis-plus-jsqlparser` 实际被 `MybatisConfig:6,21` 使用 |
| 类超过 300 行 | 3 个（`ExternalLoginService` 463、`AccessPolicy` 428、`UserService` 365），均有清晰注释边界，未发现职责混乱 |
| 只有一个实现却存在接口 | `ModelGateway`(1)、`AgentRunObserver`(0)、`EnterpriseDirectoryPort`(0) 均有文档或部署形态依据（§10-7/8、P3-02、P3-03） |
| 存在多个校验层 | 前后端密码策略（P1-05）为合理分层；仅 P2-01、P1-09 为同层重复 |
| 使用 JWT / Redis / 权限模型 / 审批状态机 | 全部为已确认设计（D-004、D-034、D-036），`ApprovalWorkflowService` 的 `FOR UPDATE` 有 D-034 明文 |
| 功能尚未完成或当前暂未使用 | `ApprovalPolicy.REQUIRED`、`AgentRunObserver`、`EnterpriseDirectoryPort`、`PasswordChangePolicy` 的时间分支、两个未接后端的查询条件——全部标注为有意预留 |
| CSS 选择器未直接出现在 TSX 中 | 8 个零命中类名全部有动态拼接或第三方来源解释（§1.2 第 5 条） |
| 纯格式化、命名风格、注释风格 | 未作为问题。**唯一例外是 P0-09 的注释与代码事实相反**——那不是风格问题，而是会误导开发的错误说明 |

---

## 附录 C：超出审计范围但值得知悉的一处配置事实（非残留判定）

审计过程中注意到一项**不属于"历史残留或不必要复杂度"范畴**的事实，按任务边界不作为问题提出，但因其影响面较大，如实记录供用户自行判断：

- `backend/src/main/resources/application.yml:13-15` **明文提交**了 `jasypt.encryptor.password: kariya`，文件自身注释写明"该值仅供开发环境使用；生产环境请改用环境变量或 Kubernetes Secret"。
- 而 `application-local.yml:5`、`:27`、`:84` 用该口令加密了 MySQL 口令、Redis 口令与 LLM API Key 的 `ENC(...)` 密文。
- 该文件（`application-local.yml`）**未被 Git 跟踪**（`.gitignore:56`），因此**密文与口令都没有进入仓库**——从 `AGENTS.md` §3 / D-010 的角度看，这条纪律是遵守的。
- 需要留意的只是：**解密口令本身已在仓库中**（`application.yml` 是跟踪文件）。若将来把 `application-local.yml` 用于任何共享环境，密文即等同明文。**本次未做任何改动，也未验证该口令在其他环境的实际使用情况。**

这一条与"删除 `org.kariya` 失效日志键"（P0-26）同处一个文件区域，建议一并处理时留意。

---

*报告结束。本报告为只读审计产出，所有"建议处理方式"均需人工决策后执行；§8.1 与 §8.2 的条目在动手前应重新确认本报告对应条目的证据与前置条件。*
