package org.lbl.system.log.analysis;

import org.lbl.common.exception.BusinessException;
import org.lbl.system.log.analysis.model.LoginAuditOverview;
import org.lbl.system.log.analysis.model.LoginAuditTotals;
import org.lbl.system.log.analysis.model.OperationAuditOverview;
import org.lbl.system.log.analysis.model.OperationAuditTotals;
import org.lbl.system.log.mapper.LoginLogMapper;
import org.lbl.system.log.mapper.OperationLogMapper;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 审计日志的确定性统计入口。
 *
 * <p>本类只负责参数边界和统计查询编排，不包含“是否异常”的主观判断。后续规则引擎、
 * REST 接口和 AgentTool 都应复用这里，而不是各自重新写一套统计 SQL。</p>
 */
@Service
public class AuditAnalysisService {
    private static final int DEFAULT_TOP_N = 10;
    private static final int MAX_TOP_N = 50;
    private static final Duration MAX_RANGE = Duration.ofDays(31);

    private final LoginLogMapper loginLogs;
    private final OperationLogMapper operationLogs;

    public AuditAnalysisService(LoginLogMapper loginLogs, OperationLogMapper operationLogs) {
        this.loginLogs = loginLogs;
        this.operationLogs = operationLogs;
    }

    public LoginAuditOverview loginOverview(LocalDateTime beginTime, LocalDateTime endTime, Integer topN) {
        int limit = validate(beginTime, endTime, topN);
        LoginAuditTotals totals = loginLogs.summarize(beginTime, endTime);
        return new LoginAuditOverview(beginTime, endTime, totals,
                loginLogs.topByAttempts(beginTime, endTime, limit),
                loginLogs.topBySuccesses(beginTime, endTime, limit),
                loginLogs.topByRejected(beginTime, endTime, limit));
    }

    public OperationAuditOverview operationOverview(LocalDateTime beginTime, LocalDateTime endTime, Integer topN) {
        int limit = validate(beginTime, endTime, topN);
        OperationAuditTotals totals = operationLogs.summarize(beginTime, endTime);
        return new OperationAuditOverview(beginTime, endTime, totals,
                operationLogs.topByOperations(beginTime, endTime, limit),
                operationLogs.topByFailures(beginTime, endTime, limit),
                operationLogs.topActions(beginTime, endTime, limit));
    }

    private int validate(LocalDateTime beginTime, LocalDateTime endTime, Integer topN) {
        if (beginTime == null || endTime == null) {
            throw new BusinessException("统计开始时间和结束时间不能为空");
        }
        if (!beginTime.isBefore(endTime)) {
            throw new BusinessException("统计开始时间必须早于结束时间");
        }
        if (Duration.between(beginTime, endTime).compareTo(MAX_RANGE) > 0) {
            throw new BusinessException("单次统计时间范围不能超过 31 天");
        }
        int limit = topN == null ? DEFAULT_TOP_N : topN;
        if (limit < 1 || limit > MAX_TOP_N) {
            throw new BusinessException("排行榜数量必须在 1 到 50 之间");
        }
        return limit;
    }
}
