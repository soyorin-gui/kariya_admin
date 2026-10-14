package org.lbl.system.user.request;

import jakarta.validation.constraints.*;
import org.lbl.auth.service.PasswordRules;

import java.util.List;

/**
 * 管理员新增用户专用请求；编辑用户时不允许顺带修改密码。
 * <p>
 * 与 {@code UserRequest}（编辑用）分开的原因：新增时管理员必须<b>直接指定初始密码</b>，
 * 而不是由系统生成一串随机字符让管理员转达。这样管理员新增和用户自助注册走的是
 * 同一套密码规则（{@link PasswordRules}），也不存在"系统给的临时密码没人记得住"的问题。
 * <p>
 * {@code password} 上 {@code @Size} 与 {@code @Pattern} 用同一句 message，
 * 原因见 {@code RegistrationRequest} 的类注释。
 */
public record UserCreateRequest(
                                @NotBlank(message = "请输入用户名")
                                @Size(max = 64, message = "用户名最长 64 个字符")
                                @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "用户名只能包含字母、数字、下划线、点和短横线")
                                String username,
                                @NotBlank(message = "请输入姓名") @Size(max = 64, message = "姓名最长 64 个字符") String realName,
                                @NotBlank(message = "请输入手机号") @Size(max = 32, message = "手机号最长 32 个字符")
                                @Pattern(regexp = "^\\+?[0-9 ()-]{5,32}$", message = "手机号格式不正确") String phone,
                                @Size(max = 128, message = "邮箱最长 128 个字符") @Email(message = "邮箱格式不正确") String email,
                                @NotNull(message = "请选择所属部门") @Positive(message = "所属部门无效") Long deptId,
                                @NotEmpty(message = "请至少选择一个角色") @Size(max = 50, message = "单个用户的角色数量过多")
                                List<@NotNull(message = "角色无效") @Positive(message = "角色无效") Long> roleIds,
                                @NotNull(message = "请选择状态") @Min(value = 0, message = "状态无效") @Max(value = 1, message = "状态无效") Integer status,
                                @NotBlank(message = "请输入密码")
                                @Size(min = PasswordRules.MIN_LENGTH, max = PasswordRules.MAX_LENGTH, message = PasswordRules.MESSAGE)
                                @Pattern(regexp = PasswordRules.REGEX, message = PasswordRules.MESSAGE)
                                String password,
                                @NotBlank(message = "请确认密码") @Size(max = 32, message = "确认密码长度不合法") String confirmPassword) {
    public UserRequest userRequest() {
        return new UserRequest(username, realName, phone, email, deptId, roleIds, status);
    }
}
