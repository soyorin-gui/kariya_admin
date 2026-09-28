package org.lbl.auth.uias;

import jakarta.servlet.http.HttpServletRequest;

/**
 * UIAS SDK 的最小适配面。
 * <p>
 * 内网接入时实现类注入 SDK 提供的 AssertionConsumer，并在此处调用
 * {@code consumer.consume(request)} 返回唯一工号。SDK 的证书、Recipient、断言有效期等
 * 校验仍由 SDK 及其配置负责；本项目不解析或记录 SAMLResponse 原文。
 */
public interface UiasAssertionConsumer {
    String consumeEmployeeNo(HttpServletRequest request);
}
