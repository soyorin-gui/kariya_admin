package org.lbl.auth.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 登录请求。
 * <p>
 * 长度上限不能省：这个接口是匿名可访问的，而密码要交给 BCrypt 处理。
 * 没有上限时，一个几十 MB 的 password 字段会让服务端做无意义的哈希运算，
 * 变成一个不需要认证就能触发的资源消耗点。72 与 BCrypt 的截断长度一致。
 * username 上限 64 对齐 sys_user.username 的列宽。
 */
public record LoginRequest(@NotBlank(message = "请输入用户名")
                           @Size(max = 64, message = "用户名长度不合法")
                           String username,
                           @NotBlank(message = "请输入密码")
                           @Size(max = 72, message = "密码长度不合法")
                           String password,
                           boolean rememberMe) {
}
