package org.lbl.agent.port.model;

import java.util.List;

/** 与供应商无关的模型消息。toolCalls/toolCallId 只用于工具调用回填。 */
public record ModelMessage(Role role, String content, List<ModelToolCall> toolCalls, String toolCallId) {
    public enum Role { SYSTEM, USER, ASSISTANT, TOOL }

    public static ModelMessage system(String content) {
        return new ModelMessage(Role.SYSTEM, content, null, null);
    }

    public static ModelMessage user(String content) {
        return new ModelMessage(Role.USER, content, null, null);
    }

    public static ModelMessage assistant(String content) {
        return new ModelMessage(Role.ASSISTANT, content, null, null);
    }

    public static ModelMessage assistant(String content, List<ModelToolCall> toolCalls) {
        return new ModelMessage(Role.ASSISTANT, content, toolCalls, null);
    }

    public static ModelMessage tool(String toolCallId, String content) {
        return new ModelMessage(Role.TOOL, content, null, toolCallId);
    }
}
