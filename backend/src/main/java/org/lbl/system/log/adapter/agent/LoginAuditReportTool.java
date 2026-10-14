package org.lbl.system.log.adapter.agent;

import org.lbl.agent.domain.AgentArtifact;
import org.lbl.agent.domain.AgentExecutionContext;
import org.lbl.agent.domain.report.AgentReport;
import org.lbl.agent.tool.*;
import org.lbl.system.log.adapter.agent.config.AuditAgentPolicy;
import org.lbl.system.log.adapter.agent.model.*;
import org.lbl.system.log.analysis.AuditAnalysisService;
import org.lbl.system.log.analysis.model.LoginAuditOverview;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

/** 登录审计的意图级工具：一次调用生成一份统计/风险/完整通用报告。 */
@Component
public class LoginAuditReportTool implements AgentTool<AuditReportInput, AgentReport> {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "login_audit_report",
            "生成登录审计报告。查询次数或排行用STATISTICS；只问风险用RISKS；同时询问排行和异常用FULL。"
                    + "支持最长31天，后端会确定性分片风险扫描，禁止自行逐日调用。",
            AuditToolSchemas.report(), ToolRisk.READ_ONLY, ApprovalPolicy.NOT_REQUIRED,
            Set.of("system:loginlog:list"), Duration.ofSeconds(45));

    private final AuditTimeRangeResolver ranges;
    private final AuditAnalysisService statistics;
    private final ChunkedAuditRiskScanService risks;
    private final AuditAgentReportAssembler reports;
    private final AuditAgentPolicy policy;

    public LoginAuditReportTool(AuditTimeRangeResolver ranges, AuditAnalysisService statistics,
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
        LoginAuditOverview overview = input.mode() == AuditReportMode.RISKS ? null
                : statistics.loginOverview(range.beginTime(), range.endTime(), topN);
        ChunkedRiskScanResult riskResult = input.mode() == AuditReportMode.STATISTICS ? null
                : risks.scanLogin(range.beginTime(), range.endTime());
        AgentReport report = reports.login(range, input.mode(), overview, riskResult);
        context.checkpoint();
        return ToolResult.artifact(reports.loginModelSummary(range, overview, riskResult),
                new AgentArtifact<>("ui.report", 1, "登录审计报告", report));
    }
}
