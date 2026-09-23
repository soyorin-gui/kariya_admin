package org.lbl.system.user.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改本人密码。
 * <p>
 * 上限 72 是 BCrypt 的固有上限：BCrypt 只取前 72 字节参与运算，更长的部分被静默忽略，
 * 于是"两个不同的超长密码"可能被判定为同一个。与其让用户以为自己设了一个更长的密码，
 * 不如在校验层直接拒绝。
 */
public record PasswordChangeRequest(@NotBlank(message = "请输入原密码")
                                    @Size(max = 72, message = "原密码长度不合法")
                                    String oldPassword,
                                    @NotBlank(message = "请输入新密码")
                                    @Size(min = 12, max = 72, message = "新密码长度需为 12-72 个字符")
                                    String newPassword) {
}
