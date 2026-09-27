package org.lbl.agent.controller;

import org.lbl.agent.config.AgentProperties;
import org.lbl.agent.core.AgentService;
import org.lbl.agent.core.model.AgentModels.AgentContext;
import org.lbl.agent.core.model.AgentModels.AgentEvent;
import org.lbl.agent.core.model.AgentModels.ChatRequest;
import org.lbl.security.context.AccessPolicy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * AI 助手聊天入口（SSE 流式）。
 * <p>
 * 为什么用 SSE 而不是 WebSocket：LLM 是"单向事件流"，SSE（{@code SseEmitter}）更简单，
 * 且能天然复用现有 JWT 鉴权链路。前端用原生 fetch 读流（EventSource 不能带 Authorization 头），
 * 见 {@code components/ai/useAgentChat.ts}。
 * <p>
 * 关键实现点：必须在<b>请求线程</b>上解析用户上下文（{@code access.actor()}），再把 agent 循环
 * 丢到 {@code agentExecutor} 后台线程跑。这样 SSE 的 {@code send()} 发生在控制器返回之后，
 * 事件才能真正边执行边推给前端（返回前同步 send 会被 Spring 缓冲成一次性 flush）。
 * <p>
 * 鉴权说明：本接口处于 {@code anyRequest().authenticated()} 覆盖下（WebSecurityConfig 只放行了
 * {@code /api/auth/**}），"必须登录"已被框架兜住。骨架阶段不叠加 {@code @PreAuthorize}，
 * 硬化时在此加 {@code @PreAuthorize("hasAuthority('agent:chat')")} 并注册该权限码，
 * 再配合每个 Capability 的 {@code requiredPermissions()} 做能力级控制。
 */
@RestController
@RequestMapping("/api/agent")
public class AgentChatController {
    private final AgentService agent;
    private final AgentProperties properties;
    private final AccessPolicy access;
    private final ThreadPoolTaskExecutor agentExecutor;

    public AgentChatController(AgentService agent, AgentProperties properties, AccessPolicy access,
                               @Qualifier("agentExecutor") ThreadPoolTaskExecutor agentExecutor) {
        this.agent = agent;
        this.properties = properties;
        this.access = access;
        this.agentExecutor = agentExecutor;
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(properties.sseTimeoutMillis());

        if (!properties.enabled()) {
            emit(emitter, AgentEvent.error("AI 助手功能未开启（lbl.agent.enabled=false）"));
            quietComplete(emitter);
            return emitter;
        }

        // ★ 在请求线程上解析当前用户（权限 + 超管标记），再显式传入后台线程。
        // 后台线程不依赖 SecurityContext 的线程本地变量，跨线程安全。
        AccessPolicy.Actor actor = access.actor();
        AgentContext context = new AgentContext(actor.user().getId(), actor.user().getUsername(),
                actor.superAdmin(), actor.permissions());

        agentExecutor.execute(() -> {
            try {
                agent.run(request, context, event -> emit(emitter, event));
                emit(emitter, AgentEvent.done());
                quietComplete(emitter);
            } catch (Exception ex) {
                emit(emitter, AgentEvent.error(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
                quietComplete(emitter);
            }
        });
        return emitter;
    }

    private void emit(SseEmitter emitter, AgentEvent event) {
        try {
            emitter.send(SseEmitter.event().name("agent").data(event));
        } catch (IOException | IllegalStateException ignored) {
            // 客户端已断开：忽略，后续 send 继续失败并被这里吞掉，最终由 quietComplete 收尾。
        }
    }

    private void quietComplete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (IllegalStateException ignored) {
            // 已因客户端断开而完成/过期，忽略。
        }
    }
}
