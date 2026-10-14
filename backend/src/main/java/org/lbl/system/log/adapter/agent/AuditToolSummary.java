package org.lbl.system.log.adapter.agent;

import org.lbl.system.log.adapter.agent.model.*;

import java.util.List;
import java.util.function.Function;

/** 控制送入模型的事实体积；完整结构化结果仍通过 artifact 交给前端。 */
final class AuditToolSummary {
    private static final int MAX_TIMELINE_SUMMARIES = 30;

    private AuditToolSummary() {
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

    private static <T> String joinLimited(List<T> values, Function<T, String> mapper) {
        if (values == null || values.isEmpty()) return "无事件";
        String result = String.join("；", values.stream().limit(MAX_TIMELINE_SUMMARIES).map(mapper).toList());
        return values.size() > MAX_TIMELINE_SUMMARIES ? result + "；其余事件请查看结构化结果" : result;
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "-" : value.replaceAll("[\\r\\n]+", " ");
    }
}
