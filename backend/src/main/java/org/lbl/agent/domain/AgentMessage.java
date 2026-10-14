package org.lbl.agent.domain;

/** Agent 内部的对话消息。API DTO 与模型供应商 DTO 都必须先转换成此类型。 */
public record AgentMessage(Role role, String content) {
    public enum Role { SYSTEM, USER, ASSISTANT }

    public static AgentMessage system(String content) {
        return new AgentMessage(Role.SYSTEM, content);
    }

    public static AgentMessage user(String content) {
        return new AgentMessage(Role.USER, content);
    }

    public static AgentMessage assistant(String content) {
        return new AgentMessage(Role.ASSISTANT, content);
    }
}
