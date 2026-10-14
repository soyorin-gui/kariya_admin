package org.lbl.agent.domain;

/**
 * 工具交给 UI 的通用制品信封。
 *
 * <p>{@code type + schemaVersion} 决定前端渲染器，业务数据只存在于 {@code data}，
 * Agent 公共层不再定义 DIFF、流水号日志、投产文档等领域模型。</p>
 */
public record AgentArtifact<T>(String type, int schemaVersion, String title, T data) {
    public AgentArtifact {
        if (type == null || type.isBlank()) throw new IllegalArgumentException("artifact type must not be blank");
        if (schemaVersion < 1) throw new IllegalArgumentException("artifact schemaVersion must be positive");
    }
}
