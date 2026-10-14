package org.lbl.agent.domain.report;

import java.util.List;
import java.util.Map;

/** 可复用于审计风险、调用异常、校验问题的通用发现项。 */
public record AgentFinding(
        String code,
        String title,
        AgentReportStatus severity,
        String summary,
        Map<String, Object> attributes,
        List<String> evidenceIds) {

    public AgentFinding {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("发现项标题不能为空");
        severity = severity == null ? AgentReportStatus.INFO : severity;
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
    }
}
