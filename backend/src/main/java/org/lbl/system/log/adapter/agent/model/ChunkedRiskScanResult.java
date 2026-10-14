package org.lbl.system.log.adapter.agent.model;

import org.lbl.system.log.risk.model.AuditRiskReport;

/** 多日风险扫描结果及其内部主分片数，供报告说明扫描覆盖情况。 */
public record ChunkedRiskScanResult(AuditRiskReport report, int segmentCount) {
}
