package org.lbl.system.log.adapter.agent.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** 用户时间线下钻输入；userId 优先，缺少 userId 时按完整用户名精确匹配。 */
public record AuditUserTimelineInput(
        @NotNull AuditRangePreset range,
        @Min(1) @Max(24) Integer recentHours,
        LocalDateTime beginTime,
        LocalDateTime endTime,
        @Min(1) Long userId,
        @Size(max = 64) String username,
        @Min(1) @Max(100) Integer limit) implements AuditRangeRequest {
}
