package org.kariya.system.role;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RoleRequest(
        @NotBlank String roleName,
        @NotBlank String roleCode,
        @NotBlank String dataScope,
        @NotNull Integer status
) {
}
