package org.kariya.system.user;

import jakarta.validation.constraints.*;
import java.util.List;

public record UserRequest(@NotBlank String username, @NotBlank String realName, @NotBlank String phone, String email,
                          @NotNull Long deptId, @NotEmpty List<Long> roleIds, @NotNull Integer status) {
}
