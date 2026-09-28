package org.lbl.agent.domain;

import org.lbl.agent.tool.ToolResult;

import java.util.List;

/** 一次运行的最终文本与工具执行结果，供审计、测试和未来会话持久化使用。 */
public record AgentRun(String runId, String text, List<ToolResult<?>> toolResults) {
    public AgentRun {
        toolResults = toolResults == null ? List.of() : List.copyOf(toolResults);
    }
}
