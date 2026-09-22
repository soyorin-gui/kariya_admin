package org.kariya.system.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordChangeRequest(@NotBlank String oldPassword,
                                    @NotBlank @Size(min = 12, max = 72) String newPassword) {
}
