package org.lbl.agent.application;

import org.lbl.agent.api.model.AgentChatRequest;
import org.lbl.agent.domain.AgentMessage;
import org.lbl.common.exception.BusinessException;
import org.springframework.stereotype.Component;

/** API 请求到领域命令的边界映射，并执行跨字段总量限制。 */
@Component
public class AgentRequestMapper {
    private static final int MAX_HISTORY_CHARS = 32_000;

    public AgentCommand toCommand(AgentChatRequest request) {
        int historyChars = request.history().stream().mapToInt(item -> item.content().length()).sum();
        if (historyChars > MAX_HISTORY_CHARS) {
            throw new BusinessException("对话历史过长，请新建一次对话后重试");
        }
        return new AgentCommand(request.message(), request.history().stream()
                .map(item -> item.role() == AgentChatRequest.HistoryRole.USER
                        ? AgentMessage.user(item.content())
                        : AgentMessage.assistant(item.content()))
                .toList());
    }
}
