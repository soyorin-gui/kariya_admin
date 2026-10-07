package org.lbl.system.log.analysis.model;

import java.time.LocalDateTime;
import java.util.List;

/** 登录审计统计结果。三个榜单分别查询，避免“总次数榜”掩盖失败次数较高的账号。 */
public record LoginAuditOverview(
        LocalDateTime beginTime,
        LocalDateTime endTime,
        LoginAuditTotals totals,
        List<LoginUserStatistics> topByAttempts,
        List<LoginUserStatistics> topBySuccesses,
        List<LoginUserStatistics> topByRejected) {
}
