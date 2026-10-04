package org.lbl.realtime;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Redis 短暂不可用时不让整个 Web 应用启动失败；后台重试订阅，健康检查负责阻止流量进入。
 */
@Component
public class RealtimeSubscriptionStarter {
    private static final Logger log = LoggerFactory.getLogger(RealtimeSubscriptionStarter.class);
    private final RedisMessageListenerContainer container;
    private final AtomicBoolean failureLogged = new AtomicBoolean();

    public RealtimeSubscriptionStarter(RedisConnectionFactory connections, RealtimeEventSubscriber subscriber) {
        // 不注册成 Spring Lifecycle Bean，避免 Redis 暂时不可用时上下文刷新阶段直接失败。
        this.container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connections);
        container.addMessageListener(subscriber, new ChannelTopic(RealtimeEventPublisher.CHANNEL));
        container.afterPropertiesSet();
    }

    @Scheduled(initialDelay = 1_000, fixedDelay = 5_000)
    public void ensureStarted() {
        if (container.isRunning()) return;
        try {
            container.start();
            if (failureLogged.getAndSet(false)) log.info("Realtime Redis subscription recovered");
        } catch (RuntimeException ex) {
            if (failureLogged.compareAndSet(false, true))
                log.warn("Realtime Redis subscription is unavailable; retrying in background: {}", ex.getMessage());
        }
    }

    @PreDestroy
    public void close() throws Exception {
        container.stop();
        container.destroy();
    }
}
