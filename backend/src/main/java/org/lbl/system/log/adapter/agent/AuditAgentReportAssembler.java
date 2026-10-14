package org.lbl.system.log.adapter.agent;

import org.lbl.agent.domain.report.*;
import org.lbl.system.log.adapter.agent.model.*;
import org.lbl.system.log.analysis.model.*;
import org.lbl.system.log.risk.model.AuditRiskFinding;
import org.lbl.system.log.risk.model.AuditRiskReport;
import org.lbl.system.log.risk.model.AuditRiskSeverity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 将审计领域对象适配为通用 ui.report；不在 Agent 公共层引入审计模型。 */
@Component
public class AuditAgentReportAssembler {

    public AgentReport login(ResolvedAuditRange range, AuditReportMode mode, LoginAuditOverview overview,
                             ChunkedRiskScanResult riskResult) {
        List<AgentReportSection> sections = new ArrayList<>();
        if (overview != null) addLoginStatistics(sections, overview);
        if (riskResult != null) addRisks(sections, riskResult, "登录风险");
        AgentReportStatus status = statusOf(riskResult == null ? null : riskResult.report());
        String summary = loginSummary(overview, riskResult);
        return new AgentReport(status, summary, metadata(range, mode), sections);
    }

    public AgentReport operation(ResolvedAuditRange range, AuditReportMode mode, OperationAuditOverview overview,
                                 ChunkedRiskScanResult riskResult) {
        List<AgentReportSection> sections = new ArrayList<>();
        if (overview != null) addOperationStatistics(sections, overview);
        if (riskResult != null) addRisks(sections, riskResult, "操作风险");
        AgentReportStatus status = statusOf(riskResult == null ? null : riskResult.report());
        String summary = operationSummary(overview, riskResult);
        return new AgentReport(status, summary, metadata(range, mode), sections);
    }

    public AgentReport loginTimeline(AuditUserTimeline<LoginTimelineEvent> timeline) {
        List<AgentTimelineItem> items = timeline.events().stream().map(event -> new AgentTimelineItem(
                string(event.occurredAt()), "登录 · " + safe(event.result()), safe(event.message()),
                resultStatus(event.result()), attributes(
                "日志ID", event.logId(), "用户", event.username(), "来源IP", event.loginIp()))).toList();
        return timelineReport(timeline, "用户登录时间线", items);
    }

    public AgentReport operationTimeline(AuditUserTimeline<OperationTimelineEvent> timeline) {
        List<AgentTimelineItem> items = timeline.events().stream().map(event -> new AgentTimelineItem(
                string(event.occurredAt()), safe(event.module()) + " · " + safe(event.action()),
                "目标：" + safe(event.targetType()) + " / " + safe(event.targetId()),
                resultStatus(event.result()), attributes(
                "日志ID", event.logId(), "用户", event.username(), "结果", event.result(),
                "耗时", event.durationMs() == null ? null : event.durationMs() + " ms",
                "requestId", event.requestId()))).toList();
        return timelineReport(timeline, "用户操作时间线", items);
    }

    public String loginModelSummary(ResolvedAuditRange range, LoginAuditOverview overview,
                                    ChunkedRiskScanResult riskResult) {
        StringBuilder value = new StringBuilder("登录审计范围[%s, %s)，时区%s。"
                .formatted(range.beginTime(), range.endTime(), range.zoneId()));
        if (overview != null) {
            LoginAuditTotals totals = overview.totals();
            value.append("总尝试%d，成功%d，失败%d，锁定%d。登录排行：%s。".formatted(
                    totals.getTotalAttempts(), totals.getSuccessCount(), totals.getFailureCount(),
                    totals.getLockedCount(), overview.topByAttempts()));
        }
        appendRiskSummary(value, riskResult);
        return value.toString();
    }

    public String operationModelSummary(ResolvedAuditRange range, OperationAuditOverview overview,
                                        ChunkedRiskScanResult riskResult) {
        StringBuilder value = new StringBuilder("操作审计范围[%s, %s)，时区%s。"
                .formatted(range.beginTime(), range.endTime(), range.zoneId()));
        if (overview != null) {
            OperationAuditTotals totals = overview.totals();
            value.append("总操作%d，成功%d，失败%d。操作排行：%s；高频动作：%s。".formatted(
                    totals.getTotalOperations(), totals.getSuccessCount(), totals.getFailureCount(),
                    overview.topByOperations(), overview.topActions()));
        }
        appendRiskSummary(value, riskResult);
        return value.toString();
    }

    private void addLoginStatistics(List<AgentReportSection> sections, LoginAuditOverview overview) {
        LoginAuditTotals totals = overview.totals();
        sections.add(AgentReportSection.metrics("登录概览", List.of(
                AgentMetric.number("登录尝试", totals.getTotalAttempts()),
                AgentMetric.number("成功", totals.getSuccessCount()),
                AgentMetric.number("失败", totals.getFailureCount()),
                AgentMetric.number("锁定", totals.getLockedCount()),
                AgentMetric.number("账号数", totals.getDistinctAccountCount()),
                AgentMetric.number("来源IP", totals.getDistinctIpCount()))));
        sections.add(AgentReportSection.table("登录频率排行", List.of(
                        new AgentTableColumn("username", "用户", "text"),
                        new AgentTableColumn("totalAttempts", "尝试", "number"),
                        new AgentTableColumn("successCount", "成功", "number"),
                        new AgentTableColumn("rejectedCount", "未成功", "number"),
                        new AgentTableColumn("distinctIpCount", "来源IP", "number")),
                overview.topByAttempts().stream().map(this::loginRow).toList()));
    }

    private void addOperationStatistics(List<AgentReportSection> sections, OperationAuditOverview overview) {
        OperationAuditTotals totals = overview.totals();
        sections.add(AgentReportSection.metrics("操作概览", List.of(
                AgentMetric.number("总操作", totals.getTotalOperations()),
                AgentMetric.number("成功", totals.getSuccessCount()),
                AgentMetric.number("失败", totals.getFailureCount()),
                AgentMetric.number("操作人", totals.getDistinctOperatorCount()),
                AgentMetric.number("动作类型", totals.getDistinctActionCount()))));
        sections.add(AgentReportSection.table("操作人排行", List.of(
                        new AgentTableColumn("username", "用户", "text"),
                        new AgentTableColumn("totalOperations", "操作数", "number"),
                        new AgentTableColumn("failureCount", "失败", "number"),
                        new AgentTableColumn("distinctActionCount", "动作类型", "number")),
                overview.topByOperations().stream().map(this::operationRow).toList()));
        sections.add(AgentReportSection.table("高频动作", List.of(
                        new AgentTableColumn("module", "模块", "text"),
                        new AgentTableColumn("action", "动作", "text"),
                        new AgentTableColumn("totalOperations", "次数", "number"),
                        new AgentTableColumn("failureCount", "失败", "number")),
                overview.topActions().stream().map(this::actionRow).toList()));
    }

    private void addRisks(List<AgentReportSection> sections, ChunkedRiskScanResult result, String title) {
        AuditRiskReport report = result.report();
        List<AgentFinding> findings = report.findings().stream().map(this::finding).toList();
        sections.add(AgentReportSection.findings(title, findings));
        String text = "扫描覆盖 %s 至 %s，共 %d 个内部主分片；命中 %d 项%s。规则命中是风险信号，不等于已确认事件。"
                .formatted(report.beginTime(), report.endTime(), result.segmentCount(), report.totalFindingCount(),
                        report.truncated() ? "，展示结果已截断" : "");
        sections.add(AgentReportSection.text("扫描说明", text));
    }

    private AgentReport timelineReport(AuditUserTimeline<?> timeline, String title,
                                       List<AgentTimelineItem> items) {
        AgentReportStatus status = timeline.truncated() ? AgentReportStatus.WARNING : AgentReportStatus.INFO;
        String summary = "返回 %d 条事件%s".formatted(timeline.returnedCount(),
                timeline.truncated() ? "，仍有更多结果" : "");
        Map<String, Object> metadata = attributes(
                "beginTime", timeline.range().beginTime(), "endTime", timeline.range().endTime(),
                "zoneId", timeline.range().zoneId(), "userId", timeline.userId(),
                "username", timeline.username());
        return new AgentReport(status, summary, metadata,
                List.of(AgentReportSection.timeline(title, items)));
    }

    private AgentFinding finding(AuditRiskFinding finding) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("对象", finding.subjectName());
        attributes.put("首次发生", finding.firstSeen());
        attributes.put("最后发生", finding.lastSeen());
        attributes.put("实际值", finding.actualValue());
        attributes.put("阈值", finding.threshold());
        return new AgentFinding(finding.ruleCode(), finding.ruleName(), severity(finding.severity()),
                finding.summary(), attributes,
                finding.evidenceLogIds().stream().map(String::valueOf).toList());
    }

    private Map<String, Object> loginRow(LoginUserStatistics item) {
        return attributes("userId", item.getUserId(), "username", item.getUsername(),
                "totalAttempts", item.getTotalAttempts(), "successCount", item.getSuccessCount(),
                "rejectedCount", item.getRejectedCount(), "distinctIpCount", item.getDistinctIpCount());
    }

    private Map<String, Object> operationRow(OperationUserStatistics item) {
        return attributes("userId", item.getUserId(), "username", item.getUsername(),
                "totalOperations", item.getTotalOperations(), "failureCount", item.getFailureCount(),
                "distinctActionCount", item.getDistinctActionCount());
    }

    private Map<String, Object> actionRow(OperationActionStatistics item) {
        return attributes("module", item.getModule(), "action", item.getAction(),
                "totalOperations", item.getTotalOperations(), "failureCount", item.getFailureCount());
    }

    private Map<String, Object> metadata(ResolvedAuditRange range, AuditReportMode mode) {
        return attributes("beginTime", range.beginTime(), "endTime", range.endTime(),
                "zoneId", range.zoneId(), "mode", mode);
    }

    private Map<String, Object> attributes(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            Object value = values[index + 1];
            if (value != null) result.put(String.valueOf(values[index]), value);
        }
        return result;
    }

    private void appendRiskSummary(StringBuilder value, ChunkedRiskScanResult result) {
        if (result == null) return;
        value.append("风险扫描分为%d个内部主分片，命中%d项：%s。风险命中不等于已确认事件；0项仅表示当前规则未命中。"
                .formatted(result.segmentCount(), result.report().totalFindingCount(),
                        result.report().findings().isEmpty() ? "无" : result.report().findings()));
    }

    private String loginSummary(LoginAuditOverview overview, ChunkedRiskScanResult risks) {
        String statistics = overview == null ? "" : "登录%d次，成功%d次，失败%d次，锁定%d次"
                .formatted(overview.totals().getTotalAttempts(), overview.totals().getSuccessCount(),
                        overview.totals().getFailureCount(), overview.totals().getLockedCount());
        return combine(statistics, riskSummary(risks));
    }

    private String operationSummary(OperationAuditOverview overview, ChunkedRiskScanResult risks) {
        String statistics = overview == null ? "" : "操作%d次，成功%d次，失败%d次"
                .formatted(overview.totals().getTotalOperations(), overview.totals().getSuccessCount(),
                        overview.totals().getFailureCount());
        return combine(statistics, riskSummary(risks));
    }

    private String riskSummary(ChunkedRiskScanResult risks) {
        return risks == null ? "" : "风险规则命中%d项".formatted(risks.report().totalFindingCount());
    }

    private String combine(String left, String right) {
        if (left.isBlank()) return right;
        if (right.isBlank()) return left;
        return left + " · " + right;
    }

    private AgentReportStatus statusOf(AuditRiskReport report) {
        if (report == null || report.findings().isEmpty()) return AgentReportStatus.SUCCESS;
        return report.findings().stream().map(AuditRiskFinding::severity)
                .min((left, right) -> Integer.compare(left.priority(), right.priority()))
                .map(this::severity).orElse(AgentReportStatus.INFO);
    }

    private AgentReportStatus severity(AuditRiskSeverity severity) {
        return switch (severity) {
            case CRITICAL -> AgentReportStatus.CRITICAL;
            case HIGH -> AgentReportStatus.HIGH;
            case MEDIUM -> AgentReportStatus.WARNING;
            case LOW -> AgentReportStatus.INFO;
        };
    }

    private AgentReportStatus resultStatus(String result) {
        if ("SUCCESS".equals(result)) return AgentReportStatus.SUCCESS;
        if ("FAILURE".equals(result) || "LOCKED".equals(result)) return AgentReportStatus.WARNING;
        return AgentReportStatus.INFO;
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value.replaceAll("[\\r\\n]+", " ");
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
