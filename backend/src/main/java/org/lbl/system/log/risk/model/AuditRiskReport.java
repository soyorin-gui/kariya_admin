package org.lbl.system.log.risk.model;

import java.time.LocalDateTime;
import java.util.List;

/** 风险扫描响应；totalFindingCount 与 truncated 让调用方知道结果是否被返回上限裁剪。 */
public record AuditRiskReport(
        LocalDateTime beginTime,
        LocalDateTime endTime,
        int totalFindingCount,
        int returnedFindingCount,
        boolean truncated,
        List<AuditRiskFinding> findings) {

    public AuditRiskReport {
        findings = findings == null ? List.of() : List.copyOf(findings);
    }
}
