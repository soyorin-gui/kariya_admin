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
import org.lbl.system.log.adapter.agent.model.LoginAuditAgentReport;
import org.lbl.system.log.adapter.agent.model.ResolvedAuditRange;
import org.lbl.system.log.analysis.AuditAnalysisService;
import org.lbl.system.log.risk.AuditRiskAnalysisService;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

/** 把阶段一统计和阶段二登录规则作为白名单能力接入 Agent。 */
@Component
public class LoginAuditAnalyzeTool implements AgentTool<AuditAnalyzeInput, LoginAuditAgentReport> {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "login_audit_analyze",
            "查询指定时间范围内的登录次数、成功/失败/锁定排行，并运行登录风险规则。用户询问谁登录最频繁、异常登录频率、撞库或登录失败时使用。",
            AuditToolSchemas.analyze(), ToolRisk.READ_ONLY, ApprovalPolicy.NOT_REQUIRED,
            Set.of("system:loginlog:list"), Duration.ofSeconds(30));

    private final AuditTimeRangeResolver ranges;
    private final AuditAnalysisService statistics;
    private final AuditRiskAnalysisService risks;
    private final AuditAgentPolicy policy;

    public LoginAuditAnalyzeTool(AuditTimeRangeResolver ranges, AuditAnalysisService statistics,
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
    public ToolResult<LoginAuditAgentReport> execute(AuditAnalyzeInput input, AgentExecutionContext context) {
        context.checkpoint();
        ResolvedAuditRange range = ranges.resolve(input);
        int topN = input.topN() == null ? policy.defaultTopN() : input.topN();
        LoginAuditAgentReport report = new LoginAuditAgentReport(range,
                statistics.loginOverview(range.beginTime(), range.endTime(), topN),
                risks.scanLoginRisks(range.beginTime(), range.endTime()));
        context.checkpoint();
        return ToolResult.artifact(AuditToolSummary.login(report),
                new AgentArtifact<>("audit.login-analysis", 1, "登录审计分析", report));
    }
}
