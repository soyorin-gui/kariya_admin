package org.lbl.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * AI 助手（Agent）相关配置。
 *
 * <p>密钥一律走环境变量（{@code LLM_API_KEY} 等），与 {@code lbl.security.jwt-secret}
 * 同一套纪律：内网 ≠ 可信，凭据不能写进仓库或明文 yml。见 docs/AI-AGENT.md「内网部署」。
 *
 * @param enabled         是否启用 AI 助手入口。为 false 时 {@code /api/agent/chat} 直接返回友好错误，
 *                        用于"后端已部署、但模型网关还没通"的过渡期。
 * @param baseUrl         OpenAI 兼容网关的根地址（不带 /chat/completions）。例如 {@code https://llm-gw.intra}。
 * @param apiKey          网关鉴权密钥，走环境变量 {@code LLM_API_KEY}。
 * @param model           模型名，例如 {@code qwen-max} / {@code deepseek-chat} / 网关定义的任意别名。
 * @param userAgent       出站 HTTP 的 User-Agent 头。★ 某些内网 Nginx 会拦截没有 UA 或 UA 不认识的请求，
 *                        这就是必须显式设置它的原因。
 * @param chatPath        chat completions 的相对路径。默认 {@code /v1/chat/completions}；
 *                        若网关把版本号放进了 baseUrl（如 {@code https://gw/v1}），则改成 {@code /chat/completions}。
 * @param maxSteps        ReAct 工具调用循环的最大步数，防止模型反复要调工具导致死循环。
 * @param temperature     采样温度，越低越稳定。对比/排查类任务建议 0~0.2。
 * @param timeoutSeconds  单次 LLM 请求的 connect/read 超时（秒）。
 * @param sseTimeoutMillis SSE 连接的总体超时（毫秒），也是前端 fetch 读流的兜底。
 */
@ConfigurationProperties(prefix = "lbl.agent")
public record AgentProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("") String baseUrl,
        @DefaultValue("") String apiKey,
        @DefaultValue("") String model,
        @DefaultValue("KariyaAdmin-Agent/1.0") String userAgent,
        @DefaultValue("/v1/chat/completions") String chatPath,
        @DefaultValue("5") int maxSteps,
        @DefaultValue("0.1") double temperature,
        @DefaultValue("60") long timeoutSeconds,
        @DefaultValue("120000") long sseTimeoutMillis) {
}
