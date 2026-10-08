package org.lbl.system.log.adapter.agent;

import org.junit.jupiter.api.Test;
import org.lbl.system.log.adapter.agent.config.AuditAgentPolicy;
import org.lbl.system.log.adapter.agent.model.ChunkedRiskScanResult;
import org.lbl.system.log.risk.AuditRiskAnalysisService;
import org.lbl.system.log.risk.config.AuditRiskPolicy;
import org.lbl.system.log.risk.config.WindowRiskPolicy;
import org.lbl.system.log.risk.model.AuditRiskFinding;
import org.lbl.system.log.risk.model.AuditRiskReport;
import org.lbl.system.log.risk.model.AuditRiskSeverity;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChunkedAuditRiskScanServiceTest {
    @Test
    void mergesOneBurstThatCrossesMidnightIntoOneFinding() {
        LocalDateTime begin = LocalDateTime.of(2026, 10, 1, 0, 0);
        LocalDateTime midnight = LocalDateTime.of(2026, 10, 2, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 3, 0, 0);
        AuditRiskAnalysisService analysis = mock(AuditRiskAnalysisService.class);
        when(analysis.scanLoginRiskChunk(begin, midnight)).thenReturn(report(begin, midnight,
                finding(begin.plusHours(23).plusMinutes(55), begin.plusHours(23).plusMinutes(59), 5)));
        // 第二个主分片向前补齐24小时，因此能看见午夜前后的完整窗口。
        when(analysis.scanLoginRiskChunk(begin, end)).thenReturn(report(begin, end,
                finding(begin.plusHours(23).plusMinutes(55), midnight.plusMinutes(3), 6)));

        ChunkedAuditRiskScanService service = new ChunkedAuditRiskScanService(
                analysis, riskPolicy(), new AuditAgentPolicy(ZoneId.of("Asia/Shanghai"),
                10, 50, 100, Duration.ofHours(24), Duration.ofDays(31)));

        ChunkedRiskScanResult result = service.scanLogin(begin, end);

        assertEquals(2, result.segmentCount());
        assertEquals(1, result.report().totalFindingCount());
        assertEquals(6, result.report().findings().get(0).actualValue());
        assertEquals(midnight.plusMinutes(3), result.report().findings().get(0).lastSeen());
    }

    private AuditRiskReport report(LocalDateTime begin, LocalDateTime end, AuditRiskFinding finding) {
        return new AuditRiskReport(begin, end, 1, 1, false, List.of(finding));
    }

    private AuditRiskFinding finding(LocalDateTime first, LocalDateTime last, long actual) {
        return new AuditRiskFinding("LOGIN_FAILURE_BURST", "登录失败爆发", AuditRiskSeverity.HIGH,
                "ACCOUNT", "user:1", "admin", "账号短时间登录失败", actual, 5,
                first, last, List.of(1L, 2L), Map.of("failureCount", actual));
    }

    private AuditRiskPolicy riskPolicy() {
        WindowRiskPolicy tenMinutes = new WindowRiskPolicy(true, Duration.ofMinutes(10), 5,
                AuditRiskSeverity.HIGH);
        return new AuditRiskPolicy(Duration.ofHours(24), 50_000, 100, 20,
                tenMinutes,
                new WindowRiskPolicy(true, Duration.ofMinutes(10), 10, AuditRiskSeverity.HIGH),
                new WindowRiskPolicy(true, Duration.ofHours(24), 3, AuditRiskSeverity.MEDIUM),
                new WindowRiskPolicy(true, Duration.ofHours(1), 5, AuditRiskSeverity.HIGH));
    }
}
