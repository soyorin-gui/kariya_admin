package org.lbl.agent.api;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.lbl.agent.api.model.AgentChatRequest;
import org.lbl.agent.application.AgentCommand;
import org.lbl.agent.application.AgentRequestMapper;
import org.lbl.agent.application.AgentRunner;
import org.lbl.agent.config.AgentProperties;
import org.lbl.agent.domain.AgentActor;
import org.lbl.agent.domain.AgentEvent;
import org.lbl.agent.domain.AgentExecutionContext;
import org.lbl.agent.domain.CancellationToken;
import org.lbl.security.context.AccessPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** HTTP/SSE 输入适配器。这里只做校验、身份快照、异步生命周期和事件传输。 */
@RestController
@RequestMapping("/api/agent")
public class AgentChatController {
    private static final Logger log = LoggerFactory.getLogger(AgentChatController.class);

    private final AgentRunner runner;
    private final AgentRequestMapper requests;
    private final AgentProperties properties;
    private final AccessPolicy access;
    private final ThreadPoolTaskExecutor executor;

    public AgentChatController(AgentRunner runner, AgentRequestMapper requests, AgentProperties properties,
                               AccessPolicy access, @Qualifier("agentExecutor") ThreadPoolTaskExecutor executor) {
        this.runner = runner;
        this.requests = requests;
        this.properties = properties;
        this.access = access;
        this.executor = executor;
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('agent:chat:use')")
    public SseEmitter chat(@Valid @RequestBody AgentChatRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");

        String runId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(properties.sseTimeoutMillis());

        AccessPolicy.Actor current = access.actor();
        AgentActor actor = new AgentActor(current.user().getId(), current.user().getUsername(),
                current.superAdmin(), current.permissions());
        CancellationToken cancellation = new CancellationToken();
        AgentExecutionContext context = new AgentExecutionContext(runId, actor,
                Instant.now().plus(properties.runTimeoutSeconds(), ChronoUnit.SECONDS), cancellation);
        AgentCommand command = requests.toCommand(request);

        AtomicReference<Future<?>> task = new AtomicReference<>();
        AtomicBoolean completedNormally = new AtomicBoolean();
        Runnable cancel = () -> {
            cancellation.cancel();
            Future<?> running = task.get();
            if (running != null) running.cancel(true);
        };
        emitter.onTimeout(cancel);
        emitter.onError(ignored -> cancel.run());
        emitter.onCompletion(() -> {
            if (!completedNormally.get()) cancel.run();
        });

        try {
            Future<?> submitted = executor.submit(() -> run(command, context, emitter, completedNormally));
            task.set(submitted);
            if (cancellation.isCancelled()) submitted.cancel(true);
        } catch (TaskRejectedException ex) {
            log.warn("Agent executor rejected runId={}", runId);
            emit(emitter, AgentEvent.error(runId, "AI 助手当前请求较多，请稍后重试"));
            completeQuietly(emitter);
        }
        return emitter;
    }

    private void run(AgentCommand command, AgentExecutionContext context, SseEmitter emitter,
                     AtomicBoolean completedNormally) {
        try {
            runner.run(command, context, event -> {
                if (!emit(emitter, event)) {
                    context.cancellation().cancel();
                    throw new CancellationException("SSE client disconnected");
                }
            });
            if (!context.cancellation().isCancelled()) {
                emit(emitter, AgentEvent.done(context.runId()));
                completedNormally.set(true);
                completeQuietly(emitter);
            }
        } catch (CancellationException ignored) {
            // 浏览器关闭、SSE 超时或调用线程被取消：不再尝试向失效连接发送事件。
        } catch (Exception ex) {
            log.warn("Agent run failed runId={}", context.runId(), ex);
            if (!context.cancellation().isCancelled()) {
                emit(emitter, AgentEvent.error(context.runId(), "AI 助手暂时无法完成请求，请稍后重试"));
                completedNormally.set(true);
                completeQuietly(emitter);
            }
        }
    }

    private boolean emit(SseEmitter emitter, AgentEvent event) {
        try {
            emitter.send(SseEmitter.event().name("agent").data(event));
            return true;
        } catch (IOException | IllegalStateException ex) {
            return false;
        }
    }

    private void completeQuietly(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (IllegalStateException ignored) {
            // 已完成或已超时。
        }
    }
}
