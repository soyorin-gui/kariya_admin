# 日志审计模块代码导读（analysis / risk / adapter.agent）

> 面向本仓库维护者。目标：读懂"日志审计"这条流水线为什么长成这样、每个类在流水线的哪一环、哪些是必要成本、哪些是可以合并的框架。
>
> 本文只描述仓库中**已存在**的代码事实。写作时点：2026-10-11（删除 4 个 `@Deprecated` 工具之后）。

## 0. 先回答你的疑问："一定需要这么多类吗？"

**先给结论**：这 46 个类里，**约 12 个是"一条流水线拆三层"带来的结构性开销**，其余的类是必要的或不值得合并的。

**为什么会有这么多类，真正的原因不是"过度设计"，而是这三层各自独立成型**：

| 层 | 包 | 文件数 | 它假设的调用者 |
| --- | --- | --- | --- |
| ① 统计层 | `analysis`（+ `mapper`） | 7 | "有人要在管理页面看登录/操作统计" |
| ② 风险层 | `risk` | 17 | "有人要自动发现异常登录" |
| ③ 智能层 | `adapter/agent` | 22 | "有人要用自然语言问审计问题" |

问题在于：**你实际上只有一个调用者（AI 助手）**。所以第 ③ 层的存在是合理的（它就是调用者），但第 ① ② 层为了"将来给页面/接口用"而保留的独立形态，目前**没有被任何非 Agent 的代码消费**：

- 统计层的 `AuditAnalysisService.loginOverview/operationOverview` —— 只被 Agent 工具调用；
- 风险层的 `scanLoginRisks/scanOperationRisks` —— 刚被我们删掉的死工具调用过，现在成了孤儿；
- 风险层的 `scan` —— 只被 `AuditRiskController` 调用，而**该 REST 接口前端零消费者**。

**所以"类多"的根因是**：每个层都按"我可能被多种调用者使用"来设计（所以有 Service + Mapper + 大量 record），但真实调用者只有一种。**这不是坏设计，是"设计得比现在的使用场景更通用"**——而通用性是要用类数量买的。

### 关键判断：这些类能不能合并？能，而且有个自然的合并目标

如果把三层合并成一个"审计助手服务"，大概是这样的规模：

| 现在的实现 | 合并后的等价实现 | 省下 |
| --- | --- | --- |
| `AuditRiskRule` 接口 + 4 个规则类 + `AuditRiskContext` | 1 个类 + 4 个私有方法 | ~120 行 / 6 个文件 |
| `AuditRiskPolicy` + `WindowRiskPolicy` + `AuditRiskPolicyConfig` | 规则类里的常量 | ~70 行 / 3 个文件 |
| `AuditRiskFinding` + `AuditRiskReport` + `AuditRiskSeverity` + `LoginRiskEvent` + `OperationRiskEvent` | 2 个 record | ~40 行 / 3 个文件 |
| `RiskRuleSupport` + `AuditOperationActions` + `SlidingWindowDetector` | 合并进服务私有方法 | ~60 行 / 2 个文件 |

**但我不建议你现在就这么做**，理由在最后一节。先读懂它，再决定。

## 1. 一条流水线：从原始日志到"人话回答"

```
【数据库】sys_login_log / sys_operation_log
     │
     │ ① 取数（SQL）
     ▼
【mapper】LoginLogMapper / OperationLogMapper
     │  统计用：聚合 SQL（SUM/CASE WHEN/GROUP BY）→ 直接返回数字
     │  风险用：明细 SQL（ORDER BY time LIMIT n）→ 返回事件流
     ▼
【analysis / risk】两类计算（互不依赖）
     ├─ 统计：AuditAnalysisService → LoginAuditOverview / OperationAuditOverview
     └─ 风险：AuditRiskAnalysisService → 4 条规则 → AuditRiskFinding 列表
     ▼
【adapter/agent】适配成"模型能看懂 + 前端能渲染"的形状
     ├─ AuditTimeRangeResolver   把"今天/昨天/最近3小时"变成确定的开始/结束时间
     ├─ ChunkedAuditRiskScanService  把"31天"拆成确定性的分片（防模型逐日调用）
     ├─ AuditAgentReportAssembler    把上面结果组装成通用 AgentReport
     └─ 4 个 Tool                     暴露成工具：参数 schema + 权限 + 超时
     ▼
【org.lbl.agent】核心循环调用工具 → 把 modelSummary 交给模型、artifact 交给前端
```

**最重要的理解**：**统计**和**风险**是两条独立的计算路径，共用同一批 mapper。它们**不互相调用**。

- 统计 = 用 SQL 聚合直接算数字（快，数据库干）；
- 风险 = 把明细事件拉到内存，用 Java 算法找模式（因为"滑动窗口内超过 N 次"这种条件 SQL 表达起来很痛苦）。

**这个分工是合理的**，也是理解整个模块的钥匙。

## 2. `analysis` 包（统计层，7 个文件）

**职责**：回答"有多少次、谁最多"这类**确定性数字**问题。不含任何"是否异常"的判断（该判断在 risk 层）。

| 类 | 行数 | 作用 |
| --- | --- | --- |
| `AuditAnalysisService` | 69 | 统计入口。**只做参数校验和查询编排**：校验时间范围（≤31天）、topN（1~50），然后依次调用 mapper 的聚合方法 |
| `model/LoginAuditOverview` | — | 登录统计的完整结果：总计 + 三个排行榜（尝试次数/成功/未成功） |
| `model/LoginAuditTotals` | — | 登录总计：尝试/成功/失败/锁定/账号数/IP数 + 首末时间 |
| `model/LoginUserStatistics` | — | 排行榜的一行（单用户的统计） |
| `model/OperationAuditOverview` | — | 操作统计结果：总计 + 两个排行榜（操作人/高频动作） |
| `model/OperationAuditTotals` | — | 操作总计 |
| `model/OperationUserStatistics` / `OperationActionStatistics` | — | 两种排行榜的行 |

**对应的 SQL 在 mapper 里**（这是这个模块里写得最扎实的部分，值得你学）：

- `LoginLogMapper.summarize()`：一条 SQL 算出 8 个聚合值，用 `SUM(CASE WHEN result='SUCCESS' THEN 1 ELSE 0 END)` 做条件计数——**比在 Java 里循环统计快得多**；
- `USER_STATISTICS` 是一个**被复用 3 次的 SQL 片段**（`topByAttempts` / `topBySuccesses` / `topByRejected` 只是追加不同的 `ORDER BY`/`HAVING`）——**这是正确的复用，不是抽象过度**；
- `findUserTimeline()` 用 MyBatis 的 `<choose>` 做"userId 优先、否则用 username"的条件匹配。

**这一层该不该留？** 该留。它薄（69 行）、职责单一、而且 SQL 无论如何都要写。唯一的问题是它现在的调用者只有 Agent。

## 3. `risk` 包（风险层，17 个文件）

**职责**：回答"有没有可疑模式"。**全部是确定性的**（没有模型参与）——这一点很重要，它保证了结果的**可复现、可审计、可解释**。

### 3.1 核心流程

```
AuditRiskAnalysisService.scanLoginRiskChunk(begin, end)
   │
   ├─ 1. 校验时间范围
   ├─ 2. 查一次明细：loginLogs.findRiskEvents(...)  ← 只查一次，四条规则共享
   ├─ 3. 把结果封进 AuditRiskContext（只读快照）
   ├─ 4. 遍历 4 条规则：rule.evaluate(context) → 各自返回 List<AuditRiskFinding>
   ├─ 5. 统一排序（严重度 → 最后发生时间 → 规则码 → 主体）
   └─ 6. 按 maxFindings 裁剪
```

**第 2 步"只查一次、四条规则共享"是整个风险层最重要的设计**（`AuditRiskContext` 的注释原话："四条规则共享，避免重复查询同一批日志"）。如果每条规则各自查库，4 条规则就是 4 次全表扫描。

### 3.2 四条规则各在找什么

| 规则类 | code | 分组键 | 判定 | 阈值 | 严重度 |
| --- | --- | --- | --- | --- | --- |
| `LoginFailureBurstRule` | `LOGIN_FAILURE_BURST` | 账号（userId 或用户名） | 窗口内**登录失败+锁定**次数 | 10 分钟 ≥ 5 次 | HIGH |
| `LoginIpAccountSprayRule` | `LOGIN_IP_ACCOUNT_SPRAY` | **IP** | 窗口内**不同账号数** | 10 分钟 ≥ 10 个 | HIGH |
| `PasswordChangeBurstRule` | `PASSWORD_CHANGE_BURST` | 账号 | 窗口内"个人中心/修改密码"操作次数 | 24 小时 ≥ 3 次 | MEDIUM |
| `PasswordResetBurstRule` | `PASSWORD_RESET_BURST` | 操作人 | 窗口内"用户管理/重置密码"操作次数 | 1 小时 ≥ 5 次 | HIGH |

**四条规则的代码结构完全一样**（这是好事，说明模式清晰）：过滤事件 → 调 `SlidingWindowDetector` → 把每个命中窗口转成 `AuditRiskFinding`。

`AuditRiskFinding` 的字段值得记住，因为它决定了"审计能不能追溯"：
```
ruleCode/ruleName    哪个规则命中
severity/subjectType/subjectId/subjectName   命中的是谁
summary              一句人话描述
actualValue/threshold  实际值和阈值（"5 次 vs 阈值 5"）
firstSeen/lastSeen   窗口的首末时间
evidenceLogIds       ★ 证据日志 ID 列表 —— 可回查到原始日志行
facts                额外事实（失败次数、涉及 IP 数等）
```

**`evidenceLogIds` 是这个模块最有价值的设计**：风险结论不是"我觉得可疑"，而是"这 7 条日志（ID 可点）触发了规则"。审计场景必须这样。

### 3.3 `SlidingWindowDetector`（144 行，唯一真正的算法）

**这是整个 risk 包里唯一"非它不可"的代码。** 它解决一个具体问题：

> 如果一个账号在 10 分钟内失败了 8 次（阈值 5），**应该生成 1 条风险提示，而不是 4 条**（第5、6、7、8次各一条）。

它用**双指针 + 重叠窗口合并**实现：
- `countIntervals`（`:72-85`）：右指针扩张、左指针收缩保持窗口，命中就记录区间；连续的命中区间会被 `merge` 合并成一个；
- `distinctIntervals`（`:87-104`）：同样的双指针，但维护一个"频次 map"来统计**不同值**的个数（用于"同一 IP 试了多个不同账号"）；
- `merge`（`:117-128`）：相邻重叠的区间合并成一个大区间。

**为什么不能用 SQL 直接做**："窗口内不同账号数 ≥ 10" 用 SQL 需要窗口函数 + 自连接，而且"合并重叠窗口"几乎无法用 SQL 表达。所以这里用 Java 是**正确的技术选择**，不是过度设计。

> 注：这个类的测试（`SlidingWindowDetectorTest`，4 个用例）现在被删了。**这是唯一验证该算法的覆盖**，我建议恢复（见第 6 节）。

### 3.4 哪些是"框架化"（可合并）

| 类 | 行数 | 问题 |
| --- | --- | --- |
| `AuditRiskPolicy` | 38 | 一个有 8 个组件的 record，其中 4 个是 `WindowRiskPolicy`。**它存在的理由是"集中管理阈值"**——这个理由本身成立，但代价是：4 条规则都要靠 `policy.xxxBurst()` 取自己的阈值 |
| `WindowRiskPolicy` | 16 | 包含 `enabled` 字段，但**4 条规则全部 `true`**（`AuditRiskPolicyConfig:24-27`），所以 4 处 `if (!rule.enabled()) return List.of()` 是**死分支** |
| `AuditRiskPolicyConfig` | 29 | 类注释自己说"需要修改时走代码评审和发布"——**也就是说它不是运行时配置**，那它就等价于"把这些常量写在规则类里" |
| `AuditRiskSeverity` | 16 | 4 个枚举值，但 `CRITICAL` 和 `LOW` **从未被赋值**（4 条规则只用 HIGH/MEDIUM）→ `AuditAgentReportAssembler:233,236` 两个分支不可达 |
| `RiskRuleSupport` | 27 | 4 个静态小方法（拼 actorKey、证据裁剪）。**合并进服务类完全可行** |
| `AuditRiskContext` | 20 | 一个只读快照 record。**但它是共享查询的关键**，删了就得把 4 个 List 参数传给每个规则 |

**统一判断**：这一层的"框架化"在于——**它为一个"会有很多规则、需要按需开关"的未来做了设计，但实际只有 4 条规则、全部常开。** 这个未来如果真来了（比如你后续业务要加"交易字段异常变更"规则），框架就有价值；如果不来，它就是 71 行策略层 + 死分支。

## 4. `adapter/agent` 包（智能层，22 个文件）

**职责**：把上面两层的能力**适配**成 Agent 能用的工具。这一层的存在**完全必要**——它就是"业务接入 Agent"的样板，也是你以后做真实业务时要照抄的模板。

### 4.1 四个工具（每个约 50 行，结构完全相同）

| 工具 | 工具名 | 权限码 | 做什么 |
| --- | --- | --- | --- |
| `LoginAuditReportTool` | `login_audit_report` | `system:loginlog:list` | 登录统计 + 风险，一次调用出整份报告 |
| `LoginUserTimelineTool` | `login_user_timeline` | `system:loginlog:list` | 某个用户的登录明细（下钻） |
| `OperationAuditReportTool` | `operation_audit_report` | `system:operatelog:list` | 操作统计 + 风险 |
| `OperationUserTimelineTool` | `operation_user_timeline` | `system:operatelog:list` | 某个用户的操作明细（下钻） |

**注意权限码的分离**：登录审计和操作审计用**不同的权限码**。这意味着**只有登录日志权限的人，其 AI 助手看不到操作审计工具**（`ToolRegistry.visibleTo` 按权限过滤）。这是正确的权限设计，也是"工具必须带权限码"的实际价值。

`LoginAuditReportTool.execute()` 只做四件事（`:46-57`）：
```java
ResolvedAuditRange range = ranges.resolve(input);          // 1. 时间范围（服务端解析）
int topN = input.topN() == null ? policy.defaultTopN() : ...; // 2. 默认值
LoginAuditOverview overview = mode == RISKS ? null : statistics.loginOverview(...); // 3. 按 mode 决定查什么
ChunkedRiskScanResult riskResult = mode == STATISTICS ? null : risks.scanLogin(...);
return ToolResult.artifact(summary, artifact);              // 4. 包装两个出口
```
**`mode` 参数（STATISTICS/RISKS/FULL）是关键设计**：它让模型用**一次调用**指定"只要统计/只要风险/都要"，而不是让模型自己组合多次调用。

### 4.2 三个支撑服务

| 类 | 行数 | 为什么存在 | 必要性 |
| --- | --- | --- | --- |
| `AuditTimeRangeResolver` | 59 | 把"今天/昨天/最近N小时/CUSTOM"**在服务端**按 Asia/Shanghai 解析成确定的起止时间 | **必要**。若让模型自己算日期，会因时区、跨月、闰年出错；system 指令第 10 条明确禁止模型自行换算 |
| `ChunkedAuditRiskScanService` | 137 | 把"31 天"确定性拆成 24 小时分片，每片向前回看最大规则窗口，再按 `lastSeen` 归属分片并合并重叠命中 | **必要**，但实现可优化（见下） |
| `AuditAgentReportAssembler` | 253 | 把 `LoginAuditOverview` / `AuditRiskReport` 组装成通用的 `AgentReport`（指标/表格/发现/时间线四种区块） | **必要**。它是"审计领域对象"与"通用 UI 报告"之间的唯一转换点。**行数最多，但都是直白的字段映射** |

**最值得你读懂的是两个"出口"的区别**（在 `AuditAgentReportAssembler` 里体现得最清楚）：

```
loginModelSummary(...)  → String  → 给【模型】看的最小事实集（几百字纯文本）
login(...)              → AgentReport → 给【前端】渲染的完整结构（指标+表格+发现）
```

为什么必须分开？**如果把完整报表塞给模型，会撑爆上下文；如果只给前端不给模型，模型就没法基于事实回答问题。** 这是 Agent 开发的核心设计模式，你以后每个工具都要做这个区分（见 `AGENT-GUIDE.md` 第 3.5 节）。

**`ChunkedAuditRiskScanService` 里的一个真实过度设计**（我之前分析过，现在你有了上下文应该能看懂）：

```
第 1 片：查 [day1 00:00, day1 24:00)
第 2 片：查 [day1 24:00 - 24h, day2 24:00)   ← 回看了整整 24 小时！
第 3 片：查 [day2 24:00 - 24h, day3 24:00)   ← 又回看 24 小时
...
```

`queryBegin = segmentStart.minus(riskPolicy.maxRuleWindow())`（`:57`），而 `maxRuleWindow()` 当前 = `PasswordChangeBurstRule` 的 **24 小时**。所以 31 天的报告实际扫描约 **1464 小时的数据，是区间本身的 2 倍**。

**回看本身是必要的**——因为一个"24 小时窗口内 3 次改密"的模式可能跨午夜，只查当天会漏判。**但回看整个 24 小时窗口是过度的**：只需要回看"上一个分片的尾部"（即窗口大小减去分片长度）就能覆盖跨边界情况，当前实现等于把整段重查一遍。

### 4.3 model 包（10 个 record，约 130 行）

这些是工具的输入/输出类型。值得注意的两个设计：

- **`AuditToolSchemas` 与 model 的校验重复**：`AuditToolSchemas:29` 写 `topN` 范围 1-50，`AuditReportInput:16` 也写 `@Min(1) @Max(50)`。**Schema 约束模型（它"看到"的规则），注解约束服务端（真实执行的规则）**，两者都必须存在，但**当前没有测试断言它们一致**。你以后加工具时会重复这个模式，**改一处忘另一处的 bug 很难查**。
- **`AuditAgentPolicy` / `AuditRiskPolicy` 都是纯 Java 配置**（不写 YAML）。这是有意的选择，注释写明"走代码评审，不占用 YAML"。**对单人项目这是对的**（少一层配置管理），但要知道代价：改阈值必须重新发布。

## 5. 一页速查：每个类在流水线的哪一环

| 你看到的问题 | 该去哪个包 | 具体类 |
| --- | --- | --- |
| "统计数字不对" | `analysis` | `AuditAnalysisService` + `mapper/*Mapper.summarize` |
| "排行榜少了/多了" | `analysis` | `mapper/USER_STATISTICS` 片段 + `topByXxx` |
| "风险规则没命中" | `risk/rule` | 对应的 `XxxBurstRule`，先看过滤条件再看阈值 |
| "风险命中太多次/重复" | `risk/support` | `SlidingWindowDetector` 的窗口合并逻辑 |
| "风险阈值要改" | `risk/config` | `AuditRiskPolicyConfig` |
| "31 天报告太慢" | `adapter/agent` | `ChunkedAuditRiskScanService` 的 `queryBegin` 回看 |
| "'今天'算错了" | `adapter/agent` | `AuditTimeRangeResolver` + `AuditAgentConfig` 的时区 |
| "AI 回答里数字不对" | `adapter/agent` | `AuditAgentReportAssembler.xxxModelSummary` |
| "前端报告要加一列" | `adapter/agent` | `AuditAgentReportAssembler.addXxxStatistics` 的 `AgentTableColumn` |
| "AI 说它做不到某事" | `adapter/agent` + `org.lbl.agent` | 工具是否注册 + profile 白名单 + 权限码 |

## 6. 我的建议（哪些动、哪些留）

### 建议保留（不要动）

1. **`analysis` 整个包 + mapper 的统计 SQL** —— 薄、正确、SQL 质量好；这是你以后做真实业务统计的模板；
2. **`SlidingWindowDetector`** —— 唯一真正的算法，SQL 无法替代；**并且建议恢复它的测试**（4 个用例，是验证该算法的唯一覆盖）；
3. **4 条风险规则本身** —— 它们是你"提前生成出来做功能测试"的产物，逻辑合理、阈值可调；即使当前场景不需要，作为"确定性风控"的样例有参考价值；
4. **4 个 Agent 工具 + `AuditTimeRangeResolver` + `AuditAgentReportAssembler`** —— 这是你未来接入真实业务时**最该照抄的样板**（工具结构、权限码分离、两个出口的区分）；
5. **`AuditAgentPolicy` / `AuditRiskPolicy` 的集中配置形态** —— 4 条规则的阈值集中看一处，比散落在 4 个类里好维护。

### 建议简化（等你决定这个模块的去留后一起做）

| 项 | 收益 | 代价 |
| --- | --- | --- |
| 删 `WindowRiskPolicy.enabled` + 4 处死守卫（全为 true） | 少量代码 + 消除不可达分支 | 失去"单条规则开关"能力（当前不存在） |
| 删 `AuditRiskSeverity.CRITICAL`/`LOW` + 2 个不可达分支 | 消除误导（看起来支持 4 级，实际只用 2 级） | 未来加规则时要重新加枚举 |
| 删 `AuditRiskAnalysisService.scanLoginRisks/scanOperationRisks` | 2 个孤儿方法（原调用者已删） | 无（`scanLoginRiskChunk` 覆盖了它） |
| 删 `AuditRiskController`（`/api/system/audit-risks`）+ `AuditRiskAnalysisService.scan()` | 前端零消费者的 REST 壳 | 失去"手工调试风险的 HTTP 入口" |
| 修 `ChunkedAuditRiskScanService` 的 24h 回看 | 31 天报告查询量减半 | 需要重新验证跨午夜窗口不漏判（**有 1 个测试覆盖，但那个测试现在被删了**） |

### 为什么我建议**先不要**把三层合并成一团

合并能省约 290 行 / 14 个文件，看起来很划算。但有三条反对理由：

1. **分层的边界是对的**。统计/风险/适配的划分在概念上成立，不是硬凑的层。合并后一个大类会同时做 SQL 编排、算法、格式化和 Tool 适配，**改任何一个都要读懂全部**；
2. **你已经有一份真实业务要接 Agent**。那时 `adapter/agent` 的样板价值立刻兑现——照抄一个工具比从零设计快得多；
3. **合并的收益是"少 14 个文件"，成本是"重新验证一遍"**。而验证手段（测试）刚刚被删了 4 个文件，你现在**没有安全网**去做这个重构。

**我建议的顺序**：
```
① 先决定：这个日志审计功能，你要长期保留，还是只当学习样本？
   ├─ 长期保留 → 按上面"建议简化"逐条做，别动分层
   └─ 只当样本 → 冻结（不删不修），把精力放到真实业务
② 无论哪条，先恢复 2 个测试：SlidingWindowDetectorTest（算法）+ AuditRiskRulesTest（4 条规则）
③ 真正要动的，等你在真实业务里写过第二个 Agent 工具之后再做——那时你会知道
   "共性"到底在哪，而不是现在猜
```

## 7. 你以后做真实业务时，这套代码哪些能直接抄

假设你后续要在**别的业务域**（比如交易资产、数据资产）接 AI 助手，可以直接照搬的模式：

| 可以照抄 | 对应样例 |
| --- | --- |
| 一个业务域建一个 `adapter/agent` 包 | `system/log/adapter/agent/` |
| 每个工具约 50 行：静态 `ToolDescriptor` + 三个方法 | `LoginAuditReportTool`（59 行，最完整） |
| 权限码与页面/接口保持一致，工具之间按权限分离 | `LoginAuditReportTool` 用 `system:loginlog:list` |
| **参数边界写两处**：JSON Schema + Bean Validation | `AuditToolSchemas` + `AuditReportInput` |
| **"意图级"工具而非细粒度工具**（避免模型自由组合、无法预测查询次数） | `login_audit_report` 的 `mode` 参数 |
| **服务端解析自然语言时间** | `AuditTimeRangeResolver` |
| **两个出口**：`modelSummary`（给模型）+ `artifact`（给前端） | `AuditAgentReportAssembler` |
| **证据可回查**：结论里带原始记录 ID | `AuditRiskFinding.evidenceLogIds` |
| 阈值集中一处 | `AuditAgentConfig` / `AuditRiskPolicyConfig` |

| 不要照抄 | 原因 |
| --- | --- |
| 4 条规则的"接口 + 策略 + 注册"框架 | 除非你确信会有很多规则且需要按需开关 |
| `ChunkedAuditRiskScanService` 的 24h 回看 | 直接实现成"只回看窗口与分片的差值" |
| `enabled` 之类的开关字段（如果全部为 true） | 死分支比缺功能更难排查 |
