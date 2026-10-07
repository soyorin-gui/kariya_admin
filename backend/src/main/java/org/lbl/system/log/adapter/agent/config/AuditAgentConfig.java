package org.lbl.system.log.adapter.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.time.ZoneId;

/** 小阶段三的 Java 配置：时区、默认返回量和下钻边界均不写入 YAML。 */
@Configuration
public class AuditAgentConfig {

    @Bean
    public AuditAgentPolicy auditAgentPolicy() {
        return new AuditAgentPolicy(
                ZoneId.of("Asia/Shanghai"),
                10,
                50,
                100,
                Duration.ofHours(24));
    }
}
