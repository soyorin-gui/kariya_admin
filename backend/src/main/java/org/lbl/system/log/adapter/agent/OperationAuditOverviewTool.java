package org.lbl.system.log.adapter.agent;

import org.lbl.agent.domain.AgentArtifact;
import org.lbl.agent.domain.AgentExecutionContext;
import org.lbl.agent.tool.AgentTool;
import org.lbl.agent.tool.ApprovalPolicy;
import org.lbl.agent.tool.ToolDescriptor;
import org.lbl.agent.tool.ToolResult;
import org.lbl.agent.tool.ToolRisk;
import org.lbl.system.log.adapter.agent.config.AuditAgentPolicy;
import org.lbl.system.log.adapter.agent.model.AuditAnalyzeInput;
import org.lbl.system.log.adapter.agent.model.ResolvedAuditRange;
import org.lbl.system.log.analysis.AuditAnalysisService;
import org.lbl.system.log.analysis.model.OperationAuditOverview;

import java.time.Duration;
import java.util.Set;

/** Operation statistics without a risk scan. */
@Deprecated(forRemoval = false)
public class OperationAuditOverviewTool implements AgentTool<AuditAnalyzeInput, OperationAuditOverview> {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "operation_audit_overview",
            "Query operation volumes, failures, and high-frequency action rankings for a time range. "
                    + "Use for operation statistics or rankings; it does not scan for risks.",
            AuditToolSchemas.analyze(), ToolRisk.READ_ONLY, ApprovalPolicy.NOT_REQUIRED,
            Set.of("system:operatelog:list"), Duration.ofSeconds(30));

    private final AuditTimeRangeResolver ranges;
    private final AuditAnalysisService statistics;
    private final AuditAgentPolicy policy;

    public OperationAuditOverviewTool(AuditTimeRangeResolver ranges, AuditAnalysisService statistics,
                                      AuditAgentPolicy policy) {
        this.ranges = ranges;
        this.statistics = statistics;
        this.policy = policy;
    }

    @Override
    public ToolDescriptor descriptor() { return DESCRIPTOR; }

    @Override
    public Class<AuditAnalyzeInput> inputType() { return AuditAnalyzeInput.class; }

    @Override
    public ToolResult<OperationAuditOverview> execute(AuditAnalyzeInput input, AgentExecutionContext context) {
        context.checkpoint();
        ResolvedAuditRange range = ranges.resolve(input);
        int topN = input.topN() == null ? policy.defaultTopN() : input.topN();
        OperationAuditOverview overview = statistics.operationOverview(range.beginTime(), range.endTime(), topN);
        context.checkpoint();
        String summary = "已查询操作统计，时间范围[%s, %s)，时区%s；总操作%d，成功%d，失败%d；操作次数排行：%s。"
                .formatted(range.beginTime(), range.endTime(), range.zoneId(), overview.totals().getTotalOperations(),
                        overview.totals().getSuccessCount(), overview.totals().getFailureCount(), overview.topByOperations());
        return ToolResult.artifact(summary,
                new AgentArtifact<>("audit.operation-overview", 1, "操作统计", overview));
    }
}
