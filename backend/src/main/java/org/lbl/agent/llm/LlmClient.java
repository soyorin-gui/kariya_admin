package org.lbl.agent.llm;

import org.lbl.agent.llm.model.OpenAiModels.ChatCompletionRequest;
import org.lbl.agent.llm.model.OpenAiModels.ChatCompletionResponse;

/**
 * 模型接入抽象。骨架里只提供 OpenAI 兼容实现（{@link OpenAiCompatibleLlmClient}）；
 * 未来若接入非 OpenAI 协议（如原生 Anthropic、或公司私有协议），新增一个实现即可。
 */
public interface LlmClient {

    /**
     * 发起一次 chat completions 调用（非流式，含工具调用）。
     * <p>
     * 说明：当前骨架刻意不做 token 级流式（stream:true）。agent 层的流式体验由
     * {@code AgentChatController} 的 SSE 在"事件级"提供（工具进度 + 最终文本分块送达），
     * 已经足够；token 级流式见 docs/AI-AGENT.md 的「进阶：token 级流式」。
     */
    ChatCompletionResponse complete(ChatCompletionRequest request);
}
