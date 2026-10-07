package org.lbl.system.log.adapter.agent.model;

import org.lbl.system.log.analysis.model.OperationAuditOverview;
import org.lbl.system.log.risk.model.AuditRiskReport;

/** 操作统计与操作风险的同一时间快照。 */
public record OperationAuditAgentReport(
        ResolvedAuditRange range,
        OperationAuditOverview statistics,
        AuditRiskReport risks) {
}
