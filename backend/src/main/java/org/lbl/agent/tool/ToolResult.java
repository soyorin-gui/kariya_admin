package org.lbl.agent.tool;

import org.lbl.agent.domain.AgentArtifact;

/**
 * 类型化工具结果。modelSummary 是给模型的最小事实集；output/artifact 面向程序和 UI。
 * 原始大报文、敏感字段和内部异常不得进入 modelSummary。
 */
public record ToolResult<O>(String modelSummary, O output, AgentArtifact<O> artifact) {
    public static <O> ToolResult<O> of(String modelSummary, O output) {
        return new ToolResult<>(modelSummary, output, null);
    }

    public static <O> ToolResult<O> artifact(String modelSummary, AgentArtifact<O> artifact) {
        return new ToolResult<>(modelSummary, artifact.data(), artifact);
    }
}
