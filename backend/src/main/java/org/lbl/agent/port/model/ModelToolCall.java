package org.lbl.agent.port.model;

/** 模型请求执行某个工具；arguments 保留原始 JSON，由统一绑定器解析和校验。 */
public record ModelToolCall(String id, String name, String arguments) {
}
