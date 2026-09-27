package org.lbl.agent.llm;

/**
 * LLM 调用链路的统一异常。Controller 会捕获它并把 message 作为 SSE 的 error 事件推给前端。
 */
public class LlmException extends RuntimeException {
    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
