package org.lbl.agent.domain;

/** 助手级规则；业务工具不得自行覆盖这些安全边界。 */
public record AgentDefinition(String id, String name, String instructions) {
}
