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
import org.lbl.system.log.analysis.model.LoginAuditOverview;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

@Component
public class LoginAuditOverviewTool implements AgentTool<AuditAnalyzeInput, LoginAuditOverview> {

    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "login_audit_overview",
            "查询指定时间范围内的登录次数、成功/失败/锁定排行。"
                    + "用户询问谁登录最频繁、登录次数排行或成功失败统计时使用。"
                    + "单次最多 31 天；不执行风险扫描。",
            AuditToolSchemas.analyze(), ToolRisk.READ_ONLY, ApprovalPolicy.NOT_REQUIRED,
            Set.of("system:loginlog:list"), Duration.ofSeconds(30)
    );

    private final AuditTimeRangeResolver ranges;
    private final AuditAnalysisService statistics;
    private final AuditAgentPolicy policy;

    public LoginAuditOverviewTool(AuditTimeRangeResolver ranges, AuditAnalysisService statistics, AuditAgentPolicy policy) {
        this.ranges = ranges;
        this.statistics = statistics;
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
    public ToolResult<LoginAuditOverview> execute(AuditAnalyzeInput input, AgentExecutionContext context) {
        context.checkpoint();
        ResolvedAuditRange range = ranges.resolve(input);
        int topN = input.topN() == null ? policy.defaultTopN() : input.topN();
        LoginAuditOverview overview = statistics.loginOverview(range.beginTime(), range.endTime(), topN);
        context.checkpoint();
        String summary = ("已查询登录统计，时间范围[%s, %s)，时区%s；"
                + "总尝试%d，成功%d，失败%d，锁定%d；登录次数排行：%s。")
                .formatted(range.beginTime(), range.endTime(), range.zoneId(), overview.totals().getTotalAttempts(),
                        overview.totals().getSuccessCount(), overview.totals().getFailureCount(),
                        overview.totals().getLockedCount(), overview.topByAttempts().isEmpty()
                                ? "无" : overview.topByAttempts());
        return ToolResult.artifact(summary, new AgentArtifact<>("audit.login-overview", 1, "登录统计", overview));
    }
}