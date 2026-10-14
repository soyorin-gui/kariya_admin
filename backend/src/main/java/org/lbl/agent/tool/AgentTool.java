package org.lbl.agent.tool;

import org.lbl.agent.domain.AgentExecutionContext;

/**
 * 业务能力接入 Agent 的唯一接口。
 *
 * <p>实现类应当放在所属业务模块的 {@code adapter.agent} 包，只做参数适配、调用业务用例和包装结果；
 * 不得在工具里实现核心业务算法，也不得读取 Controller/SecurityContext。</p>
 */
public interface AgentTool<I, O> {
    ToolDescriptor descriptor();

    Class<I> inputType();

    ToolResult<O> execute(I input, AgentExecutionContext context);
}
