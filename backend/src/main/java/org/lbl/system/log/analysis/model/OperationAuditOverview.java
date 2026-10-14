package org.lbl.system.log.analysis.model;

import java.time.LocalDateTime;
import java.util.List;

/** 操作审计统计结果。这里只陈述数量事实，不判断某个行为是否异常。 */
public record OperationAuditOverview(
        LocalDateTime beginTime,
        LocalDateTime endTime,
        OperationAuditTotals totals,
        List<OperationUserStatistics> topByOperations,
        List<OperationUserStatistics> topByFailures,
        List<OperationActionStatistics> topActions) {
}
