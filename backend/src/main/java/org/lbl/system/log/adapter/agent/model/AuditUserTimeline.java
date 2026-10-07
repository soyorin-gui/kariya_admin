package org.lbl.system.log.adapter.agent.model;

import java.util.List;

/** 有明确上限的用户事件时间线；truncated=true 时提示模型缩小范围。 */
public record AuditUserTimeline<T>(
        ResolvedAuditRange range,
        Long userId,
        String username,
        int returnedCount,
        boolean truncated,
        List<T> events) {

    public AuditUserTimeline {
        events = events == null ? List.of() : List.copyOf(events);
    }
}
