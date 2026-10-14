package org.lbl.agent.port.model;

import java.util.Map;

/** 提供给模型的工具声明。strict 是否真正生效由具体模型网关决定。 */
public record ModelToolDefinition(String name, String description, Map<String, Object> inputSchema, boolean strict) {
}
