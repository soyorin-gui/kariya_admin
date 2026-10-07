package org.lbl.system.log.risk.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 一条可解释、可回查证据的确定性风险命中。 */
public record AuditRiskFinding(
        String ruleCode,
        String ruleName,
        AuditRiskSeverity severity,
        String subjectType,
        String subjectId,
        String subjectName,
        String summary,
        long actualValue,
        long threshold,
        LocalDateTime firstSeen,
        LocalDateTime lastSeen,
        List<Long> evidenceLogIds,
        Map<String, Object> facts) {

    public AuditRiskFinding {
        evidenceLogIds = evidenceLogIds == null ? List.of() : List.copyOf(evidenceLogIds);
        facts = facts == null ? Map.of() : Map.copyOf(facts);
    }
}
