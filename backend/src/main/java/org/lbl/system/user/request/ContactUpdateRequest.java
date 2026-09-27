package org.lbl.system.user.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ContactUpdateRequest(
        @Size(max = 32, message = "手机号最长 32 个字符")
        @Pattern(regexp = "^$|^\\+?[0-9 ()-]{5,32}$", message = "手机号格式不正确") String phone,
        @Size(max = 128, message = "邮箱最长 128 个字符")
        @Email(message = "邮箱格式不正确") String email) {
}
