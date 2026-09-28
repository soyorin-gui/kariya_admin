# Agent 开发规范与接入指南

本文描述项目中的通用 Agent 基础设施、每个类的职责，以及新增业务工具的标准流程。

当前代码刻意不包含“报文对比、流水号日志分析、投产材料生成”等业务实现。Agent 包只提供跨业务约束；
真正的业务能力必须留在所属业务模块中，最后通过一个很薄的 `adapter.agent` 适配器接入。

---

## 1. 核心原则

1. **业务能力先独立成立，最后接入 Agent。** 业务 Service/UseCase 不得依赖 Agent、Prompt 或模型 DTO。
2. **LLM 只负责理解、选工具和解释。** 查询、校验、计算、权限、写入等确定性行为由 Java 完成。
3. **不调用工具就不代表真实系统状态。** 模型不能声称“已查询、已生成、已修改”。
4. **模型输出永远不可信。** 工具参数必须经过类型绑定、Bean Validation、权限和审批检查。
5. **权限必须完整中介。** 工具是否展示给模型是一层；真正执行前必须再次鉴权。
6. **写操作默认需要批准。** `EFFECTFUL_WRITE` 工具应设置 `ApprovalPolicy.REQUIRED`；在审批协议落地前会拒绝执行。
7. **工具结果最小化。** 给模型的是脱敏事实摘要，完整结构通过 Artifact 给 UI，不把原始大报文塞回上下文。
8. **供应商协议止步于 infrastructure。** application/domain/tool 包不能 import OpenAI、DeepSeek 等线格式 DTO。
9. **网络 IO 不进入数据库事务。** 模型调用和外部系统查询都必须设置超时。
10. **先测试工具，再测试编排，最后做模型评测。** Prompt 不能替代单元测试和权限测试。

---

## 2. 当前运行链路

```text
AiAssistant.tsx
  └─ useAgentChat.ts：POST /api/agent/chat + fetch 读取 SSE
       └─ AgentChatController（api）
            ├─ 校验请求，只接受 user/assistant 历史
            ├─ 在请求线程生成 AgentActor 权限快照
            ├─ 创建 deadline + CancellationToken
            └─ agentExecutor 后台执行
                 └─ AgentRunner（application）
                      ├─ ToolRegistry：筛选当前用户可见工具
                      ├─ ModelGateway：调用模型
                      ├─ ToolExecutionService：二次鉴权、审批、绑定参数、执行
                      └─ AgentEvent：message/tool_call/tool_result/error/done
```

浏览器关闭面板或 SSE 断开后，前端 `AbortController` 与后端 `CancellationToken` 会取消本次运行。

---

## 3. 包结构

```text
org.lbl.agent
├── api/                         HTTP/SSE 输入输出适配
│   ├── AgentChatController
│   └── model/AgentChatRequest
├── application/                 用例编排
│   ├── AgentCommand
│   ├── AgentRequestMapper
│   └── AgentRunner
├── domain/                      与框架、传输、供应商无关的核心模型
│   ├── AgentActor
│   ├── AgentArtifact
│   ├── AgentDefinition
│   ├── AgentEvent
│   ├── AgentExecutionContext
│   ├── AgentMessage
│   ├── AgentRun
│   └── CancellationToken
├── port/                        核心对外依赖的接口
│   ├── ModelGateway
│   ├── AgentRunObserver
│   └── model/Model*
├── tool/                        业务工具的公共契约和统一执行入口
│   ├── AgentTool
│   ├── ToolDescriptor
│   ├── ToolRisk
│   ├── ApprovalPolicy
│   ├── ToolResult
│   ├── ToolRegistry
│   ├── ToolArgumentBinder
│   └── ToolExecutionService
├── infrastructure/              外部技术实现
│   └── model/openai/*
└── config/
    ├── AgentProperties
    ├── AgentDefinitionConfig
    └── AgentAsyncConfig
```

不要重新创建全局 `capability/compare`、`capability/logdiag` 这类包。业务 Agent 适配器跟随业务模块放置。

---

## 4. 每个类负责什么

### 4.1 api

| 类 | 职责 | 禁止承担 |
| --- | --- | --- |
| `AgentChatController` | HTTP、SSE、权限入口、异步任务生命周期、断开取消 | Prompt、工具路由、业务逻辑 |
| `AgentChatRequest` | 浏览器输入白名单和基础校验 | 复用模型供应商 DTO、接收 system/tool 角色 |

### 4.2 application

| 类 | 职责 |
| --- | --- |
| `AgentCommand` | 与 HTTP 无关的一次对话命令 |
| `AgentRequestMapper` | API DTO → 内部命令，并限制历史总长度 |
| `AgentRunner` | 模型与工具之间的循环、最大步数、事件发布、观察者通知 |

`AgentRunner` 不知道某个工具是在查日志、生成文件还是调用业务接口。

### 4.3 domain

| 类 | 职责 |
| --- | --- |
| `AgentActor` | 当前用户身份与权限的不可变快照 |
| `AgentDefinition` | 助手级、与业务无关的系统约束 |
| `AgentMessage` | 内部对话消息，隔离 API 和供应商 DTO |
| `AgentExecutionContext` | runId、用户、总截止时间和取消令牌 |
| `CancellationToken` | SSE 断开、超时、主动取消时的协作式取消 |
| `AgentArtifact<T>` | UI 制品信封，通过 type/schemaVersion 选择渲染器 |
| `AgentEvent` | 传输无关的运行事件 |
| `AgentRun` | 最终文本与工具结果，供测试、审计和后续持久化 |

### 4.4 port

| 类 | 职责 |
| --- | --- |
| `ModelGateway` | 模型供应商端口；更换协议时新增实现，不修改 AgentRunner |
| `ModelMessage/Request/Response` | 供应商无关的模型交互模型 |
| `ModelToolDefinition/ModelToolCall` | 供应商无关的工具声明和调用请求 |
| `AgentRunObserver` | 审计、指标、追踪扩展点；观察者失败不能影响主流程 |

### 4.5 tool

| 类 | 职责 |
| --- | --- |
| `AgentTool<I,O>` | 业务模块接入 Agent 的唯一接口，输入输出必须类型化 |
| `ToolDescriptor` | 名称、说明、JSON Schema、风险、审批、权限和工具超时 |
| `ToolRisk` | `READ_ONLY`、`REVERSIBLE_WRITE`、`EFFECTFUL_WRITE` |
| `ApprovalPolicy` | 是否需要用户确认；当前 `REQUIRED` 一律安全拒绝 |
| `ToolResult<O>` | 给模型的脱敏摘要，以及给程序/UI 的类型化结果和 Artifact |
| `ToolRegistry` | 自动发现工具、校验全局唯一名称、按权限筛选 |
| `ToolArgumentBinder` | JSON → 输入 record，并执行 Bean Validation |
| `ToolExecutionService` | 工具执行唯一入口：二次鉴权、审批、绑定和截止时间 |

### 4.6 infrastructure/config

| 类 | 职责 |
| --- | --- |
| `OpenAiCompatibleModelGateway` | 将通用模型对象转换为 OpenAI Chat Completions 线格式 |
| `OpenAiWireModels` | 只在 OpenAI 适配器内部使用的 JSON DTO |
| `ModelGatewayException` | 屏蔽网关底层异常和敏感响应体 |
| `AgentProperties` | 开关、网关、模型、超时、步数等配置 |
| `AgentDefinitionConfig` | 集中定义助手级安全规则，不放业务 Prompt |
| `AgentAsyncConfig` | 有界线程池；满载时拒绝，绝不回退占用 Tomcat 请求线程 |

---

## 5. 工具开发约定

### 5.1 命名

- 工具名全局唯一，只允许 `^[a-z][a-z0-9_]{0,63}$`。
- 使用“动词 + 对象”，例如 `query_trace_facts`，不要用 `handle`、`process`。
- 一个工具只做一个边界清楚的动作，不要提供“任意 URL”“任意 SQL”“任意命令”工具。

### 5.2 输入

- 用 Java `record` 定义输入。
- 必须加 Jakarta Validation，例如 `@NotBlank`、`@Size`、`@Pattern`。
- JSON Schema 约束模型；Java 类型与 Validation 约束服务端，二者不能互相替代。
- 不允许把 `Map<String,Object>` 传入业务 Service。

### 5.3 权限与审批

- `ToolDescriptor.permissions` 声明工具权限码。
- `ToolRegistry` 只向模型展示有权工具。
- `ToolExecutionService` 在执行前再次检查，防止模型伪造工具名。
- 读取类工具使用最小只读下游凭据。
- 正式写入、发消息、触发任务、删除等必须是 `EFFECTFUL_WRITE + REQUIRED`。

### 5.4 输出

```java
ToolResult.of("给模型的短事实摘要", typedOutput);

ToolResult.artifact(
    "给模型的短事实摘要",
    new AgentArtifact<>("trace-facts", 1, "流水号分析", typedOutput)
);
```

- `modelSummary` 必须短、脱敏、只包含模型作答所需事实。
- 大列表、原始日志、文件内容不要进入模型上下文。
- Artifact 类型使用稳定的短横线名称，并从 `schemaVersion=1` 开始。
- Artifact 结构变化不兼容时提升版本，不偷偷改变旧版本语义。

### 5.5 异常

- 参数不合法抛 `BusinessException`。
- 权限不足抛 `AccessDeniedException`。
- 下游错误在业务基础设施层转换成稳定异常，不把 URL、密钥、响应体返回用户。
- 工具实现必须遵守 `context.deadline()`，调用 HTTP 时把剩余时间转换成客户端超时。
- 长循环和分批任务中调用 `context.checkpoint()`。

---

## 6. 示例：接入“根据流水号分析日志”

这不是让 Agent 包新增 `LogDiagService`。正确结构是：

```text
org.lbl.observability.trace
├── domain/
│   └── TraceFacts.java
├── application/
│   └── TraceQueryUseCase.java
├── port/
│   └── TraceLogRepository.java
├── infrastructure/
│   └── InternalTraceApiClient.java
└── adapter/agent/
    ├── QueryTraceFactsInput.java
    └── QueryTraceFactsTool.java
```

### 第一步：先做与 AI 无关的业务能力

```java
package org.lbl.observability.trace.application;

public interface TraceQueryUseCase {
    TraceFacts query(String serialNo, Long requesterId);
}
```

`TraceFacts` 只保存已经验证的事实，例如是否找到、时间范围、阶段、错误码、脱敏错误摘要。
不要让 LLM 直接读取几万行原始日志。

### 第二步：定义类型化 Agent 输入

```java
package org.lbl.observability.trace.adapter.agent;

public record QueryTraceFactsInput(
        @NotBlank
        @Size(max = 64)
        @Pattern(regexp = "^[A-Za-z0-9_-]+$")
        String serialNo) {
}
```

### 第三步：写一个薄适配器

```java
@Component
public final class QueryTraceFactsTool
        implements AgentTool<QueryTraceFactsInput, TraceFacts> {

    private final TraceQueryUseCase useCase;

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
                "query_trace_facts",
                "按调用方流水号查询已验证的链路事实；仅在用户明确提供流水号并要求排查时调用。",
                TRACE_INPUT_SCHEMA,
                ToolRisk.READ_ONLY,
                ApprovalPolicy.NOT_REQUIRED,
                Set.of("trace:query"),
                Duration.ofSeconds(10));
    }

    @Override
    public Class<QueryTraceFactsInput> inputType() {
        return QueryTraceFactsInput.class;
    }

    @Override
    public ToolResult<TraceFacts> execute(
            QueryTraceFactsInput input,
            AgentExecutionContext context) {
        context.checkpoint();
        TraceFacts facts = useCase.query(input.serialNo(), context.actor().userId());
        return ToolResult.artifact(
                facts.toModelSummary(),
                new AgentArtifact<>("trace-facts", 1, "流水号分析", facts));
    }
}
```

Spring 会自动发现该工具，`ToolRegistry` 会注册它；不需要修改 `AgentRunner`、Controller 或模型适配器。

### 第四步：权限和前端渲染

1. 在 `init_data.sql` 与 `SystemPermissionInitializer` 注册 `trace:query`。
2. 将权限授予需要的角色。
3. 在前端实现 `trace-facts@1` 渲染器；未实现渲染器时仍可显示 `summary`。
4. 严禁仅靠前端 `<Permission>`，后端工具权限才是安全边界。

### 第五步：测试

至少包含：

- 流水号格式校验。
- 查询不到日志。
- 下游超时。
- 脱敏是否生效。
- 无权限用户无法发现和执行工具。
- 工具摘要不包含原始敏感日志。
- Mock `ModelGateway` 后，用户表达应选择 `query_trace_facts`。
- 建立小型评测集：输入、允许工具、期望工具、关键事实，不用全文答案做脆弱比较。

---

## 7. 前端协议

公共类型位于 `frontend/src/types/agent.ts`：

```ts
interface AgentArtifact<T = unknown> {
  type: string;
  schemaVersion: number;
  title?: string;
  data: T;
}
```

前端不得在公共 Agent 类型中声明 `DiffReport`、`TraceFacts` 等业务模型。业务类型和渲染组件应放在对应业务模块。

当前 UI 已支持文本、工具进度和取消请求。后续增加 Artifact 渲染器时，建议使用注册表：

```ts
registerArtifactRenderer('trace-facts', 1, TraceFactsRenderer);
```

不要在 `AiAssistant.tsx` 中写不断增长的 `if (artifact.type === ...)`。

---

## 8. 配置与内网部署

```bash
AI_ENABLED=true
LLM_BASE_URL=https://llm-gateway.example.internal
LLM_API_KEY=通过 Secret 注入
LLM_MODEL=网关提供的模型名
LLM_USER_AGENT=KariyaAdmin-Agent/1.0
LLM_CHAT_PATH=/v1/chat/completions
LLM_STRICT_TOOL_SCHEMA=false
```

- AI 默认关闭，配置完整并验证后再打开。
- 密钥禁止写入 yml、代码、日志或前端。
- 内网自签名证书应导入 JVM trust store，不得关闭 TLS 校验。
- `run-timeout-seconds` 必须小于 `sse-timeout-millis / 1000`。
- 只有网关确认支持 strict tool schema 时才打开 `LLM_STRICT_TOOL_SCHEMA`。
- Nginx 的 `/api/agent/chat` 必须关闭响应缓冲；Controller 已发送 `X-Accel-Buffering: no`。

---

## 9. 上线检查清单

- [ ] `agent:chat:use` 只授予需要的角色
- [ ] 每个工具有独立权限码，且下游使用最小权限身份
- [ ] 所有输入都是 record + Bean Validation
- [ ] 写操作设置审批策略，没有通用 SQL/URL/命令工具
- [ ] 工具摘要和日志均完成脱敏
- [ ] 模型网关、工具、整轮运行三层超时关系正确
- [ ] SSE 断开后后台任务能取消
- [ ] 记录 runId、用户、工具名、耗时、token、结果状态，不记录秘密和完整原文
- [ ] 工具单测、权限测试、编排测试和评测集通过
- [ ] 前端未知 Artifact 能安全降级，不执行 Artifact 中的 HTML/脚本

---

## 10. 当前仍刻意保留的边界

- 当前对话仍由浏览器回传有限历史；需要跨设备会话时再实现服务端 `ConversationRepository`。
- 当前是事件级 SSE，不是 token 级模型流式。
- `ApprovalPolicy.REQUIRED` 当前安全拒绝；实现确认 UI 和一次性批准令牌后才能开放写工具。
- `AgentRunObserver` 已提供接口，但尚未创建 Agent 专属审计表。
- 当前没有任何业务工具，这是去业务化后的预期状态，不是缺失功能。
