package org.lbl.auth.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.lbl.auth.service.PasswordRules;

/**
 * 自助注册。
 * <p>
 * {@code captchaId} / {@code captchaCode} 刻意不加 {@code @NotBlank}：验证码功能可以整体关闭
 * （{@code lbl.security.captcha-enabled=false}），此时前端不会提交这两个字段。
 * "到底要不要验证码"这个判定只应该有一个地方说了算，就是 {@code CaptchaService}；
 * 在这里再加一层注解约束会让开关失效。
 * <p>
 * {@code password} 上 {@code @Size} 与 {@code @Pattern} <b>故意用同一句 message</b>：
 * Bean Validation 会把属性上的所有约束都跑一遍，不存在"先过的那个先返回"，
 * 最终回显哪一条取决于遍历顺序。两句话只要不同，用户就会随机构收到不同的提示；
 * 用同一句完整规则就与顺序无关了。{@code @Size} 的额外价值是把长度上限
 * 也表达在常量层（正则之外的第二处显式声明），而不是靠正则里的 {@code {8,32}} 隐式携带。
 */
public record RegistrationRequest(
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
        @NotBlank(message = "请确认密码") @Size(max = 32, message = "确认密码长度不合法") String confirmPassword,
        boolean rememberMe,
        @Size(max = 64, message = "验证码标识不合法") String captchaId,
        @Size(max = 8, message = "验证码长度不合法") String captchaCode) {
}
