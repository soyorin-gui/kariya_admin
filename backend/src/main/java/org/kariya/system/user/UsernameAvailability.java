package org.kariya.system.user;

/**
 * 用户名可用性校验结果，供新增用户表单在输入阶段即时提示。
 *
 * @param available 是否可用
 * @param message   不可用原因；available 为 true 时为 null
 */
public record UsernameAvailability(boolean available, String message) {
}
