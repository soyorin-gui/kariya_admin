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

@Deprecated(forRemoval = false)
public class LoginAuditRiskScanTool implements AgentTool<AuditRiskScanInput, AuditRiskReport> {

    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "login_audit_risk_scan",
            "扫描登录风险规则，例如撞库、登录失败爆发等。"
                    + "仅在用户明确询问风险、异常或攻击迹象时使用；单次最多 24 小时。",
            AuditToolSchemas.riskScan(), ToolRisk.READ_ONLY, ApprovalPolicy.NOT_REQUIRED,
            Set.of("system:loginlog:list"), Duration.ofSeconds(30));

    private final AuditTimeRangeResolver ranges;
    private final AuditRiskAnalysisService risks;

    public LoginAuditRiskScanTool(AuditTimeRangeResolver ranges, AuditRiskAnalysisService risks) {
        this.ranges = ranges;
        this.risks = risks;
    }

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public Class<AuditRiskScanInput> inputType() {
        return AuditRiskScanInput.class;
    }

    @Override
    public ToolResult<AuditRiskReport> execute(AuditRiskScanInput input, AgentExecutionContext context) {
        context.checkpoint();
        ResolvedAuditRange range = ranges.resolve(input);
        AuditRiskReport report = risks.scanLoginRisks(range.beginTime(), range.endTime());
        context.checkpoint();
        String summary = "已扫描登录风险，时间范围[%s, %s)，命中%d项%s。".formatted(range.beginTime(),
                range.endTime(), report.totalFindingCount(), report.truncated() ? "（结果已截断）" : "");
        return ToolResult.artifact(summary, new AgentArtifact<>("audit.login-risks", 1,
                "登录风险扫描", report));
    }
}
