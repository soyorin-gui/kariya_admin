package org.lbl.agent.domain.report;

import java.util.Map;

/** 登录轨迹、调用链和审批流共用的时间线节点。 */
public record AgentTimelineItem(
        String time,
        String title,
        String description,
        AgentReportStatus status,
        Map<String, Object> attributes) {

    public AgentTimelineItem {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("时间线标题不能为空");
        status = status == null ? AgentReportStatus.INFO : status;
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
