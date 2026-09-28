package org.lbl.agent.infrastructure.model.openai;

import org.lbl.agent.config.AgentProperties;
import org.lbl.agent.domain.AgentExecutionContext;
import org.lbl.agent.infrastructure.model.ModelGatewayException;
import org.lbl.agent.port.ModelGateway;
import org.lbl.agent.port.model.ModelMessage;
import org.lbl.agent.port.model.ModelRequest;
import org.lbl.agent.port.model.ModelResponse;
import org.lbl.agent.port.model.ModelToolCall;
import org.lbl.agent.port.model.ModelToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

/** OpenAI Chat Completions 兼容适配器；供应商协议不会泄漏到 Agent 应用层。 */
@Component
public class OpenAiCompatibleModelGateway implements ModelGateway {
    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleModelGateway.class);

    private final AgentProperties properties;
    private volatile RestClient rest;

    public OpenAiCompatibleModelGateway(AgentProperties properties) {
        this.properties = properties;
    }

    @Override
    public ModelResponse complete(ModelRequest request, AgentExecutionContext context) {
        context.checkpoint();
        OpenAiWireModels.ChatRequest wireRequest = new OpenAiWireModels.ChatRequest(
                properties.model(),
                request.messages().stream().map(this::toWireMessage).toList(),
                request.tools().isEmpty() ? null : request.tools().stream().map(this::toWireTool).toList(),
                request.tools().isEmpty() ? null : "auto",
                properties.temperature());
        try {
            OpenAiWireModels.ChatResponse response = restClient().post()
                    .uri(properties.chatPath())
                    .body(wireRequest)
                    .retrieve()
                    .body(OpenAiWireModels.ChatResponse.class);
            context.checkpoint();
            return fromWire(response);
        } catch (RestClientResponseException ex) {
            // 响应体可能包含提示词、工具参数或网关内部信息，禁止直接写日志。
            log.warn("LLM gateway rejected request status={}", ex.getStatusCode());
            throw new ModelGatewayException("模型服务暂时无法处理请求", ex);
        } catch (ResourceAccessException ex) {
            throw new ModelGatewayException("无法连接模型服务", ex);
        }
    }

    private ModelResponse fromWire(OpenAiWireModels.ChatResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()
                || response.choices().get(0).message() == null) {
            throw new ModelGatewayException("模型返回了空结果");
        }
        OpenAiWireModels.ChatResponse.Choice choice = response.choices().get(0);
        OpenAiWireModels.Message message = choice.message();
        List<ModelToolCall> calls = message.toolCalls() == null ? List.of() : message.toolCalls().stream()
                .filter(call -> call.function() != null)
                .map(call -> new ModelToolCall(call.id(), call.function().name(), call.function().arguments()))
                .toList();
        OpenAiWireModels.ChatResponse.Usage usage = response.usage();
        ModelResponse.Usage mappedUsage = usage == null ? ModelResponse.Usage.empty()
                : new ModelResponse.Usage(usage.promptTokens(), usage.completionTokens(), usage.totalTokens());
        return new ModelResponse(new ModelMessage(ModelMessage.Role.ASSISTANT, message.content(), calls, null),
                mappedUsage, choice.finishReason());
    }

    private OpenAiWireModels.Message toWireMessage(ModelMessage message) {
        String role = message.role().name().toLowerCase();
        List<OpenAiWireModels.ToolCall> calls = message.toolCalls() == null ? null : message.toolCalls().stream()
                .map(call -> new OpenAiWireModels.ToolCall(call.id(), "function",
                        new OpenAiWireModels.FunctionCall(call.name(), call.arguments())))
                .toList();
        return new OpenAiWireModels.Message(role, message.content(), calls, message.toolCallId());
    }

    private OpenAiWireModels.ToolSpec toWireTool(ModelToolDefinition tool) {
        Boolean strict = properties.strictToolSchema() && tool.strict() ? Boolean.TRUE : null;
        return new OpenAiWireModels.ToolSpec("function",
                new OpenAiWireModels.FunctionSpec(tool.name(), tool.description(), tool.inputSchema(), strict));
    }

    private RestClient restClient() {
        RestClient current = rest;
        if (current != null) return current;
        ensureConfigured();
        synchronized (this) {
            if (rest == null) {
                SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
                int timeoutMillis = Math.toIntExact(properties.timeoutSeconds() * 1000);
                factory.setConnectTimeout(timeoutMillis);
                factory.setReadTimeout(timeoutMillis);
                rest = RestClient.builder()
                        .baseUrl(properties.baseUrl())
                        .requestFactory(factory)
                        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
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
            throw new ModelGatewayException("AI 助手尚未配置模型网关");
        }
    }
}
