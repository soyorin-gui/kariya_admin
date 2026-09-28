package org.lbl.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Agent 专用的异步执行器。
 * <p>
 * LLM 调用是慢速网络 IO，不能占用 Tomcat 请求线程；同时 SSE 的 {@code send()} 必须在
 * 控制器方法返回之后才由后台线程调用（返回前调用会被 Spring 缓冲到返回时一次性 flush，
 * 起不到流式效果）。所以 agent 循环跑在这个专用线程池上。
 * <p>
 * 线程数与队列都有界。队列满时明确拒绝，由 Controller 返回“系统繁忙”；绝不能退回请求线程执行，
 * 否则慢速模型调用会占满 Tomcat 工作线程。
 */
@Configuration
public class AgentAsyncConfig {

    @Bean(name = "agentExecutor")
    public ThreadPoolTaskExecutor agentExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("agent-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
