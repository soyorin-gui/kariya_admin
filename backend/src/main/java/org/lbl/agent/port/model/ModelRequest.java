package org.lbl.agent.port.model;

import java.util.List;

/** Agent 应用层提交给模型端口的请求，不包含 baseUrl、模型名等供应商配置。 */
public record ModelRequest(List<ModelMessage> messages, List<ModelToolDefinition> tools) {
    public ModelRequest {
        messages = List.copyOf(messages);
        tools = tools == null ? List.of() : List.copyOf(tools);
    }
}
