package org.lbl.agent.llm;

import org.lbl.agent.config.AgentProperties;
import org.lbl.agent.llm.model.OpenAiModels.ChatCompletionRequest;
import org.lbl.agent.llm.model.OpenAiModels.ChatCompletionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * OpenAI 兼容协议的模型客户端，基于 Spring 6 自带的 {@link RestClient}（零新增依赖）。
 * <p>
 * 三个与内网强相关的刻意设计：
 * <ol>
 *   <li><b>显式 User-Agent</b>：部分内网 Nginx 会拦截无 UA / UA 不认识的外呼请求，
 *       因此用 {@code lbl.agent.user-agent} 统一设置。</li>
 *   <li><b>配置后置校验</b>：base-url / api-key / model 为空时不在启动阶段抛错（应用仍能起，
 *       便于"后端先部署、模型网关后通"），而是在首次调用时抛出可读的 {@link LlmException}。</li>
 *   <li><b>RestClient 懒初始化</b>：避免在配置为空时于构造期构建客户端导致启动失败。</li>
 * </ol>
 */
@Service
public class OpenAiCompatibleLlmClient implements LlmClient {
    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleLlmClient.class);

    private final AgentProperties properties;
    private volatile RestClient rest;

    public OpenAiCompatibleLlmClient(AgentProperties properties) {
        this.properties = properties;
    }

    @Override
    public ChatCompletionResponse complete(ChatCompletionRequest request) {
        try {
            return restClient().post()
                    .uri(properties.chatPath())
                    .body(request)
                    .retrieve()
                    .body(ChatCompletionResponse.class);
        } catch (RestClientResponseException ex) {
            log.warn("LLM 调用被拒 status={} body={}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new LlmException("模型服务返回错误（HTTP " + ex.getStatusCode().value() + "），请检查 api-key / model 是否配置正确", ex);
        } catch (ResourceAccessException ex) {
            throw new LlmException("无法连接模型服务，请检查网络与 lbl.agent.base-url", ex);
        }
    }

    private RestClient restClient() {
        RestClient current = rest;
        if (current != null) return current;
        ensureConfigured();
        synchronized (this) {
            if (rest == null) {
                SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
                int timeoutMillis = (int) (properties.timeoutSeconds() * 1000);
                factory.setConnectTimeout(timeoutMillis);
                factory.setReadTimeout(timeoutMillis);
                rest = RestClient.builder()
                        .baseUrl(properties.baseUrl())
                        .requestFactory(factory)
                        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                        // ★ 内网 Nginx 拦截点：必须显式带上 User-Agent。
                        .defaultHeader(HttpHeaders.USER_AGENT, properties.userAgent())
                        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .build();
            }
        }
        return rest;
    }

    private void ensureConfigured() {
        if (!StringUtils.hasText(properties.baseUrl())
                || !StringUtils.hasText(properties.apiKey())
                || !StringUtils.hasText(properties.model())) {
            throw new LlmException("AI 助手尚未配置：请设置环境变量 LLM_BASE_URL、LLM_API_KEY、LLM_MODEL（或 lbl.agent.* 配置项）");
        }
    }
}
