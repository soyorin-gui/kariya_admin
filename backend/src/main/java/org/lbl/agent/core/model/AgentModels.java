package org.lbl.agent.core.model;

import org.lbl.agent.llm.model.OpenAiModels.ChatMessage;

import java.util.List;
import java.util.Set;

/**
 * Agent 核心层共用的数据模型（刻意集中在一个文件，与 {@code DepartmentChangeModels} 同类约定）。
 */
public final class AgentModels {
    private AgentModels() {
    }

    /**
     * 一次 agent 执行中的"当前用户上下文"。
     * <p>
     * 在 Controller 的请求线程上从 {@code AccessPolicy.Actor} 构造，再显式传入执行链——
     * 这样 agent 循环即使被挪到其它线程，也不依赖 SecurityContext 的线程本地变量。
     *
     * @param userId      当前用户 id
     * @param username    当前用户名
     * @param superAdmin  是否超管（超管自动拥有所有能力权限）
     * @param permissions 当前用户权限码集合（与 {@code AccessPolicy.Actor.permissions()} 同口径）
     */
    public record AgentContext(Long userId, String username, boolean superAdmin, Set<String> permissions) {
        public boolean has(String permission) {
            return superAdmin || permissions.contains(permission);
        }

        /** 空集合 = 任何已登录用户可用；否则需要拥有全部权限码。 */
        public boolean hasAll(Set<String> required) {
            return required == null || required.isEmpty() || required.stream().allMatch(this::has);
        }
    }

    /**
     * 工具执行结果。
     *
     * @param kind    内容形态（TEXT/DIFF/FILE/GRAPH），决定前端渲染器
     * @param summary 给 LLM 看的结构化摘要（短、可判断，绝不要塞原始大报文）
     * @param payload 给前端看的完整数据（diff 明细 / 文件 token / 图数据），LLM 不读它
     */
    public record ToolResult(ContentKind kind, String summary, Object payload) {
        public static ToolResult text(String summary) {
            return new ToolResult(ContentKind.TEXT, summary, null);
        }

        public static ToolResult diff(String summary, Object payload) {
            return new ToolResult(ContentKind.DIFF, summary, payload);
        }

        public static ToolResult file(String summary, Object payload) {
            return new ToolResult(ContentKind.FILE, summary, payload);
        }

        public static ToolResult graph(String summary, Object payload) {
            return new ToolResult(ContentKind.GRAPH, summary, payload);
        }
    }

    /**
     * 通过 SSE 推给前端的事件。type 是判别字段，其余字段按 type 选择性填充。
     * <p>
     * 支持的类型：{@code message}（最终/中间文本）、{@code tool_call}（开始调某工具）、
     * {@code tool_result}（工具出结果）、{@code error}、{@code done}（结束）。
     */
    public record AgentEvent(String type, String toolName, ContentKind kind,
                             String text, String summary, Object payload) {
        public static AgentEvent message(String text) {
            return new AgentEvent("message", null, null, text, null, null);
        }

        public static AgentEvent toolCall(String toolName) {
            return new AgentEvent("tool_call", toolName, null, null, null, null);
        }

        public static AgentEvent toolResult(String toolName, ToolResult result) {
            return new AgentEvent("tool_result", toolName, result.kind(), null, result.summary(), result.payload());
        }

        public static AgentEvent error(String text) {
            return new AgentEvent("error", null, null, text, null, null);
        }

        public static AgentEvent done() {
            return new AgentEvent("done", null, null, null, null, null);
        }
    }

    /**
     * 一次 agent 执行的最终产物：LLM 给出的最终文本 + 本轮执行过程中产生的所有工具结果。
     */
    public record AgentRun(String text, List<ToolResult> toolResults) {
    }

    /**
     * 前端发来的聊天请求。history 是多轮上下文（不含 system 提示，由后端组装）。
     */
    public record ChatRequest(String message, List<ChatMessage> history) {
    }
}
