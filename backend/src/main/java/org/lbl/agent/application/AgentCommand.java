package org.lbl.agent.application;

import org.lbl.agent.domain.AgentMessage;

import java.util.List;

/** 一次无状态对话命令。未来迁移服务端会话时，API 层可改传 conversationId，运行器无需依赖 HTTP DTO。 */
public record AgentCommand(String message, List<AgentMessage> history) {
    public AgentCommand {
        history = history == null ? List.of() : List.copyOf(history);
    }
}
