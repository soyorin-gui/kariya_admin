package org.lbl.agent.domain.report;

import java.util.List;
import java.util.Map;

/**
 * 通用报告区块。不同 type 只使用对应字段，避免向前端发送任意 HTML 或组件配置。
 * 支持 metrics/table/findings/timeline/text；chart 数据预留在 rows 中。
 */
public record AgentReportSection(
        String type,
        String title,
        List<AgentMetric> metrics,
        List<AgentTableColumn> columns,
        List<Map<String, Object>> rows,
        List<AgentFinding> findings,
        List<AgentTimelineItem> timeline,
        String text) {

    public AgentReportSection {
        if (type == null || type.isBlank()) throw new IllegalArgumentException("报告区块类型不能为空");
        metrics = metrics == null ? List.of() : List.copyOf(metrics);
        columns = columns == null ? List.of() : List.copyOf(columns);
        rows = rows == null ? List.of() : rows.stream().map(Map::copyOf).toList();
        findings = findings == null ? List.of() : List.copyOf(findings);
        timeline = timeline == null ? List.of() : List.copyOf(timeline);
    }

    public static AgentReportSection metrics(String title, List<AgentMetric> items) {
        return new AgentReportSection("metrics", title, items, null, null, null, null, null);
    }

    public static AgentReportSection table(String title, List<AgentTableColumn> columns,
                                           List<Map<String, Object>> rows) {
        return new AgentReportSection("table", title, null, columns, rows, null, null, null);
    }

    public static AgentReportSection findings(String title, List<AgentFinding> items) {
        return new AgentReportSection("findings", title, null, null, null, items, null, null);
    }

    public static AgentReportSection timeline(String title, List<AgentTimelineItem> items) {
        return new AgentReportSection("timeline", title, null, null, null, null, items, null);
    }

    public static AgentReportSection text(String title, String text) {
        return new AgentReportSection("text", title, null, null, null, null, null, text);
    }
}
