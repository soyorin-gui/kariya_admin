package org.lbl.agent.domain.report;

/** 通用指标；format 由前端白名单格式化器解释。 */
public record AgentMetric(String label, Object value, String format, AgentReportStatus status) {
    public AgentMetric {
        if (label == null || label.isBlank()) throw new IllegalArgumentException("指标名称不能为空");
    }

    public static AgentMetric number(String label, Number value) {
        return new AgentMetric(label, value, "number", null);
    }
}
