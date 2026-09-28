package org.lbl.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.lbl.agent.domain.AgentActor;
import org.lbl.agent.domain.AgentExecutionContext;
import org.lbl.agent.domain.CancellationToken;
import org.lbl.common.exception.BusinessException;
import org.springframework.security.access.AccessDeniedException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ToolExecutionServiceTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final ToolArgumentBinder binder = new ToolArgumentBinder(new ObjectMapper(), validator);

    @Test
    void bindsTypedInputAndExecutesAllowedTool() {
        ToolExecutionService service = service(new TestTool(ApprovalPolicy.NOT_REQUIRED));

        ToolResult<?> result = service.execute("query_trace", "{\"serialNo\":\"TX-100\"}", context(Set.of("trace:query")));

        assertEquals("已查询流水号 TX-100", result.modelSummary());
        assertEquals("TX-100", result.output());
    }

    @Test
    void rejectsInvalidArgumentsBeforeBusinessExecution() {
        ToolExecutionService service = service(new TestTool(ApprovalPolicy.NOT_REQUIRED));

        assertThrows(BusinessException.class,
                () -> service.execute("query_trace", "{\"serialNo\":\"\"}", context(Set.of("trace:query"))));
    }

    @Test
    void checksPermissionAgainAtExecutionTime() {
        ToolExecutionService service = service(new TestTool(ApprovalPolicy.NOT_REQUIRED));

        assertThrows(AccessDeniedException.class,
                () -> service.execute("query_trace", "{\"serialNo\":\"TX-100\"}", context(Set.of())));
    }

    @Test
    void failsClosedWhenApprovalIsRequired() {
        ToolExecutionService service = service(new TestTool(ApprovalPolicy.REQUIRED));

        assertThrows(BusinessException.class,
                () -> service.execute("query_trace", "{\"serialNo\":\"TX-100\"}", context(Set.of("trace:query"))));
    }

    private ToolExecutionService service(AgentTool<?, ?> tool) {
        return new ToolExecutionService(new ToolRegistry(List.of(tool)), binder);
    }

    private AgentExecutionContext context(Set<String> permissions) {
        return new AgentExecutionContext("run-1", new AgentActor(1L, "tester", false, permissions),
                Instant.now().plusSeconds(30), new CancellationToken());
    }

    private record TraceInput(@NotBlank String serialNo) {
    }

    private static final class TestTool implements AgentTool<TraceInput, String> {
        private final ApprovalPolicy approval;

        private TestTool(ApprovalPolicy approval) {
            this.approval = approval;
        }

        @Override
        public ToolDescriptor descriptor() {
            return new ToolDescriptor("query_trace", "按流水号查询已验证事实",
                    Map.of("type", "object", "properties", Map.of("serialNo", Map.of("type", "string")),
                            "required", List.of("serialNo")),
                    ToolRisk.READ_ONLY, approval, Set.of("trace:query"), Duration.ofSeconds(5));
        }

        @Override
        public Class<TraceInput> inputType() {
            return TraceInput.class;
        }

        @Override
        public ToolResult<String> execute(TraceInput input, AgentExecutionContext context) {
            context.checkpoint();
            return ToolResult.of("已查询流水号 " + input.serialNo(), input.serialNo());
        }
    }
}
