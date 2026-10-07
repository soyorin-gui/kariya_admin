package org.lbl.system.log.risk.rule;

import org.junit.jupiter.api.Test;
import org.lbl.system.log.risk.AuditRiskContext;
import org.lbl.system.log.risk.config.AuditRiskPolicy;
import org.lbl.system.log.risk.config.WindowRiskPolicy;
import org.lbl.system.log.risk.model.AuditRiskSeverity;
import org.lbl.system.log.risk.model.LoginRiskEvent;
import org.lbl.system.log.risk.model.OperationRiskEvent;
import org.lbl.system.log.risk.support.AuditOperationActions;
import org.lbl.system.log.support.LogResult;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditRiskRulesTest {
    private static final LocalDateTime BASE = LocalDateTime.of(2026, 10, 5, 9, 0);
    private final AuditRiskPolicy policy = policy();

    @Test
    void loginFailureRuleCombinesFailureAndLocked() {
        List<LoginRiskEvent> events = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            events.add(login(index + 1, "zhangsan", "10.0.0.1", LogResult.FAILURE, index));
        }
        events.add(login(5, "zhangsan", "10.0.0.2", LogResult.LOCKED, 5));

        var findings = new LoginFailureBurstRule(policy).evaluate(context(events, List.of()));

        assertEquals(1, findings.size());
        assertEquals(5, findings.get(0).actualValue());
        assertEquals(1L, findings.get(0).facts().get("lockedCount"));
        assertEquals(2L, findings.get(0).facts().get("distinctIpCount"));
    }

    @Test
    void loginFailureRuleDoesNotMixDifferentAccounts() {
        List<LoginRiskEvent> events = List.of(
                login(1, "a", "10.0.0.1", LogResult.FAILURE, 0),
                login(2, "a", "10.0.0.1", LogResult.FAILURE, 1),
                login(3, "a", "10.0.0.1", LogResult.FAILURE, 2),
                login(4, "b", "10.0.0.1", LogResult.FAILURE, 3),
                login(5, "b", "10.0.0.1", LogResult.FAILURE, 4));

        assertTrue(new LoginFailureBurstRule(policy).evaluate(context(events, List.of())).isEmpty());
    }

    @Test
    void ipSprayRuleCountsDistinctAccounts() {
        List<LoginRiskEvent> events = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            events.add(login(index + 1, "user" + index, "10.0.0.8", LogResult.FAILURE, index));
        }

        var findings = new LoginIpAccountSprayRule(policy).evaluate(context(events, List.of()));

        assertEquals(1, findings.size());
        assertEquals(10, findings.get(0).actualValue());
    }

    @Test
    void passwordChangeRuleMatchesThreeAttempts() {
        List<OperationRiskEvent> events = List.of(
                operation(1, 7L, "operator", AuditOperationActions.PERSONAL_CENTER,
                        AuditOperationActions.PASSWORD_CHANGE, null, LogResult.SUCCESS, 0),
                operation(2, 7L, "operator", AuditOperationActions.PERSONAL_CENTER,
                        AuditOperationActions.PASSWORD_CHANGE, null, LogResult.FAILURE, 30),
                operation(3, 7L, "operator", AuditOperationActions.PERSONAL_CENTER,
                        AuditOperationActions.PASSWORD_CHANGE, null, LogResult.SUCCESS, 60));

        var findings = new PasswordChangeBurstRule(policy).evaluate(context(List.of(), events));

        assertEquals(1, findings.size());
        assertEquals(2L, findings.get(0).facts().get("successCount"));
        assertEquals(1L, findings.get(0).facts().get("failureCount"));
    }

    @Test
    void passwordResetRuleIncludesDistinctTargets() {
        List<OperationRiskEvent> events = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            events.add(operation(index + 1, 9L, "admin", AuditOperationActions.USER_MANAGEMENT,
                    AuditOperationActions.PASSWORD_RESET, String.valueOf(100 + index), LogResult.SUCCESS, index * 5L));
        }

        var findings = new PasswordResetBurstRule(policy).evaluate(context(List.of(), events));

        assertEquals(1, findings.size());
        assertEquals(5L, findings.get(0).facts().get("distinctTargetCount"));
    }

    private AuditRiskContext context(List<LoginRiskEvent> login, List<OperationRiskEvent> operation) {
        return new AuditRiskContext(BASE, BASE.plusDays(1), login, operation);
    }

    private LoginRiskEvent login(long id, String username, String ip, String result, long minute) {
        LoginRiskEvent event = new LoginRiskEvent();
        event.setId(id);
        event.setUserId((long) username.hashCode());
        event.setUsername(username);
        event.setLoginIp(ip);
        event.setResult(result);
        event.setLoginTime(BASE.plusMinutes(minute));
        return event;
    }

    private OperationRiskEvent operation(long id, Long userId, String username, String module, String action,
                                         String targetId, String result, long minute) {
        OperationRiskEvent event = new OperationRiskEvent();
        event.setId(id);
        event.setUserId(userId);
        event.setUsername(username);
        event.setModule(module);
        event.setAction(action);
        event.setTargetId(targetId);
        event.setResult(result);
        event.setCreatedTime(BASE.plusMinutes(minute));
        return event;
    }

    private AuditRiskPolicy policy() {
        return new AuditRiskPolicy(Duration.ofHours(24), 50_000, 100, 20,
                new WindowRiskPolicy(true, Duration.ofMinutes(10), 5, AuditRiskSeverity.HIGH),
                new WindowRiskPolicy(true, Duration.ofMinutes(10), 10, AuditRiskSeverity.HIGH),
                new WindowRiskPolicy(true, Duration.ofHours(24), 3, AuditRiskSeverity.MEDIUM),
                new WindowRiskPolicy(true, Duration.ofHours(1), 5, AuditRiskSeverity.HIGH));
    }
}
