package org.lbl.system.log.risk.config;

import java.time.Duration;

/** 阶段二风险扫描的完整策略；由 Java Config 创建，不依赖 YAML。 */
public record AuditRiskPolicy(
        Duration maxScanRange,
        int maxEventsPerSource,
        int maxFindings,
        int maxEvidenceIds,
        WindowRiskPolicy loginFailureBurst,
        WindowRiskPolicy loginIpAccountSpray,
        WindowRiskPolicy passwordChangeBurst,
        WindowRiskPolicy passwordResetBurst) {

    public AuditRiskPolicy {
        if (maxScanRange == null || maxScanRange.isZero() || maxScanRange.isNegative()) {
            throw new IllegalArgumentException("最大扫描范围必须大于 0");
        }
        if (maxEventsPerSource < 1 || maxFindings < 1 || maxEvidenceIds < 1) {
            throw new IllegalArgumentException("风险扫描数量限制必须大于 0");
        }
        if (loginFailureBurst == null || loginIpAccountSpray == null
                || passwordChangeBurst == null || passwordResetBurst == null) {
            throw new IllegalArgumentException("风险规则策略不能为空");
        }
    }
}
