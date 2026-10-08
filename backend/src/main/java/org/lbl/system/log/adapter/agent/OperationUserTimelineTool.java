package org.lbl.system.log.adapter.agent;

import org.lbl.agent.domain.AgentArtifact;
import org.lbl.agent.domain.AgentExecutionContext;
import org.lbl.agent.domain.report.AgentReport;
import org.lbl.agent.tool.AgentTool;
import org.lbl.agent.tool.ApprovalPolicy;
import org.lbl.agent.tool.ToolDescriptor;
import org.lbl.agent.tool.ToolResult;
import org.lbl.agent.tool.ToolRisk;
import org.lbl.system.log.adapter.agent.model.AuditUserTimeline;
import org.lbl.system.log.adapter.agent.model.AuditUserTimelineInput;
import org.lbl.system.log.adapter.agent.model.OperationTimelineEvent;
import org.lbl.system.log.adapter.agent.model.ResolvedAuditRange;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

/** 在统计或规则命中后，按用户查看有界的操作证据时间线。 */
@Component
public class OperationUserTimelineTool implements AgentTool<AuditUserTimelineInput, AgentReport> {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "operation_user_timeline",
            "按 userId 或完整用户名查询指定时间范围内的操作事件时间线。用于核对高频或失败操作的动作、目标、耗时、requestId 和证据日志。",
            AuditToolSchemas.timeline(), ToolRisk.READ_ONLY, ApprovalPolicy.NOT_REQUIRED,
            Set.of("system:operatelog:list"), Duration.ofSeconds(20));

    private final AuditTimeRangeResolver ranges;
    private final AuditTimelineService timelines;
    private final AuditAgentReportAssembler reports;

    public OperationUserTimelineTool(AuditTimeRangeResolver ranges, AuditTimelineService timelines,
                                     AuditAgentReportAssembler reports) {
        this.ranges = ranges;
        this.timelines = timelines;
        this.reports = reports;
    }

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public Class<AuditUserTimelineInput> inputType() {
        return AuditUserTimelineInput.class;
    }

    @Override
    public ToolResult<AgentReport> execute(AuditUserTimelineInput input, AgentExecutionContext context) {
        context.checkpoint();
        ResolvedAuditRange range = ranges.resolve(input);
        AuditUserTimeline<OperationTimelineEvent> timeline = timelines.operationTimeline(input, range);
        context.checkpoint();
        return ToolResult.artifact(AuditToolSummary.operationTimeline(timeline),
                new AgentArtifact<>("ui.report", 1, "用户操作时间线", reports.operationTimeline(timeline)));
    }
}
