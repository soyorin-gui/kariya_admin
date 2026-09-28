package org.lbl.agent.port;

import org.lbl.agent.domain.AgentExecutionContext;
import org.lbl.agent.domain.AgentRun;
import org.lbl.agent.tool.ToolDescriptor;

/**
 * 审计与指标扩展点。观察者不得改变运行结果，也不得把未脱敏的提示词或工具参数写入日志。
 * 没有实现时运行器照常工作。
 */
public interface AgentRunObserver {
    default void onStarted(AgentExecutionContext context) {
    }

    default void onToolStarted(AgentExecutionContext context, ToolDescriptor tool) {
    }

    default void onCompleted(AgentExecutionContext context, AgentRun run) {
    }

    default void onFailed(AgentExecutionContext context, Throwable error) {
    }
}
