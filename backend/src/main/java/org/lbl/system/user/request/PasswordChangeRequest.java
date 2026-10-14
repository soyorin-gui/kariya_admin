package org.lbl.system.user.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.lbl.auth.service.PasswordRules;

/**
 * 修改本人密码。
 * <p>
 * 两个字段的规则刻意<b>不对称</b>，这是本类最容易改错的地方：
 * <ul>
 *   <li>{@code oldPassword} 只限长度、<b>不套新密码规则</b>。它是<b>历史密码</b>，
 *       可能是旧的 12-72 位、且含符号，套上新规则会让所有老用户立刻改不了密码。
 *       上限 72 是 BCrypt 的固有截断长度（只取前 72 字节参与运算，
 *       更长的部分被静默忽略，于是"两个不同的超长密码"可能被判定为同一个）。</li>
 *   <li>{@code newPassword} 走全站统一的新规则，见 {@link PasswordRules}。
 *       {@code @Size} 与 {@code @Pattern} 用同一句 message，原因见 {@code RegistrationRequest} 的类注释。</li>
 * </ul>
 */
public record PasswordChangeRequest(@NotBlank(message = "请输入原密码")
                                    @Size(max = 72, message = "原密码长度不合法")
                                    String oldPassword,
                                    @NotBlank(message = "请输入新密码")
                                    @Size(min = PasswordRules.MIN_LENGTH, max = PasswordRules.MAX_LENGTH, message = PasswordRules.MESSAGE)
                                    @Pattern(regexp = PasswordRules.REGEX, message = PasswordRules.MESSAGE)
                                    String newPassword) {
}
