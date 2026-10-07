package org.lbl.system.log.adapter.agent.config;

import java.time.Duration;
import java.time.ZoneId;

/** Agent 审计工具的固定边界，走代码评审，不占用 YAML。 */
public record AuditAgentPolicy(
        ZoneId zoneId,
        int defaultTopN,
        int defaultTimelineLimit,
        int maxTimelineLimit,
        Duration maxTimelineRange) {

    public AuditAgentPolicy {
        if (zoneId == null || maxTimelineRange == null || maxTimelineRange.isNegative()
                || maxTimelineRange.isZero()) {
            throw new IllegalArgumentException("审计 Agent 时间策略无效");
        }
        if (defaultTopN < 1 || defaultTimelineLimit < 1
                || maxTimelineLimit < defaultTimelineLimit) {
            throw new IllegalArgumentException("审计 Agent 数量策略无效");
        }
    }
}
