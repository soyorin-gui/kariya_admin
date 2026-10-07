package org.lbl.system.log.adapter.agent.model;

import org.lbl.system.log.analysis.model.LoginAuditOverview;
import org.lbl.system.log.risk.model.AuditRiskReport;

/** 登录统计与登录风险的同一时间快照。 */
public record LoginAuditAgentReport(
        ResolvedAuditRange range,
        LoginAuditOverview statistics,
        AuditRiskReport risks) {
}
