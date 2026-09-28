package org.lbl.agent.application;

import org.junit.jupiter.api.Test;
import org.lbl.agent.api.model.AgentChatRequest;
import org.lbl.common.exception.BusinessException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentRequestMapperTest {
    private final AgentRequestMapper mapper = new AgentRequestMapper();

    @Test
    void mapsOnlyWhitelistedHistoryRoles() {
        AgentChatRequest request = new AgentChatRequest("继续",
                List.of(new AgentChatRequest.HistoryMessage(AgentChatRequest.HistoryRole.USER, "问题"),
                        new AgentChatRequest.HistoryMessage(AgentChatRequest.HistoryRole.ASSISTANT, "回答")));

        AgentCommand command = mapper.toCommand(request);

        assertEquals(2, command.history().size());
        assertEquals("问题", command.history().get(0).content());
    }

    @Test
    void rejectsExcessiveTotalHistory() {
        String longMessage = "x".repeat(7_000);
        List<AgentChatRequest.HistoryMessage> history = java.util.stream.IntStream.range(0, 5)
                .mapToObj(index -> new AgentChatRequest.HistoryMessage(
                        index % 2 == 0 ? AgentChatRequest.HistoryRole.USER : AgentChatRequest.HistoryRole.ASSISTANT,
                        longMessage))
                .toList();

        assertThrows(BusinessException.class,
                () -> mapper.toCommand(new AgentChatRequest("继续", history)));
    }
}
