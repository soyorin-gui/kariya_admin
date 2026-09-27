package org.lbl.auth.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.lbl.auth.service.PasswordRules;

/** 外部身份首次认证后，用户选择创建系统账号时提交的表单。 */
public record OnboardingAccountRequest(
        @NotBlank(message = "请输入用户名")
        @Size(max = 64, message = "用户名最长 64 个字符")
        @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "用户名只能包含字母、数字、下划线、点和短横线")
        String username,
        @NotBlank(message = "请输入姓名") @Size(max = 64, message = "姓名最长 64 个字符") String realName,
        @Size(max = 32, message = "手机号最长 32 个字符") String phone,
        @Email(message = "邮箱格式不正确") @Size(max = 128, message = "邮箱最长 128 个字符") String email,
        @NotBlank(message = "请输入密码")
        @Size(min = PasswordRules.MIN_LENGTH, max = PasswordRules.MAX_LENGTH, message = PasswordRules.MESSAGE)
        @Pattern(regexp = PasswordRules.REGEX, message = PasswordRules.MESSAGE) String password,
        @NotBlank(message = "请确认密码") @Size(max = 32, message = "确认密码长度不合法") String confirmPassword) {
}
