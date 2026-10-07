package org.lbl.system.log.adapter.agent;

import org.lbl.agent.domain.AgentArtifact;
import org.lbl.agent.domain.AgentExecutionContext;
import org.lbl.agent.tool.AgentTool;
import org.lbl.agent.tool.ApprovalPolicy;
import org.lbl.agent.tool.ToolDescriptor;
import org.lbl.agent.tool.ToolResult;
import org.lbl.agent.tool.ToolRisk;
import org.lbl.system.log.adapter.agent.model.AuditUserTimeline;
import org.lbl.system.log.adapter.agent.model.AuditUserTimelineInput;
import org.lbl.system.log.adapter.agent.model.LoginTimelineEvent;
import org.lbl.system.log.adapter.agent.model.ResolvedAuditRange;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

/** 在统计或规则命中后，按用户查看有界的登录证据时间线。 */
@Component
public class LoginUserTimelineTool implements AgentTool<AuditUserTimelineInput, AuditUserTimeline<LoginTimelineEvent>> {
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "login_user_timeline",
            "按 userId 或完整用户名查询指定时间范围内的登录事件时间线。用于在登录排行或风险命中后核对具体成功、失败、锁定、来源 IP 和证据日志。",
            AuditToolSchemas.timeline(), ToolRisk.READ_ONLY, ApprovalPolicy.NOT_REQUIRED,
            Set.of("system:loginlog:list"), Duration.ofSeconds(20));

    private final AuditTimeRangeResolver ranges;
    private final AuditTimelineService timelines;

    public LoginUserTimelineTool(AuditTimeRangeResolver ranges, AuditTimelineService timelines) {
        this.ranges = ranges;
        this.timelines = timelines;
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
    public ToolResult<AuditUserTimeline<LoginTimelineEvent>> execute(AuditUserTimelineInput input,
                                                                     AgentExecutionContext context) {
        context.checkpoint();
        ResolvedAuditRange range = ranges.resolve(input);
        AuditUserTimeline<LoginTimelineEvent> timeline = timelines.loginTimeline(input, range);
        context.checkpoint();
        return ToolResult.artifact(AuditToolSummary.loginTimeline(timeline),
                new AgentArtifact<>("audit.login-timeline", 1, "用户登录时间线", timeline));
    }
}
