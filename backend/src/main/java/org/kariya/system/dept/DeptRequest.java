package org.kariya.system.dept;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DeptRequest(
        Long parentId,
        @NotBlank String deptName,
        @NotBlank String deptCode,
        Long leaderUserId,
        @NotNull Integer sortOrder,
        @NotNull Integer status
) {
}
