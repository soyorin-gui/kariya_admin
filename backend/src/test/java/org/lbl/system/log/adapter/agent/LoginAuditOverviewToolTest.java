package org.lbl.system.log.adapter.agent;

import org.junit.jupiter.api.Test;
import org.lbl.agent.domain.AgentActor;
import org.lbl.agent.domain.AgentExecutionContext;
import org.lbl.agent.domain.CancellationToken;
import org.lbl.system.log.adapter.agent.config.AuditAgentPolicy;
import org.lbl.system.log.adapter.agent.model.AuditAnalyzeInput;
import org.lbl.system.log.adapter.agent.model.AuditRangePreset;
import org.lbl.system.log.analysis.AuditAnalysisService;
import org.lbl.system.log.analysis.model.LoginAuditOverview;
import org.lbl.system.log.analysis.model.LoginAuditTotals;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoginAuditOverviewToolTest {
    @Test
    void formatsDateTimeAndNumericTotalsInOneSummary() {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        AuditAgentPolicy policy = new AuditAgentPolicy(zone, 10, 50, 100, Duration.ofHours(24));
        AuditTimeRangeResolver ranges = new AuditTimeRangeResolver(policy,
                Clock.fixed(Instant.parse("2026-10-08T00:00:00Z"), zone));
        AuditAnalysisService statistics = mock(AuditAnalysisService.class);
        LocalDateTime begin = LocalDateTime.of(2026, 10, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 8, 0, 0);
        LoginAuditTotals totals = new LoginAuditTotals();
        totals.setTotalAttempts(12);
        totals.setSuccessCount(9);
        totals.setFailureCount(2);
        totals.setLockedCount(1);
        when(statistics.loginOverview(eq(begin), eq(end), eq(10))).thenReturn(
                new LoginAuditOverview(begin, end, totals, List.of(), List.of(), List.of()));
        LoginAuditOverviewTool tool = new LoginAuditOverviewTool(ranges, statistics, policy);
        AgentExecutionContext context = new AgentExecutionContext("test", new AgentActor(1L, "tester", false,
                Set.of("system:loginlog:list")), Instant.now().plusSeconds(30), new CancellationToken());

        String summary = tool.execute(new AuditAnalyzeInput(AuditRangePreset.CUSTOM, null, begin, end, null), context)
                .modelSummary();

        assertTrue(summary.contains("2026-10-01T00:00"));
        assertTrue(summary.contains("总尝试12，成功9，失败2，锁定1"));
    }
}