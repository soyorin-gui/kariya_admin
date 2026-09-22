package org.kariya.system.menu;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MenuRequest(
        Long parentId,
        @NotBlank String menuName,
        @NotBlank String menuType,
        String routeName,
        String routePath,
        String component,
        String permissionCode,
        String icon,
        @NotNull Integer sortOrder,
        @NotNull Integer visible,
        @NotNull Integer status,
        @NotNull Integer keepAlive
) {
}
