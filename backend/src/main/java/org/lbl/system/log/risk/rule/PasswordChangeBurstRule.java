package org.lbl.system.log.risk.rule;

import org.lbl.system.log.risk.AuditRiskContext;
import org.lbl.system.log.risk.AuditRiskRule;
import org.lbl.system.log.risk.config.AuditRiskPolicy;
import org.lbl.system.log.risk.config.WindowRiskPolicy;
import org.lbl.system.log.risk.model.AuditRiskFinding;
import org.lbl.system.log.risk.model.OperationRiskEvent;
import org.lbl.system.log.risk.support.AuditOperationActions;
import org.lbl.system.log.risk.support.RiskRuleSupport;
import org.lbl.system.log.risk.support.SlidingWindowDetector;
import org.lbl.system.log.support.LogResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 同一用户短时间频繁修改自己的密码。 */
@Component
public class PasswordChangeBurstRule implements AuditRiskRule {
    public static final String CODE = "PASSWORD_CHANGE_BURST";
    private static final String NAME = "频繁修改密码";

    private final AuditRiskPolicy policy;

    public PasswordChangeBurstRule(AuditRiskPolicy policy) {
        this.policy = policy;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public List<AuditRiskFinding> evaluate(AuditRiskContext context) {
        WindowRiskPolicy rule = policy.passwordChangeBurst();
        if (!rule.enabled()) return List.of();
        List<OperationRiskEvent> candidates = context.operationEvents().stream()
                .filter(event -> AuditOperationActions.PERSONAL_CENTER.equals(event.getModule()))
                .filter(event -> AuditOperationActions.PASSWORD_CHANGE.equals(event.getAction()))
                .toList();
        return SlidingWindowDetector.countBursts(candidates,
                        event -> RiskRuleSupport.actorKey(event.getUserId(), event.getUsername()),
                        OperationRiskEvent::getCreatedTime, rule.window(), rule.threshold())
                .stream().map(match -> finding(match.events(), rule)).toList();
    }

    private AuditRiskFinding finding(List<OperationRiskEvent> events, WindowRiskPolicy rule) {
        OperationRiskEvent first = events.get(0);
        OperationRiskEvent last = events.get(events.size() - 1);
        long successCount = events.stream().filter(event -> LogResult.SUCCESS.equals(event.getResult())).count();
        long failureCount = events.stream().filter(event -> LogResult.FAILURE.equals(event.getResult())).count();
        String username = RiskRuleSupport.display(first.getUsername());
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("successCount", successCount);
        facts.put("failureCount", failureCount);
        facts.put("windowHours", rule.window().toHours());
        return new AuditRiskFinding(CODE, NAME, rule.severity(), "USER",
                RiskRuleSupport.subjectId(first.getUserId(), username), username,
                "用户 %s 在 %d 小时窗口内发起 %d 次修改密码操作".formatted(
                        username, rule.window().toHours(), events.size()),
                events.size(), rule.threshold(), first.getCreatedTime(), last.getCreatedTime(),
                RiskRuleSupport.evidenceIds(events, OperationRiskEvent::getId, policy.maxEvidenceIds()), facts);
    }
}
