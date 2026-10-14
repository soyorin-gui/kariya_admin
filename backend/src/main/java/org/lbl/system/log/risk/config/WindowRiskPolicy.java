package org.lbl.system.log.risk.config;

import org.lbl.system.log.risk.model.AuditRiskSeverity;

import java.time.Duration;

/** 一条窗口型风险规则的固定策略。 */
public record WindowRiskPolicy(boolean enabled, Duration window, int threshold, AuditRiskSeverity severity) {
    public WindowRiskPolicy {
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("风险规则时间窗口必须大于 0");
        }
        if (threshold < 1) throw new IllegalArgumentException("风险规则阈值必须大于 0");
        if (severity == null) throw new IllegalArgumentException("风险等级不能为空");
    }
}
