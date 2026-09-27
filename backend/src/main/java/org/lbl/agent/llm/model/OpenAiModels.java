package org.lbl.agent.llm.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容 chat completions 协议的线格式 DTO。
 * <p>
 * 手写这些而不是依赖 Spring AI，是为了三件事：①精确控制出站 User-Agent（内网 Nginx 拦截）；
 * ②不引入任何新 Maven 依赖（内网离线仓库不会被 Spring AI 的传递依赖卡住）；
 * ③让 tool-calling 循环透明可读。协议细节见 docs/AI-AGENT.md。
 */
public final class OpenAiModels {
    private OpenAiModels() {
    }

    /**
     * 一条消息。role ∈ {system, user, assistant, tool}。
     * tool_calls 只出现在 assistant 消息里；tool_call_id 只出现在 tool 消息里。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChatMessage(String role, String content,
                              @JsonProperty("tool_calls") List<ToolCall> toolCalls,
                              @JsonProperty("tool_call_id") String toolCallId) {
        public static ChatMessage system(String content) {
            return new ChatMessage("system", content, null, null);
        }

        public static ChatMessage user(String content) {
            return new ChatMessage("user", content, null, null);
        }

        public static ChatMessage assistant(String content, List<ToolCall> toolCalls) {
            return new ChatMessage("assistant", content, toolCalls, null);
        }

        public static ChatMessage tool(String toolCallId, String content) {
            return new ChatMessage("tool", content, null, toolCallId);
        }
    }

    /** 模型返回的一次工具调用请求。 */
    public record ToolCall(String id, String type, FunctionCall function) {
    }

    /** 工具调用里的函数名与参数（arguments 是 JSON 字符串，需自行解析）。 */
    public record FunctionCall(String name, String arguments) {
    }

    /** 请求体里的工具声明（type 固定 "function"）。 */
    public record ToolSpec(String type, FunctionSpec function) {
    }

    /** 工具的 name/description/parameters（parameters 是 JSON Schema object）。 */
    public record FunctionSpec(String name, String description, Map<String, Object> parameters) {
    }

    /** chat completions 请求体。tools 为空时置 null，避免个别网关拒绝空数组。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChatCompletionRequest(String model, List<ChatMessage> messages,
                                        List<ToolSpec> tools, String toolChoice, Double temperature) {
    }

    /** chat completions 响应体。 */
    public record ChatCompletionResponse(List<Choice> choices, Usage usage) {

        public record Choice(int index, Message message,
                             @JsonProperty("finish_reason") String finishReason) {
        }

        public record Message(String role, String content,
                              @JsonProperty("tool_calls") List<ToolCall> toolCalls) {
        }

        public record Usage(@JsonProperty("prompt_tokens") int promptTokens,
                            @JsonProperty("completion_tokens") int completionTokens,
                            @JsonProperty("total_tokens") int totalTokens) {
        }
    }
}
