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

/** 管理员短时间频繁重置用户密码。 */
@Component
public class PasswordResetBurstRule implements AuditRiskRule {
    public static final String CODE = "PASSWORD_RESET_BURST";
    private static final String NAME = "频繁重置用户密码";

    private final AuditRiskPolicy policy;

    public PasswordResetBurstRule(AuditRiskPolicy policy) {
        this.policy = policy;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public List<AuditRiskFinding> evaluate(AuditRiskContext context) {
        WindowRiskPolicy rule = policy.passwordResetBurst();
        if (!rule.enabled()) return List.of();
        List<OperationRiskEvent> candidates = context.operationEvents().stream()
                .filter(event -> AuditOperationActions.USER_MANAGEMENT.equals(event.getModule()))
                .filter(event -> AuditOperationActions.PASSWORD_RESET.equals(event.getAction()))
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
        long distinctTargetCount = events.stream().map(OperationRiskEvent::getTargetId)
                .filter(value -> value != null && !value.isBlank()).distinct().count();
        String username = RiskRuleSupport.display(first.getUsername());
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("successCount", successCount);
        facts.put("failureCount", failureCount);
        facts.put("distinctTargetCount", distinctTargetCount);
        facts.put("windowMinutes", rule.window().toMinutes());
        return new AuditRiskFinding(CODE, NAME, rule.severity(), "OPERATOR",
                RiskRuleSupport.subjectId(first.getUserId(), username), username,
                "操作人 %s 在 %d 分钟窗口内发起 %d 次重置密码操作，涉及 %d 个用户".formatted(
                        username, rule.window().toMinutes(), events.size(), distinctTargetCount),
                events.size(), rule.threshold(), first.getCreatedTime(), last.getCreatedTime(),
                RiskRuleSupport.evidenceIds(events, OperationRiskEvent::getId, policy.maxEvidenceIds()), facts);
    }
}
