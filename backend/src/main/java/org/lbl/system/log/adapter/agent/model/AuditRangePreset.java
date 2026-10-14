package org.lbl.system.log.adapter.agent.model;

/** 模型只能从这些确定性时间范围中选择，避免自行计算“今天”。 */
public enum AuditRangePreset {
    TODAY,
    YESTERDAY,
    RECENT_HOURS,
    CUSTOM
}
