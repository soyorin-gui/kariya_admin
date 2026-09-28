package org.lbl.agent.tool;

import org.lbl.agent.domain.AgentExecutionContext;
import org.lbl.common.exception.BusinessException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Instant;

/** 工具执行的唯一入口：再次鉴权、审批门禁、参数绑定和运行上下文检查都集中在这里。 */
@Service
public class ToolExecutionService {
    private final ToolRegistry registry;
    private final ToolArgumentBinder binder;

    public ToolExecutionService(ToolRegistry registry, ToolArgumentBinder binder) {
        this.registry = registry;
        this.binder = binder;
    }

    public ToolResult<?> execute(String toolName, String argumentsJson, AgentExecutionContext context) {
        context.checkpoint();
        AgentTool<?, ?> rawTool = registry.find(toolName);
        if (rawTool == null) throw new BusinessException("不存在可执行的工具：" + toolName);
        ToolDescriptor descriptor = rawTool.descriptor();
        if (!context.actor().hasAll(descriptor.permissions())) {
            throw new AccessDeniedException("没有使用工具 " + toolName + " 的权限");
        }
        if (descriptor.approval() == ApprovalPolicy.REQUIRED) {
            throw new BusinessException("工具 " + toolName + " 需要用户确认，当前请求尚未获得批准");
        }
        return executeTyped(rawTool, argumentsJson, context);
    }

    private <I, O> ToolResult<O> executeTyped(AgentTool<I, O> tool, String argumentsJson,
                                               AgentExecutionContext context) {
        I input = binder.bindJson(argumentsJson, tool.inputType());
        Instant ownDeadline = Instant.now().plus(tool.descriptor().timeout());
        AgentExecutionContext toolContext = new AgentExecutionContext(context.runId(), context.actor(),
                ownDeadline.isBefore(context.deadline()) ? ownDeadline : context.deadline(), context.cancellation());
        ToolResult<O> result = tool.execute(input, toolContext);
        toolContext.checkpoint();
        if (result == null || result.modelSummary() == null || result.modelSummary().isBlank()) {
            throw new IllegalStateException("工具 " + tool.descriptor().name() + " 未返回 modelSummary");
        }
        return result;
    }
}
