package org.lbl.auth.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 把本次已验证的外部身份绑定到一个已有系统账号。 */
public record OnboardingBindRequest(@NotBlank(message = "请输入用户名")
                                    @Size(max = 64, message = "用户名最长 64 个字符")
                                    String username,
                                    @NotBlank(message = "请输入密码")
                                    @Size(max = 72, message = "密码最长 72 个字符")
                                    String password) {
}
