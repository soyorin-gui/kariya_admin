package org.lbl.agent.infrastructure.model.openai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/** OpenAI Chat Completions 兼容协议的线格式，只允许在 infrastructure.model.openai 包中使用。 */
final class OpenAiWireModels {
    private OpenAiWireModels() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Message(String role, String content,
                   @JsonProperty("tool_calls") List<ToolCall> toolCalls,
                   @JsonProperty("tool_call_id") String toolCallId) {
    }

    record ToolCall(String id, String type, FunctionCall function) {
    }

    record FunctionCall(String name, String arguments) {
    }

    record ToolSpec(String type, FunctionSpec function) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record FunctionSpec(String name, String description, Map<String, Object> parameters, Boolean strict) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ChatRequest(String model, List<Message> messages, List<ToolSpec> tools,
                       @JsonProperty("tool_choice") String toolChoice, Double temperature) {
    }

    record ChatResponse(List<Choice> choices, Usage usage) {
        record Choice(int index, Message message, @JsonProperty("finish_reason") String finishReason) {
        }

        record Usage(@JsonProperty("prompt_tokens") int promptTokens,
                     @JsonProperty("completion_tokens") int completionTokens,
                     @JsonProperty("total_tokens") int totalTokens) {
        }
    }
}
