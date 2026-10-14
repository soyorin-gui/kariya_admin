package org.lbl.agent.tool;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * 模型可见且执行器可强制执行的工具元数据。
 *
 * <p>JSON Schema 是跨模型协议；{@link AgentTool#inputType()} 才是 Java 执行时类型。
 * 两者都必须存在：Schema 约束模型，类型绑定与 Bean Validation 约束服务端。</p>
 */
public record ToolDescriptor(
        String name,
        String description,
        Map<String, Object> inputSchema,
        ToolRisk risk,
        ApprovalPolicy approval,
        Set<String> permissions,
        Duration timeout) {

    public ToolDescriptor {
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
        inputSchema = inputSchema == null ? Map.of("type", "object", "properties", Map.of()) : Map.copyOf(inputSchema);
        risk = risk == null ? ToolRisk.READ_ONLY : risk;
        approval = approval == null ? ApprovalPolicy.NOT_REQUIRED : approval;
        timeout = timeout == null ? Duration.ofSeconds(30) : timeout;
    }
}
