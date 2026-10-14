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

    /** 所有已配置规则中的最大观察窗口，用于多分片扫描时向前补齐上下文。 */
    public Duration maxRuleWindow() {
        Duration max = Duration.ZERO;
        for (WindowRiskPolicy rule : new WindowRiskPolicy[]{loginFailureBurst, loginIpAccountSpray,
                passwordChangeBurst, passwordResetBurst}) {
            if (rule.enabled() && rule.window().compareTo(max) > 0) max = rule.window();
        }
        return max;
    }
}
