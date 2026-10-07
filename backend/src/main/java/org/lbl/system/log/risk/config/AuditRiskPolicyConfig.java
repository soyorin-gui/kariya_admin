package org.lbl.system.log.risk.config;

import org.lbl.system.log.risk.model.AuditRiskSeverity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 审计风险规则的代码配置。
 *
 * <p>这些阈值是当前产品规则，需要修改时走代码评审和发布；密钥、地址等运行时配置不在这里。</p>
 */
@Configuration
public class AuditRiskPolicyConfig {

    @Bean
    public AuditRiskPolicy auditRiskPolicy() {
        return new AuditRiskPolicy(
                Duration.ofHours(24),
                50_000,
                100,
                20,
                new WindowRiskPolicy(true, Duration.ofMinutes(10), 5, AuditRiskSeverity.HIGH),
                new WindowRiskPolicy(true, Duration.ofMinutes(10), 10, AuditRiskSeverity.HIGH),
                new WindowRiskPolicy(true, Duration.ofHours(24), 3, AuditRiskSeverity.MEDIUM),
                new WindowRiskPolicy(true, Duration.ofHours(1), 5, AuditRiskSeverity.HIGH));
    }
}
