package org.lbl.agent.domain.report;

import java.util.List;
import java.util.Map;

/** 通用、可版本化的 UI 报告；业务模块只组装数据，前端公共组件负责展示。 */
public record AgentReport(
        AgentReportStatus status,
        String summary,
        Map<String, Object> metadata,
        List<AgentReportSection> sections) {

    public AgentReport {
        status = status == null ? AgentReportStatus.INFO : status;
        summary = summary == null ? "" : summary;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        sections = sections == null ? List.of() : List.copyOf(sections);
    }
}
