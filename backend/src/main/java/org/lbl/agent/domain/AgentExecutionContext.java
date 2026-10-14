package org.lbl.agent.domain;

import java.time.Instant;

/** 模型调用与工具执行共享的运行上下文，不包含任何具体业务字段。 */
public record AgentExecutionContext(
        String runId,
        AgentActor actor,
        Instant deadline,
        CancellationToken cancellation) {

    public void checkpoint() {
        cancellation.throwIfCancelled();
        if (Instant.now().isAfter(deadline)) {
            cancellation.cancel();
            cancellation.throwIfCancelled();
        }
    }
}
