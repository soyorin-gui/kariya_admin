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

/** 同一账号在短时间内多次登录失败或被锁定。 */
@Component
public class LoginFailureBurstRule implements AuditRiskRule {
    public static final String CODE = "LOGIN_FAILURE_BURST";
    private static final String NAME = "账号短时间连续登录失败";

    private final AuditRiskPolicy policy;

    public LoginFailureBurstRule(AuditRiskPolicy policy) {
        this.policy = policy;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public List<AuditRiskFinding> evaluate(AuditRiskContext context) {
        WindowRiskPolicy rule = policy.loginFailureBurst();
        if (!rule.enabled()) return List.of();
        List<LoginRiskEvent> rejected = context.loginEvents().stream()
                .filter(event -> LogResult.FAILURE.equals(event.getResult()) || LogResult.LOCKED.equals(event.getResult()))
                .toList();
        return SlidingWindowDetector.countBursts(rejected,
                        event -> RiskRuleSupport.actorKey(event.getUserId(), event.getUsername()),
                        LoginRiskEvent::getLoginTime, rule.window(), rule.threshold())
                .stream().map(match -> finding(match.events(), rule)).toList();
    }

    private AuditRiskFinding finding(List<LoginRiskEvent> events, WindowRiskPolicy rule) {
        LoginRiskEvent first = events.get(0);
        LoginRiskEvent last = events.get(events.size() - 1);
        long failureCount = events.stream().filter(event -> LogResult.FAILURE.equals(event.getResult())).count();
        long lockedCount = events.stream().filter(event -> LogResult.LOCKED.equals(event.getResult())).count();
        long distinctIpCount = events.stream().map(LoginRiskEvent::getLoginIp)
                .filter(value -> value != null && !value.isBlank()).distinct().count();
        String username = RiskRuleSupport.display(first.getUsername());
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("failureCount", failureCount);
        facts.put("lockedCount", lockedCount);
        facts.put("distinctIpCount", distinctIpCount);
        facts.put("windowMinutes", rule.window().toMinutes());
        return new AuditRiskFinding(CODE, NAME, rule.severity(), "ACCOUNT",
                RiskRuleSupport.subjectId(first.getUserId(), username), username,
                "账号 %s 在 %d 分钟窗口内出现 %d 次未成功登录".formatted(
                        username, rule.window().toMinutes(), events.size()),
                events.size(), rule.threshold(), first.getLoginTime(), last.getLoginTime(),
                RiskRuleSupport.evidenceIds(events, LoginRiskEvent::getId, policy.maxEvidenceIds()), facts);
    }
}
