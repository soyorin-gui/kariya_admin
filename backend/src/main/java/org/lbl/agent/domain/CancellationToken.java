package org.lbl.agent.domain;

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

/** 协作式取消令牌。SSE 断开、超时或用户主动取消时由传输层触发。 */
public final class CancellationToken {
    private final AtomicBoolean cancelled = new AtomicBoolean();

    public void cancel() {
        cancelled.set(true);
    }

    public boolean isCancelled() {
        return cancelled.get() || Thread.currentThread().isInterrupted();
    }

    public void throwIfCancelled() {
        if (isCancelled()) throw new CancellationException("Agent run cancelled");
    }
}
