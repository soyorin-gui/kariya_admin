package org.lbl.system.log.adapter.agent.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/** 登录/操作审计分析工具的类型化输入。 */
public record AuditAnalyzeInput(
        @NotNull AuditRangePreset range,
        @Min(1) @Max(24) Integer recentHours,
        LocalDateTime beginTime,
        LocalDateTime endTime,
        @Min(1) @Max(50) Integer topN) implements AuditRangeRequest {
}
