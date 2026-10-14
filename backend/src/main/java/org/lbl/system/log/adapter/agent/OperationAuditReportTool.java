package org.lbl.system.log.adapter.agent;

import org.lbl.agent.domain.AgentArtifact;
import org.lbl.agent.domain.AgentExecutionContext;
import org.lbl.agent.domain.report.AgentReport;
import org.lbl.agent.tool.*;
import org.lbl.system.log.adapter.agent.config.AuditAgentPolicy;
import org.lbl.system.log.adapter.agent.model.*;
import org.lbl.system.log.analysis.AuditAnalysisService;
import org.lbl.system.log.analysis.model.OperationAuditOverview;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

/** 操作审计的意图级工具，输出与登录、流水号等业务共用的 ui.report。 */
@Component
public class OperationAuditReportTool implements AgentTool<AuditReportInput, AgentReport> {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "operation_audit_report",
            "生成操作审计报告。查询操作量或排行用STATISTICS；只问异常或违规用RISKS；两者都问用FULL。"
                    + "支持最长31天，后端负责风险分片，不要自行逐日调用。",
            AuditToolSchemas.report(), ToolRisk.READ_ONLY, ApprovalPolicy.NOT_REQUIRED,
            Set.of("system:operatelog:list"), Duration.ofSeconds(45));

    private final AuditTimeRangeResolver ranges;
    private final AuditAnalysisService statistics;
    private final ChunkedAuditRiskScanService risks;
    private final AuditAgentReportAssembler reports;
    private final AuditAgentPolicy policy;

    public OperationAuditReportTool(AuditTimeRangeResolver ranges, AuditAnalysisService statistics,
                                    ChunkedAuditRiskScanService risks, AuditAgentReportAssembler reports,
                                    AuditAgentPolicy policy) {
        this.ranges = ranges;
        this.statistics = statistics;
        this.risks = risks;
        this.reports = reports;
        this.policy = policy;
    }

    @Override public ToolDescriptor descriptor() { return DESCRIPTOR; }
    @Override public Class<AuditReportInput> inputType() { return AuditReportInput.class; }

    @Override
    public ToolResult<AgentReport> execute(AuditReportInput input, AgentExecutionContext context) {
        context.checkpoint();
        ResolvedAuditRange range = ranges.resolve(input);
        int topN = input.topN() == null ? policy.defaultTopN() : input.topN();
        OperationAuditOverview overview = input.mode() == AuditReportMode.RISKS ? null
                : statistics.operationOverview(range.beginTime(), range.endTime(), topN);
        ChunkedRiskScanResult riskResult = input.mode() == AuditReportMode.STATISTICS ? null
                : risks.scanOperation(range.beginTime(), range.endTime());
        AgentReport report = reports.operation(range, input.mode(), overview, riskResult);
        context.checkpoint();
        return ToolResult.artifact(reports.operationModelSummary(range, overview, riskResult),
                new AgentArtifact<>("ui.report", 1, "操作审计报告", report));
    }
}
