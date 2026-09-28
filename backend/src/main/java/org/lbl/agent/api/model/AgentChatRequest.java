package org.lbl.agent.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 浏览器可提交的白名单 DTO；刻意不复用模型供应商的 message/tool-call 类型。 */
public record AgentChatRequest(
        @NotBlank(message = "消息不能为空")
        @Size(max = 8000, message = "单条消息不能超过 8000 个字符")
        String message,

        @Size(max = 20, message = "历史消息不能超过 20 条")
        List<@Valid HistoryMessage> history) {

    public AgentChatRequest {
        history = history == null ? List.of() : List.copyOf(history);
    }

    public record HistoryMessage(
            @NotNull(message = "历史消息角色不能为空") HistoryRole role,
            @NotBlank(message = "历史消息内容不能为空")
            @Size(max = 8000, message = "单条历史消息不能超过 8000 个字符")
            String content) {
    }

    public enum HistoryRole {
        @JsonProperty("user") USER,
        @JsonProperty("assistant") ASSISTANT
    }
}
