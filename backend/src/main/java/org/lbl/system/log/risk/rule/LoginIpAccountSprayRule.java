package org.lbl.system.log.risk.rule;

import org.lbl.system.log.risk.AuditRiskContext;
import org.lbl.system.log.risk.AuditRiskRule;
import org.lbl.system.log.risk.config.AuditRiskPolicy;
import org.lbl.system.log.risk.config.WindowRiskPolicy;
import org.lbl.system.log.risk.model.AuditRiskFinding;
import org.lbl.system.log.risk.model.LoginRiskEvent;
import org.lbl.system.log.risk.support.RiskRuleSupport;
import org.lbl.system.log.risk.support.SlidingWindowDetector;
import org.lbl.system.log.support.LogResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 同一 IP 在短时间内尝试大量不同账号，属于疑似账号枚举或撞库信号。 */
@Component
public class LoginIpAccountSprayRule implements AuditRiskRule {
    public static final String CODE = "LOGIN_IP_ACCOUNT_SPRAY";
    private static final String NAME = "同一 IP 短时间尝试多个账号";

    private final AuditRiskPolicy policy;

    public LoginIpAccountSprayRule(AuditRiskPolicy policy) {
        this.policy = policy;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public List<AuditRiskFinding> evaluate(AuditRiskContext context) {
        WindowRiskPolicy rule = policy.loginIpAccountSpray();
        if (!rule.enabled()) return List.of();
        List<LoginRiskEvent> withIdentity = context.loginEvents().stream()
                .filter(event -> event.getLoginIp() != null && !event.getLoginIp().isBlank())
                .filter(event -> event.getUsername() != null && !event.getUsername().isBlank())
                .toList();
        return SlidingWindowDetector.distinctBursts(withIdentity,
                        event -> event.getLoginIp().trim(), LoginRiskEvent::getLoginTime,
                        event -> event.getUsername().trim(), rule.window(), rule.threshold())
                .stream().map(match -> finding(match.events(), rule)).toList();
    }

    private AuditRiskFinding finding(List<LoginRiskEvent> events, WindowRiskPolicy rule) {
        LoginRiskEvent first = events.get(0);
        LoginRiskEvent last = events.get(events.size() - 1);
        List<String> accounts = events.stream().map(LoginRiskEvent::getUsername).map(String::trim).distinct().toList();
        long successCount = events.stream().filter(event -> LogResult.SUCCESS.equals(event.getResult())).count();
        long rejectedCount = events.stream()
                .filter(event -> LogResult.FAILURE.equals(event.getResult()) || LogResult.LOCKED.equals(event.getResult()))
                .count();
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("attemptCount", events.size());
        facts.put("successCount", successCount);
        facts.put("rejectedCount", rejectedCount);
        facts.put("accountSamples", accounts.stream().limit(10).toList());
        facts.put("windowMinutes", rule.window().toMinutes());
        String ip = first.getLoginIp().trim();
        return new AuditRiskFinding(CODE, NAME, rule.severity(), "IP", ip, ip,
                "IP %s 在 %d 分钟窗口内尝试登录 %d 个不同账号".formatted(
                        ip, rule.window().toMinutes(), accounts.size()),
                accounts.size(), rule.threshold(), first.getLoginTime(), last.getLoginTime(),
                RiskRuleSupport.evidenceIds(events, LoginRiskEvent::getId, policy.maxEvidenceIds()), facts);
    }
}
