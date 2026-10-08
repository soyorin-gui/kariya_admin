package org.lbl.agent.domain.report;

/** 通用报告状态，只表达展示语义，不替代业务领域状态。 */
public enum AgentReportStatus {
    INFO,
    SUCCESS,
    WARNING,
    HIGH,
    CRITICAL
}
