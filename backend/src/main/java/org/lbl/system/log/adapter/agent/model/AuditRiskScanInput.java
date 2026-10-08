package org.lbl.system.log.adapter.agent.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/** Input used by audit risk scans; it intentionally has no ranking parameter. */
public record AuditRiskScanInput(
        @NotNull AuditRangePreset range,
        @Min(1) @Max(24) Integer recentHours,
        LocalDateTime beginTime,
        LocalDateTime endTime) implements AuditRangeRequest {
}
