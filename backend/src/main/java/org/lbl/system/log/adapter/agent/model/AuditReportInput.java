package org.lbl.system.log.adapter.agent.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/** 一次调用完成统计、风险或完整报告，避免模型自行拆分业务步骤。 */
public record AuditReportInput(
        @NotNull AuditRangePreset range,
        @Min(1) @Max(24) Integer recentHours,
        LocalDateTime beginTime,
        LocalDateTime endTime,
        @NotNull AuditReportMode mode,
        @Min(1) @Max(50) Integer topN) implements AuditRangeRequest {
}
