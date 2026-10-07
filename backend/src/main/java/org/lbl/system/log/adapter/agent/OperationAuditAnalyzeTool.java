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
import org.lbl.system.log.adapter.agent.model.OperationAuditAgentReport;
import org.lbl.system.log.adapter.agent.model.ResolvedAuditRange;
import org.lbl.system.log.analysis.AuditAnalysisService;
import org.lbl.system.log.risk.AuditRiskAnalysisService;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

/** 把操作统计与密码类风险规则接入 Agent，且不读取登录日志。 */
@Component
public class OperationAuditAnalyzeTool implements AgentTool<AuditAnalyzeInput, OperationAuditAgentReport> {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "operation_audit_analyze",
            "查询指定时间范围内的操作量、失败量、高频动作，并运行频繁修改密码、频繁重置密码等操作风险规则。",
            AuditToolSchemas.analyze(), ToolRisk.READ_ONLY, ApprovalPolicy.NOT_REQUIRED,
            Set.of("system:operatelog:list"), Duration.ofSeconds(30));

    private final AuditTimeRangeResolver ranges;
    private final AuditAnalysisService statistics;
    private final AuditRiskAnalysisService risks;
    private final AuditAgentPolicy policy;

    public OperationAuditAnalyzeTool(AuditTimeRangeResolver ranges, AuditAnalysisService statistics,
                                     AuditRiskAnalysisService risks, AuditAgentPolicy policy) {
        this.ranges = ranges;
        this.statistics = statistics;
        this.risks = risks;
        this.policy = policy;
    }

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public Class<AuditAnalyzeInput> inputType() {
        return AuditAnalyzeInput.class;
    }

    @Override
    public ToolResult<OperationAuditAgentReport> execute(AuditAnalyzeInput input, AgentExecutionContext context) {
        context.checkpoint();
        ResolvedAuditRange range = ranges.resolve(input);
        int topN = input.topN() == null ? policy.defaultTopN() : input.topN();
        OperationAuditAgentReport report = new OperationAuditAgentReport(range,
                statistics.operationOverview(range.beginTime(), range.endTime(), topN),
                risks.scanOperationRisks(range.beginTime(), range.endTime()));
        context.checkpoint();
        return ToolResult.artifact(AuditToolSummary.operation(report),
                new AgentArtifact<>("audit.operation-analysis", 1, "操作审计分析", report));
    }
}
