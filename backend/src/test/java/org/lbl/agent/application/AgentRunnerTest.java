package org.lbl.agent.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.lbl.agent.config.AgentProperties;
import org.lbl.agent.domain.AgentActor;
import org.lbl.agent.domain.AgentDefinition;
import org.lbl.agent.domain.AgentEvent;
import org.lbl.agent.domain.AgentExecutionContext;
import org.lbl.agent.domain.CancellationToken;
import org.lbl.agent.port.ModelGateway;
import org.lbl.agent.port.model.ModelMessage;
import org.lbl.agent.port.model.ModelResponse;
import org.lbl.agent.port.model.ModelToolCall;
import org.lbl.agent.tool.AgentTool;
import org.lbl.agent.tool.ApprovalPolicy;
import org.lbl.agent.tool.ToolArgumentBinder;
import org.lbl.agent.tool.ToolDescriptor;
import org.lbl.agent.tool.ToolExecutionService;
import org.lbl.agent.tool.ToolRegistry;
import org.lbl.agent.tool.ToolResult;
import org.lbl.agent.tool.ToolRisk;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentRunnerTest {

    @Test
    void executesToolAndFeedsSummaryBackToModel() {
        EchoTool tool = new EchoTool();
        ToolRegistry registry = new ToolRegistry(List.of(tool));
        ToolExecutionService execution = new ToolExecutionService(registry,
                new ToolArgumentBinder(new ObjectMapper(), Validation.buildDefaultValidatorFactory().getValidator()));
        AtomicInteger modelCalls = new AtomicInteger();
        ModelGateway model = (request, context) -> {
            if (modelCalls.getAndIncrement() == 0) {
                return response(ModelMessage.assistant(null,
                        List.of(new ModelToolCall("call-1", "echo_value", "{\"value\":\"hello\"}"))));
            }
            assertEquals("已确认值 hello", request.messages().get(request.messages().size() - 1).content());
            return response(ModelMessage.assistant("处理完成"));
        };
        AgentRunner runner = new AgentRunner(model, registry, execution,
                new AgentDefinition("test", "test", "只根据工具事实回答"), properties(), List.of());
        List<AgentEvent> events = new ArrayList<>();

        runner.run(new AgentCommand("测试", List.of()), context(), events::add);

        assertEquals(2, modelCalls.get());
        assertEquals(List.of("tool_call", "tool_result", "message"), events.stream().map(AgentEvent::type).toList());
    }

    private ModelResponse response(ModelMessage message) {
        return new ModelResponse(message, ModelResponse.Usage.empty(), "stop");
    }

    private AgentExecutionContext context() {
        return new AgentExecutionContext("run-1", new AgentActor(1L, "tester", false, Set.of("echo:use")),
                Instant.now().plusSeconds(30), new CancellationToken());
    }

    private AgentProperties properties() {
        return new AgentProperties(true, "http://unused", "key", "model", "test", "/chat", 3,
                0.1, 10, 30, false, 60_000);
    }

    private record EchoInput(String value) {
    }

    private static final class EchoTool implements AgentTool<EchoInput, String> {
        @Override
        public ToolDescriptor descriptor() {
            return new ToolDescriptor("echo_value", "返回传入值",
                    Map.of("type", "object", "properties", Map.of("value", Map.of("type", "string"))),
                    ToolRisk.READ_ONLY, ApprovalPolicy.NOT_REQUIRED, Set.of("echo:use"), Duration.ofSeconds(5));
        }

        @Override
        public Class<EchoInput> inputType() {
            return EchoInput.class;
        }

        @Override
        public ToolResult<String> execute(EchoInput input, AgentExecutionContext context) {
            return ToolResult.of("已确认值 " + input.value(), input.value());
        }
    }
}
