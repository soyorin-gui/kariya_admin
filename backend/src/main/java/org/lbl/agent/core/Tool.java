package org.lbl.agent.core;

import org.lbl.agent.core.model.AgentModels.AgentContext;
import org.lbl.agent.core.model.AgentModels.ToolResult;

import java.util.Map;

/**
 * 一个可供 LLM 通过 function calling 调用的工具。
 * <p>
 * 工具是 agent 与确定性能力之间的唯一契约：LLM 只负责"决定调谁、传什么参数"，
 * 真正的执行逻辑（查日志、跑对比、生成文档）在这里、由确定性代码完成。
 * <p>
 * 实现要点：
 * <ul>
 *   <li>{@link #description()} 是写给 LLM 看的，必须说清楚"什么时候用、入参什么意思"，它直接决定模型会不会用错；</li>
 *   <li>{@link #parameters()} 返回该工具入参的 JSON Schema（object 根），用于告诉模型参数的形状；</li>
 *   <li>{@link #execute(Map, AgentContext)} 的返回值里，summary 要给足"下一步提示"，别让模型自由发挥。</li>
 * </ul>
 */
public interface Tool {

    /** 全局唯一工具名（function name），例如 {@code compare_systems}。 */
    String name();

    /** 给 LLM 的工具说明，越具体越好。 */
    String description();

    /** 入参 JSON Schema（object 根），例如 {"type":"object","properties":{...},"required":[...]}。 */
    Map<String, Object> parameters();

    /** 执行工具。arguments 是模型填的参数（已从 JSON 字符串解析成 Map）。 */
    ToolResult execute(Map<String, Object> arguments, AgentContext context);
}
