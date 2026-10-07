package org.lbl.system.log.adapter.agent;

import org.lbl.system.log.adapter.agent.model.AuditUserTimeline;
import org.lbl.system.log.adapter.agent.model.LoginAuditAgentReport;
import org.lbl.system.log.adapter.agent.model.LoginTimelineEvent;
import org.lbl.system.log.adapter.agent.model.OperationAuditAgentReport;
import org.lbl.system.log.adapter.agent.model.OperationTimelineEvent;
import org.lbl.system.log.analysis.model.LoginUserStatistics;
import org.lbl.system.log.analysis.model.OperationActionStatistics;
import org.lbl.system.log.analysis.model.OperationUserStatistics;
import org.lbl.system.log.risk.model.AuditRiskFinding;

import java.util.List;
import java.util.function.Function;

/** 控制送入模型的事实体积；完整结构化结果仍通过 artifact 交给前端。 */
final class AuditToolSummary {
    private static final int MAX_RISK_SUMMARIES = 10;
    private static final int MAX_TIMELINE_SUMMARIES = 30;

    private AuditToolSummary() {
    }

    static String login(LoginAuditAgentReport report) {
        var totals = report.statistics().totals();
        return """
                已查询登录审计，时间范围[%s, %s)，时区%s。
                总尝试%d，成功%d，失败%d，锁定%d，账号%d，IP%d。
                尝试次数排行：%s。
                未成功次数排行：%s。
                确定性规则命中%d项%s：%s。
                风险命中只是规则信号，不等同于已确认攻击；0项仅表示当前规则未命中。
                """.formatted(
                report.range().beginTime(), report.range().endTime(), report.range().zoneId(),
                totals.getTotalAttempts(), totals.getSuccessCount(), totals.getFailureCount(), totals.getLockedCount(),
                totals.getDistinctAccountCount(), totals.getDistinctIpCount(),
                join(report.statistics().topByAttempts(), AuditToolSummary::loginAttempts),
                join(report.statistics().topByRejected(), AuditToolSummary::loginRejected),
                report.risks().totalFindingCount(), report.risks().truncated() ? "（返回已截断）" : "",
                risks(report.risks().findings()));
    }

    static String operation(OperationAuditAgentReport report) {
        var totals = report.statistics().totals();
        return """
                已查询操作审计，时间范围[%s, %s)，时区%s。
                总操作%d，成功%d，失败%d，操作人%d，动作类型%d。
                操作次数排行：%s。
                失败次数排行：%s。
                高频动作：%s。
                确定性规则命中%d项%s：%s。
                风险命中只是规则信号，不等同于已确认违规；0项仅表示当前规则未命中。
                """.formatted(
                report.range().beginTime(), report.range().endTime(), report.range().zoneId(),
                totals.getTotalOperations(), totals.getSuccessCount(), totals.getFailureCount(),
                totals.getDistinctOperatorCount(), totals.getDistinctActionCount(),
                join(report.statistics().topByOperations(), AuditToolSummary::operationCount),
                join(report.statistics().topByFailures(), AuditToolSummary::operationFailures),
                join(report.statistics().topActions(), AuditToolSummary::actionCount),
                report.risks().totalFindingCount(), report.risks().truncated() ? "（返回已截断）" : "",
                risks(report.risks().findings()));
    }

    static String loginTimeline(AuditUserTimeline<LoginTimelineEvent> timeline) {
        return timelineHeader(timeline) + joinLimited(timeline.events(), event ->
                "%s 登录[%s] IP=%s 结果=%s 说明=%s logId=%s".formatted(event.occurredAt(),
                        safe(event.username()), safe(event.loginIp()), safe(event.result()),
                        safe(event.message()), event.logId()));
    }

    static String operationTimeline(AuditUserTimeline<OperationTimelineEvent> timeline) {
        return timelineHeader(timeline) + joinLimited(timeline.events(), event ->
                "%s 操作[%s/%s] 目标=%s:%s 结果=%s 耗时=%sms requestId=%s logId=%s".formatted(
                        event.occurredAt(), safe(event.module()), safe(event.action()), safe(event.targetType()),
                        safe(event.targetId()), safe(event.result()), event.durationMs(), safe(event.requestId()),
                        event.logId()));
    }

    private static String timelineHeader(AuditUserTimeline<?> timeline) {
        return "已按用户下钻，userId=%s，username=%s，范围[%s, %s)，返回%d条%s。事件按时间倒序："
                .formatted(timeline.userId(), safe(timeline.username()), timeline.range().beginTime(),
                        timeline.range().endTime(), timeline.returnedCount(), timeline.truncated() ? "（存在更多，请缩小范围）" : "");
    }

    private static String loginAttempts(LoginUserStatistics item) {
        return "%s(userId=%s):%d次".formatted(safe(item.getUsername()), item.getUserId(), item.getTotalAttempts());
    }

    private static String loginRejected(LoginUserStatistics item) {
        return "%s(userId=%s):%d次".formatted(safe(item.getUsername()), item.getUserId(), item.getRejectedCount());
    }

    private static String operationCount(OperationUserStatistics item) {
        return "%s(userId=%s):%d次".formatted(safe(item.getUsername()), item.getUserId(), item.getTotalOperations());
    }

    private static String operationFailures(OperationUserStatistics item) {
        return "%s(userId=%s):%d次".formatted(safe(item.getUsername()), item.getUserId(), item.getFailureCount());
    }

    private static String actionCount(OperationActionStatistics item) {
        return "%s/%s:%d次".formatted(safe(item.getModule()), safe(item.getAction()), item.getTotalOperations());
    }

    private static String risks(List<AuditRiskFinding> findings) {
        if (findings.isEmpty()) return "无";
        String value = join(findings.stream().limit(MAX_RISK_SUMMARIES).toList(), finding ->
                "%s[%s] %s，证据日志=%s".formatted(finding.ruleCode(), finding.severity(),
                        finding.summary(), finding.evidenceLogIds()));
        return findings.size() > MAX_RISK_SUMMARIES ? value + "；其余命中请查看结构化结果" : value;
    }

    private static <T> String join(List<T> values, Function<T, String> mapper) {
        return values == null || values.isEmpty() ? "无" : String.join("；", values.stream().map(mapper).toList());
    }

    private static <T> String joinLimited(List<T> values, Function<T, String> mapper) {
        if (values == null || values.isEmpty()) return "无事件";
        String result = String.join("；", values.stream().limit(MAX_TIMELINE_SUMMARIES).map(mapper).toList());
        return values.size() > MAX_TIMELINE_SUMMARIES ? result + "；其余事件请查看结构化结果" : result;
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "-" : value.replaceAll("[\\r\\n]+", " ");
    }
}
