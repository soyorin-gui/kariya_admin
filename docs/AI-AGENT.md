# AI 助手（Agent）设计与内网接入指南

> 面向"要把 AI 助手接进公司内网"的开发者。目标：读完能说清楚**架构为什么这么分、代码在哪、
> 怎么新增一个能力、搬到内网怎么配、怎么验证**。
>
> 配套事实：
> - 你的 LLM 是 **OpenAI 兼容协议**（已验证过 base-url / api-key / model 三个参数即可跑通 chat）。
> - 你的内网 **Nginx 会拦截没有（或不认识）User-Agent 的外呼请求**——这是本骨架不选 Spring AI、
>   改用 Spring 6 自带 `RestClient` 手写客户端的关键原因之一（详见第 2 节）。

---

## 1. 核心设计原则（先读这一句）

> **能确定性的，一律不交给 LLM；LLM 只做"理解意图 → 编排工具 → 解释结论"。**

对照你的需求：

| 需求 | 确定性代码（Java）做什么 | LLM 只做什么 |
| --- | --- | --- |
| 双系统对比 | 排序归一化 + 双口径 diff + 差异分类（`Canonicalizer`/`DiffAnalyzer`） | 解释 diff 结论 |
| 流水号日志排查 | 按流水号查日志，抽出"事实包"（有无/行数/关键错误行/归档状态） | 在事实上给原因，且标注"推测" |
| Word 投产材料 | POI 模板占位符填充 + 落盘 | 组织文字、填空 |
| 血缘分析（以后） | 查元数据 + 图遍历 | 解释血缘路径 |

**为什么**：大模型会幻觉、有上下文长度限制、且逐字节 diff 不可复现。排序归一化、字段 diff 是明确算法，
代码比模型快、准、便宜。所以四个能力里"最该先写对"的是各自的**确定性层**，而不是 prompt。

---

## 2. 为什么不用 Spring AI（你已验证过它）

你用 Spring AI 只测了 chat 能通，但这里有三个对它不利、对手写有利的事实：

| 维度 | Spring AI | 本骨架（手写 `RestClient`） |
| --- | --- | --- |
| **User-Agent 控制** | 需要覆盖 `OpenAiApi` 内部或改 `RestClient.Builder`，版本相关、脆弱 | `OpenAiCompatibleLlmClient` 里一行 `defaultHeader(USER_AGENT, ...)`，完全可控 |
| **内网离线 Maven 仓库** | `spring-ai-*` 带一大批传递依赖，版本必须精确匹配（你的 `pom.xml` 已记录过本地仓库版本不全的坑） | **零新增依赖**（`RestClient` 是 Spring 6 自带的） |
| **可读性 / 学习** | tool-calling 循环被框架藏起来 | 循环就在 `AgentService.java`，~130 行，看得见摸得着 |

结论：**骨架阶段手写，吃透原理；将来编排真的复杂到需要 Spring AI 时再迁**（`Tool`/`Capability` 抽象
是框架无关的，迁移只需换 `LlmClient` 一个实现 + `AgentService` 的循环）。

---

## 3. 架构：框架层与能力层分离

```
agent/            ← 框架层（通用、零业务知识，加能力不动它）
capability/       ← 能力层（一个能力一个包，可插拔）
```

```
backend/src/main/java/org/lbl/
├── agent/
│   ├── config/AgentProperties.java          # @ConfigurationProperties("lbl.agent")
│   ├── core/
│   │   ├── Tool.java                        # 工具抽象：name/description/parameters/execute
│   │   ├── Capability.java                  # 能力抽象：id/描述/系统提示/tools/权限
│   │   ├── CapabilityRegistry.java          # 聚合所有能力 → 可见工具清单 + 系统提示
│   │   ├── AgentService.java                # tool-calling 循环（maxSteps 防死循环）
│   │   └── model/                           # AgentModels / ContentKind
│   ├── llm/
│   │   ├── LlmClient.java                   # 接口
│   │   ├── LlmException.java
│   │   ├── OpenAiCompatibleLlmClient.java   # RestClient + 显式 User-Agent
│   │   └── model/OpenAiModels.java          # OpenAI 协议线格式 DTO
│   └── controller/AgentChatController.java  # POST /api/agent/chat（SSE）
│
└── capability/compare/                      # ① 双系统对比（示范能力）
    ├── CompareCapability.java
    ├── tool/CompareSystemsTool.java
    ├── core/ComparisonService.java          # ★ 当前是演示桩，待接真实接口
    ├── core/Canonicalizer.java              # 排序归一化（真实现）
    ├── core/DiffAnalyzer.java               # 差异分类（真实现）
    └── model/CompareModels.java
```

前端：

```
frontend/src/
├── components/ai/
│   ├── AiAssistant.tsx          # 已改造：假回复 → 流式调用
│   ├── AiAssistant.css          # 补 .ai-tool-status
│   └── useAgentChat.ts          # fetch + ReadableStream 解析 SSE
└── types/agent.ts               # AgentStreamEvent / DiffReport / …
```

---

## 4. 一次对话的完整生命周期（SSE）

```
用户点"帮我对比一下这个查询报文"
  │
  ├─ 前端 AiAssistant.send() → useAgentChat.send()
  │      fetch POST /api/agent/chat {message, history}
  │      带 Authorization: Bearer <accessToken>（EventSource 带不了头，所以用 fetch）
  │
  ├─ 后端 AgentChatController.chat()
  │      1) 检查 lbl.agent.enabled
  │      2) access.actor()  → AgentContext{userId, username, superAdmin, permissions}
  │      3) 返回 SseEmitter（text/event-stream）
  │
  ├─ AgentService.run()
  │      messages = [system(能力提示) + history + user(本次消息)]
  │      tools   = CapabilityRegistry.visibleTools(context)   ← 按权限过滤
  │      ┌─ 循环（最多 maxSteps 次）：
  │      │   LLM.complete(model, messages, tools)
  │      │   ├─ 返回 tool_calls？→ 逐条执行工具 → 结果作为 tool 消息回填 → 继续循环
  │      │   └─ 返回 content？    → 结束，这就是最终答复
  │      └─ 每步把事件推给 SseEmitter：tool_call / tool_result / message / error
  │
  └─ 前端逐块读流：tool_call → 显示"正在调用 compare_systems…"；message → 打字机追加文本
```

**关键点**：LLM 只读工具的 `summary`（结构化、短）；前端读 `payload`（完整 diff 明细）。
两者分开，绝不让 LLM 啃原始报文。

---

## 5. 已落地文件清单（本次骨架）

| 层 | 文件 | 状态 |
| --- | --- | --- |
| 配置 | `agent/config/AgentProperties.java` | ✅ 完整 |
| 配置 | `config/AppConfig.java`（已注册 `AgentProperties.class`） | ✅ 已改 |
| 配置 | `application.yml`（`lbl.agent` 块） | ✅ 已改 |
| 框架 | `agent/core/model/ContentKind.java` / `AgentModels.java` | ✅ 完整 |
| 框架 | `agent/core/Tool.java` / `Capability.java` / `CapabilityRegistry.java` | ✅ 完整 |
| 框架 | `agent/core/AgentService.java` | ✅ 完整 |
| 框架 | `agent/llm/LlmClient.java` / `LlmException.java` / `OpenAiCompatibleLlmClient.java` | ✅ 完整 |
| 框架 | `agent/llm/model/OpenAiModels.java` | ✅ 完整 |
| 框架 | `agent/controller/AgentChatController.java` | ✅ 完整 |
| 能力 | `capability/compare/**` | ✅ 完整（`ComparisonService` 是演示桩） |
| 前端 | `components/ai/useAgentChat.ts` / `types/agent.ts` | ✅ 完整 |
| 前端 | `components/ai/AiAssistant.tsx` / `.css` | ✅ 已接线 |

**唯一需要你补的"空实现"**：`capability/compare/core/ComparisonService.java` 里的演示数据，
替换成对你现有"同报文双发对比"接口的真实 HTTP 调用（见第 8.1 节）。

---

## 6. 怎么新增一个能力（以"流水号日志排查"为例）

按 `compare` 能力照抄，四步：

1. 新建包 `capability/logdiag/`，写确定性层：`LogQueryService`（调你"按流水号查日志"接口）
   + `LogFactAnalyzer`（把结果整理成"事实包"：`{serialValid, serialExists, logsFound, logCount, keyErrorLines[], archiveStatus, queriedSystem}`）。
2. 写 `tool/QueryLogsBySerialTool`：入参 `serialNo`，返回 `ToolResult.text(事实摘要)`。
   **注意**：summary 里写清"以下均为事实，无数据时不要臆测原因"，引导 LLM 标注推测。
3. 写 `LogDiagCapability` 实现 `Capability`：`requiredPermissions()` 先留空，硬化时改 `Set.of("agent:logdiag:use")`。
4. 什么都不用改——`CapabilityRegistry` 会自动聚合，重启即生效。

> 四个能力里，**日志排查价值最高、也最容易踩坑**："查无数据"有 5 种可能（流水号格式错 / 不存在 /
> 日志未落 / 已清理 / 查错系统），务必由确定性层尽量枚举成事实包，再让 LLM 下结论。

---

## 7. 内网部署与实现指南（★ 你最关心的部分）

### 7.1 需要设置的环境变量

全部通过环境变量注入（与 `JWT_SECRET` 同一纪律，密钥绝不进仓库/明文 yml）：

```bash
# ── 必填 ────────────────────────────────────────────────
LLM_BASE_URL=https://你的内网模型网关          # 根地址，不含 /chat/completions
LLM_API_KEY=网关发给你的密钥
LLM_MODEL=qwen-max                             # 或 deepseek-chat / 网关定义的别名

# ── 可选（有默认值）─────────────────────────────────────
AI_ENABLED=true                                # false = 关掉 /api/agent/chat
LLM_USER_AGENT=KariyaAdmin-Agent/1.0          # ★ 见 7.2，按 nginx 要求改
LLM_CHAT_PATH=/v1/chat/completions             # 见 7.3
```

`application.yml` 里已有这些占位（`${LLM_BASE_URL:}` 等），生产环境**不需要改任何 yml**，
只要在服务器的启动脚本 / systemd `EnvironmentFile` / 容器 env 里设好即可。

### 7.2 ★ User-Agent 与 Nginx 拦截

你遇到的"Spring AI 会被 nginx 拦"正是 UA 问题。本骨架在
`OpenAiCompatibleLlmClient.java` 里显式设置了 `defaultHeader(USER_AGENT, properties.userAgent())`。

- 默认 UA：`KariyaAdmin-Agent/1.0`（在 `application.yml` 的 `lbl.agent.user-agent`）。
- **如果 nginx 有 UA 白名单**：把 `LLM_USER_AGENT` 改成 nginx 允许的 UA（例如公司约定的
  `YourApp/2.0 (+https://...)` 或一个浏览器 UA）。改环境变量即可，**不用改代码**。
- 若 nginx 是拦"空 UA"，默认值已经足够。

### 7.3 base-url 与 chat-path 怎么填

OpenAI 兼容网关的完整地址有四种常见形态，按你的实际情况填：

| 网关给的完整地址 | base-url | chat-path |
| --- | --- | --- |
| `https://gw/v1/chat/completions` | `https://gw/v1` | `/chat/completions` |
| `https://gw/chat/completions`（无版本号） | `https://gw` | `/chat/completions` |
| `https://gw/v1`（根就到版本号） | `https://gw/v1` | `/chat/completions` |
| `https://api.openai.com/v1/chat/completions` | `https://api.openai.com` | `/v1/chat/completions`（默认） |

**验证命令**（curl 先确认网关通，再启动应用）：

```bash
curl -sS -X POST "${LLM_BASE_URL}${LLM_CHAT_PATH}" \
  -H "Authorization: Bearer ${LLM_API_KEY}" \
  -H "User-Agent: KariyaAdmin-Agent/1.0" \
  -H "Content-Type: application/json" \
  -d '{"model":"'"${LLM_MODEL}"'","messages":[{"role":"user","content":"你好"}]}'
```

能返回带 `choices[0].message.content` 的 JSON 就说明网关、密钥、UA、路径全对。

### 7.4 自签名证书（内网常见）

`SimpleClientHttpRequestFactory` 用 JDK 默认信任库，**不信任内网自签名证书**。若网关是
`https://` + 自签，要么把网关证书导入 JDK 的 cacerts，要么（临时）在
`OpenAiCompatibleLlmClient` 里换成 `JdkClientHttpRequestFactory` 并配 trust store。
建议走"导入证书"，别在生产关校验。

### 7.5 鉴权与安全

- `/api/agent/**` 已被 `WebSecurityConfig` 的 `anyRequest().authenticated()` 覆盖，**必须登录**才能用。
- 骨架阶段未叠加 `@PreAuthorize`（保证开箱即用）；硬化时在 `AgentChatController` 加
  `@PreAuthorize("hasAuthority('agent:chat')")` + 每个能力的 `requiredPermissions()`。
- 密钥走环境变量；**内网 ≠ 可信**（你的 `INTRANET-MIGRATION.md` 第 ① 节就是这个纪律）。

### 7.6 部署后验证清单

- [ ] curl 7.3 的命令返回正常 content
- [ ] 前端 `npm run build` 通过（`tsc --noEmit` 是 build 的一部分）
- [ ] 后端能启动，日志无异常（未配 LLM 时不会启动失败，但对话会提示"未配置"）
- [ ] 登录后点右下角 AI 助手，发一句"帮我对比一下这个报文…"，能看到"正在调用 compare_systems…"
      → 打字机式输出"数据一致、仅排序不一致…"
- [ ] 故意发一句无关问题（如"你好"），模型能直接闲聊、不硬调工具

---

## 8. 硬化清单（上线前逐项做）

| # | 项 | 怎么做 |
| --- | --- | --- |
| 1 | 接入真实对比接口 | `ComparisonService.compare()` 替换演示数据（见 8.1） |
| 2 | 能力级权限 | 每个 `Capability.requiredPermissions()` 从空改 `agent:xxx:use`；并按 `DEVELOPMENT.md` B.3 的"4 处都要改"注册权限码（`init_data.sql` + `SystemPermissionInitializer` + 前端 `<Permission>` + 后端） |
| 3 | 聊天/对比审计 | 复用 `@OperationLog` 或新增 `sys_agent_usage` 表，记录"谁、何时、问了什么、调了哪个工具、花了多少 token" |
| 4 | 评测集 | 攒 20~50 条 golden 用例（"这句话 → 应调哪个工具 → 应给什么结论"），改 prompt 后跑回归 |
| 5 | 结果缓存 | 相同查询的对比结果可缓存（Redis），省钱、降延迟 |
| 6 | 异步化 | 高并发时把 `AgentService.run` 挪进有界线程池（`AgentContext` 已显式捕获，不依赖线程本地） |
| 7 | token 级流式 | 见 8.2 |
| 8 | 文件产出 seam | Word 能力需要"程序生成字节 → 暂存 → 下载"（现有 `StagedFileStorage` 只有上传形，见 8.3） |

### 8.1 接真实对比接口

`ComparisonService.java` 的 `compare()` 现在返回写死的演示数据。真实实现只需：

```java
String solrJson = httpGet(solrHbaseUrl, requestBody);   // 你已有的对比接口
String esJson   = httpGet(esHbaseUrl,   requestBody);
List<JsonNode> solr = parseRecords(solrJson);
List<JsonNode> es   = parseRecords(esJson);
return DiffAnalyzer.diff(solr, es, "业务主键字段名");
```

注意：外部 HTTP 调用要设超时、不要包在 `@Transactional` 里（占 Hikari 连接，全站会被拖死）。

### 8.2 进阶：token 级流式

当前 SSE 是"事件级"流式（工具进度即时可见，最终文本一次性送达）。要做打字机式 token 流式：
在 `LlmClient` 加 `stream()` 方法（请求带 `"stream":true`，读 `data: {...delta...}` 行），
`AgentService` 在"最终答复"那一支改走流式即可；工具调用阶段仍走非流式。这是增量改造，不影响现有结构。

### 8.3 Word 产出的文件 seam

现有 `LocalStagedFileStorage.stage(MultipartFile, ownerId)` 只支持上传。docgen 需要新增一个
`stage(byte[] content, String filename, Long ownerId)` 重载 + 一个下载端点，前端用现成的
`utils/download.ts` 的 `downloadBlob` 拿文件。这是四个能力里**唯一需要动现有 `file/` 包**的地方。

---

## 9. 与项目现有约定对齐（别踩的坑）

1. **鉴权走 `AccessPolicy`**：能力内部如需"按数据范围/越权"判断，注入 `AccessPolicy` 用
   `actor()`，不要自己写第二套权限逻辑（`DEVELOPMENT.md` 附录 B 第 1 条）。
2. **网络 IO 不进事务**：LLM 调用、查日志、调对比接口都是外部网络，别放 `@Transactional` 里。
3. **401/403 语义**：agent 接口 401 = 未登录（前端 fetch 会提示刷新）；不要自造新状态码。
4. **密钥纪律**：`LLM_API_KEY` 走环境变量，和 `JWT_SECRET` 一样。

---

## 附：一页纸速查

```
【配置】
 □ LLM_BASE_URL / LLM_API_KEY / LLM_MODEL 三个环境变量
 □ LLM_USER_AGENT（nginx 白名单则改）
 □ LLM_CHAT_PATH（按网关地址形态，见 7.3）
 □ AI_ENABLED=true

【验证】
 □ curl 网关通（7.3）
 □ npm run build 过
 □ 后端启动无异常
 □ AI 助手对话："正在调用 compare_systems…" → 打字机出结论
 □ 无关问题能闲聊不硬调工具

【上线前硬化】
 □ ComparisonService 接真实接口
 □ 能力权限码 agent:xxx:use（4 处注册）
 □ 审计 + 评测集 + 结果缓存
 □ （需要时）异步线程池 / token 流式 / Word 文件 seam
```
