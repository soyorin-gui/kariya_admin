package org.lbl.system.log.adapter.agent;

import org.lbl.agent.domain.AgentArtifact;
import org.lbl.agent.domain.AgentExecutionContext;
import org.lbl.agent.tool.AgentTool;
import org.lbl.agent.tool.ApprovalPolicy;
import org.lbl.agent.tool.ToolDescriptor;
import org.lbl.agent.tool.ToolResult;
import org.lbl.agent.tool.ToolRisk;
import org.lbl.system.log.adapter.agent.model.AuditRiskScanInput;
import org.lbl.system.log.adapter.agent.model.ResolvedAuditRange;
import org.lbl.system.log.risk.AuditRiskAnalysisService;
import org.lbl.system.log.risk.model.AuditRiskReport;

import java.time.Duration;
import java.util.Set;

/** Runs operation risk rules only when a risk scan is requested. */
@Deprecated(forRemoval = false)
public class OperationAuditRiskScanTool implements AgentTool<AuditRiskScanInput, AuditRiskReport> {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "operation_audit_risk_scan",
            "Scan operation risk rules, including frequent password changes and resets. "
                    + "Use only for explicit risk, anomaly, or violation investigations; maximum 24 hours.",
            AuditToolSchemas.riskScan(), ToolRisk.READ_ONLY, ApprovalPolicy.NOT_REQUIRED,
            Set.of("system:operatelog:list"), Duration.ofSeconds(30));

    private final AuditTimeRangeResolver ranges;
    private final AuditRiskAnalysisService risks;

    public OperationAuditRiskScanTool(AuditTimeRangeResolver ranges, AuditRiskAnalysisService risks) {
        this.ranges = ranges;
        this.risks = risks;
    }

    @Override
    public ToolDescriptor descriptor() { return DESCRIPTOR; }

    @Override
    public Class<AuditRiskScanInput> inputType() { return AuditRiskScanInput.class; }

    @Override
    public ToolResult<AuditRiskReport> execute(AuditRiskScanInput input, AgentExecutionContext context) {
        context.checkpoint();
        ResolvedAuditRange range = ranges.resolve(input);
        AuditRiskReport report = risks.scanOperationRisks(range.beginTime(), range.endTime());
        context.checkpoint();
        String summary = "已扫描操作风险，时间范围[%s, %s)，命中%d项%s。".formatted(range.beginTime(),
                range.endTime(), report.totalFindingCount(), report.truncated() ? "（结果已截断）" : "");
        return ToolResult.artifact(summary,
                new AgentArtifact<>("audit.operation-risks", 1, "操作风险扫描", report));
    }
}
