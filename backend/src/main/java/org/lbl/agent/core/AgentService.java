package org.lbl.agent.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.lbl.agent.config.AgentProperties;
import org.lbl.agent.core.model.AgentModels.AgentContext;
import org.lbl.agent.core.model.AgentModels.AgentEvent;
import org.lbl.agent.core.model.AgentModels.AgentRun;
import org.lbl.agent.core.model.AgentModels.ChatRequest;
import org.lbl.agent.core.model.AgentModels.ToolResult;
import org.lbl.agent.llm.LlmClient;
import org.lbl.agent.llm.LlmException;
import org.lbl.agent.llm.model.OpenAiModels.ChatCompletionRequest;
import org.lbl.agent.llm.model.OpenAiModels.ChatCompletionResponse;
import org.lbl.agent.llm.model.OpenAiModels.ChatMessage;
import org.lbl.agent.llm.model.OpenAiModels.FunctionSpec;
import org.lbl.agent.llm.model.OpenAiModels.ToolCall;
import org.lbl.agent.llm.model.OpenAiModels.ToolSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Agent 核心：一条最小、可读、可观测的 tool-calling 循环（ReAct 的 function-calling 变体）。
 * <p>
 * 循环逻辑：把"系统提示 + 历史 + 用户消息 + 可见工具清单"发给模型 →
 * 若模型要求调工具，就执行工具、把结果作为 tool 消息回填，再问模型 → 直到模型给出最终文本
 * 或达到 maxSteps 上限。每一步通过 {@code sink} 把进度事件推给上层（SSE）。
 * <p>
 * 注意：本循环<b>不做网络 IO 之外的事</b>，也<b>不占事务</b>（LLM 调用慢且贵），
 * 与项目里"事务里不放网络 IO"（ExternalLoginPersistence）是同一纪律。
 */
@Service
public class AgentService {
    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final LlmClient llm;
    private final CapabilityRegistry capabilities;
    private final AgentProperties properties;
    private final ObjectMapper mapper;

    public AgentService(LlmClient llm, CapabilityRegistry capabilities, AgentProperties properties, ObjectMapper mapper) {
        this.llm = llm;
        this.capabilities = capabilities;
        this.properties = properties;
        this.mapper = mapper;
    }

    public AgentRun run(ChatRequest request, AgentContext context, Consumer<AgentEvent> sink) {
        List<Tool> visibleTools = capabilities.visibleTools(context);
        String systemPrompt = capabilities.systemPrompt(context);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(systemPrompt));
        if (request.history() != null) messages.addAll(request.history());
        messages.add(ChatMessage.user(request.message()));

        List<ToolResult> results = new ArrayList<>();

        for (int step = 0; step < properties.maxSteps(); step++) {
            ChatCompletionRequest completion = new ChatCompletionRequest(
                    properties.model(),
                    messages,
                    toToolSpecs(visibleTools),
                    visibleTools.isEmpty() ? null : "auto",
                    properties.temperature());

            ChatCompletionResponse response = llm.complete(completion);
            if (response == null || response.choices() == null || response.choices().isEmpty()
                    || response.choices().get(0).message() == null) {
                throw new LlmException("模型返回了空结果，请稍后重试");
            }

            ChatCompletionResponse.Message message = response.choices().get(0).message();

            // 模型直接给出最终答复（没有要调用的工具）→ 结束。
            if (message.toolCalls() == null || message.toolCalls().isEmpty()) {
                String text = message.content() == null ? "" : message.content();
                sink.accept(AgentEvent.message(text));
                return new AgentRun(text, results);
            }

            // 模型要求调工具：先把 assistant 消息（含 tool_calls）回填，再逐条执行工具。
            messages.add(ChatMessage.assistant(message.content(), message.toolCalls()));
            for (ToolCall call : message.toolCalls()) {
                Tool tool = capabilities.toolByName(call.function().name());
                if (tool == null) {
                    messages.add(ChatMessage.tool(call.id(), "没有名为 " + call.function().name() + " 的工具，请重新选择。"));
                    continue;
                }
                if (!isAllowed(tool, visibleTools)) {
                    messages.add(ChatMessage.tool(call.id(), "当前账号无权使用工具 " + tool.name() + "，请告知用户该能力未开放。"));
                    continue;
                }
                sink.accept(AgentEvent.toolCall(tool.name()));
                try {
                    Map<String, Object> arguments = parseArguments(call.function().arguments());
                    ToolResult result = tool.execute(arguments, context);
                    results.add(result);
                    messages.add(ChatMessage.tool(call.id(), result.summary()));
                    sink.accept(AgentEvent.toolResult(tool.name(), result));
                } catch (Exception ex) {
                    log.warn("工具 {} 执行失败", tool.name(), ex);
                    String reason = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                    messages.add(ChatMessage.tool(call.id(), "工具执行失败：" + reason + "，请据此调整并告知用户。"));
                    sink.accept(AgentEvent.error("工具 " + tool.name() + " 执行失败：" + reason));
                }
            }
        }

        // 达到步数上限仍未收敛：给出兜底文案，避免死循环。
        String fallback = "处理步骤过多，已中止。请把问题拆得更具体，或稍后重试。";
        sink.accept(AgentEvent.message(fallback));
        return new AgentRun(fallback, results);
    }

    private boolean isAllowed(Tool tool, List<Tool> visibleTools) {
        return visibleTools.stream().anyMatch(t -> t.name().equals(tool.name()));
    }

    private List<ToolSpec> toToolSpecs(List<Tool> tools) {
        return tools.stream()
                .map(tool -> new ToolSpec("function", new FunctionSpec(tool.name(), tool.description(), tool.parameters())))
                .toList();
    }

    private Map<String, Object> parseArguments(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return mapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            log.warn("工具参数 JSON 解析失败：{}", json, ex);
            return Map.of();
        }
    }
}
