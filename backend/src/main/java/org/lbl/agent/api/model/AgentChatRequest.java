package org.lbl.agent.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 浏览器可提交的白名单 DTO；刻意不复用模型供应商的 message/tool-call 类型。 */
public record AgentChatRequest(
        @NotBlank(message = "消息不能为空")
        @Size(max = 8000, message = "单条消息不能超过 8000 个字符")
        String message,

        @Size(max = 20, message = "历史消息不能超过 20 条")
        List<@Valid HistoryMessage> history,

        @Valid PageContext pageContext) {

    public AgentChatRequest {
        history = history == null ? List.of() : List.copyOf(history);
    }

    public record HistoryMessage(
            @NotNull(message = "历史消息角色不能为空") HistoryRole role,
            @NotBlank(message = "历史消息内容不能为空")
            @Size(max = 8000, message = "单条历史消息不能超过 8000 个字符")
            String content) {
    }

    /**
     * 浏览器可提交的页面上下文白名单。刻意不接收权限码、角色或任意属性 Map，
     * 避免未来有人误把客户端声明当成授权依据。
     */
    public record PageContext(
            @NotBlank(message = "页面路径不能为空")
            @Size(max = 256, message = "页面路径不能超过 256 个字符")
            @Pattern(regexp = "^/[^\\r\\n]*$", message = "页面路径格式不正确")
            String routePath,

            @Positive(message = "菜单 ID 必须为正数") Long menuId,

            @Size(max = 100, message = "页面名称不能超过 100 个字符") String pageTitle,

            @Size(max = 8, message = "页面面包屑不能超过 8 级")
            List<@NotBlank(message = "面包屑名称不能为空") @Size(max = 100, message = "面包屑名称不能超过 100 个字符") String> breadcrumb) {

        public PageContext {
            breadcrumb = breadcrumb == null ? List.of() : List.copyOf(breadcrumb);
        }
    }

    public enum HistoryRole {
        @JsonProperty("user") USER,
        @JsonProperty("assistant") ASSISTANT
    }
}
