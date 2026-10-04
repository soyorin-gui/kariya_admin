package org.lbl.agent.application;

import org.lbl.agent.api.model.AgentChatRequest;
import org.lbl.agent.domain.AgentMessage;
import org.lbl.agent.domain.AgentPageContext;
import org.lbl.common.exception.BusinessException;
import org.lbl.common.util.TextValues;
import org.springframework.stereotype.Component;

import static org.lbl.common.util.TextValues.trimToNull;

/** API 请求到领域命令的边界映射，并执行跨字段总量限制。 */
@Component
public class AgentRequestMapper {
    private static final int MAX_HISTORY_CHARS = 32_000;

    public AgentCommand toCommand(AgentChatRequest request) {
        int historyChars = request.history().stream().mapToInt(item -> item.content().length()).sum();
        if (historyChars > MAX_HISTORY_CHARS) {
            throw new BusinessException("对话历史过长，请新建一次对话后重试");
        }
        AgentChatRequest.PageContext page = request.pageContext();
        AgentPageContext pageContext = page == null ? null : new AgentPageContext(
                normalizePath(page.routePath()), page.menuId(), singleLine(page.pageTitle()),
                page.breadcrumb().stream().map(this::singleLine).filter(java.util.Objects::nonNull).toList());
        return new AgentCommand(request.message(), request.history().stream()
                .map(item -> item.role() == AgentChatRequest.HistoryRole.USER
                        ? AgentMessage.user(item.content())
                        : AgentMessage.assistant(item.content()))
                .toList(), pageContext);
    }

    private String normalizePath(String value) {
        if (value == null) return null;
        String path = value.trim();
        return path.startsWith("/") ? path : null;
    }


    private String singleLine(String value) {
        String trimmed = TextValues.trimToNull(value);
        return trimmed == null ? null : trimmed.replaceAll("\\s+", " ");
    }
}
