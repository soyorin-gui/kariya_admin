package org.lbl.system.log.risk.model;

/** 风险严重程度；priority 越小，展示优先级越高。 */
public enum AuditRiskSeverity {
    CRITICAL(0), HIGH(1), MEDIUM(2), LOW(3);

    private final int priority;

    AuditRiskSeverity(int priority) {
        this.priority = priority;
    }

    public int priority() {
        return priority;
    }
}
