package org.lbl.agent.domain;

/** 传输无关的 Agent 运行事件；SSE 只是它当前的一种输出适配器。 */
public record AgentEvent(
        String type,
        String runId,
        String toolName,
        String text,
        String summary,
        AgentArtifact<?> artifact) {

    public static AgentEvent message(String runId, String text) {
        return new AgentEvent("message", runId, null, text, null, null);
    }

    public static AgentEvent toolCall(String runId, String toolName) {
        return new AgentEvent("tool_call", runId, toolName, null, null, null);
    }

    public static AgentEvent toolResult(String runId, String toolName, String summary, AgentArtifact<?> artifact) {
        return new AgentEvent("tool_result", runId, toolName, null, summary, artifact);
    }

    public static AgentEvent error(String runId, String text) {
        return new AgentEvent("error", runId, null, text, null, null);
    }

    public static AgentEvent done(String runId) {
        return new AgentEvent("done", runId, null, null, null, null);
    }
}
