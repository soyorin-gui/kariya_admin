package org.lbl.agent.port.model;

/** 一轮模型调用结果以及可观测的 token 用量。 */
public record ModelResponse(ModelMessage message, Usage usage, String finishReason) {
    public record Usage(int inputTokens, int outputTokens, int totalTokens) {
        public static Usage empty() {
            return new Usage(0, 0, 0);
        }
    }
}
