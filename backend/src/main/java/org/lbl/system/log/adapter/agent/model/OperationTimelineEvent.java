package org.lbl.system.log.adapter.agent.model;

import java.time.LocalDateTime;

/** 给 Agent 的操作证据最小投影。 */
public record OperationTimelineEvent(
        Long logId,
        Long userId,
        String username,
        String module,
        String action,
        String targetType,
        String targetId,
        String targetName,
        String requestId,
        String result,
        Long durationMs,
        LocalDateTime occurredAt) {
}
