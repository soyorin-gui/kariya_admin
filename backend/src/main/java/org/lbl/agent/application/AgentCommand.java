package org.lbl.agent.application;

import org.lbl.agent.domain.AgentMessage;
import org.lbl.agent.domain.AgentPageContext;

import java.util.List;

/** A stateless chat command. profileId is selected by a trusted entry point; null uses the default profile. */
public record AgentCommand(String message, List<AgentMessage> history, AgentPageContext pageContext, String profileId) {
    public AgentCommand {
        history = history == null ? List.of() : List.copyOf(history);
    }

    public AgentCommand(String message, List<AgentMessage> history, AgentPageContext pageContext) {
        this(message, history, pageContext, null);
    }
}