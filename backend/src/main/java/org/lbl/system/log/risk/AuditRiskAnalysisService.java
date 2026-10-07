package org.lbl.system.log.risk;

import org.lbl.common.exception.BusinessException;
import org.lbl.system.log.mapper.LoginLogMapper;
import org.lbl.system.log.mapper.OperationLogMapper;
import org.lbl.system.log.risk.config.AuditRiskPolicy;
import org.lbl.system.log.risk.model.AuditRiskFinding;
import org.lbl.system.log.risk.model.AuditRiskReport;
import org.lbl.system.log.risk.model.LoginRiskEvent;
import org.lbl.system.log.risk.model.OperationRiskEvent;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 查询一次日志快照、运行全部确定性规则，并统一排序和裁剪结果。 */
@Service
public class AuditRiskAnalysisService {
    private final LoginLogMapper loginLogs;
    private final OperationLogMapper operationLogs;
    private final AuditRiskPolicy policy;
    private final List<AuditRiskRule> rules;

    public AuditRiskAnalysisService(LoginLogMapper loginLogs, OperationLogMapper operationLogs,
                                    AuditRiskPolicy policy, List<AuditRiskRule> rules) {
        this.loginLogs = loginLogs;
        this.operationLogs = operationLogs;
        this.policy = policy;
        this.rules = rules.stream().sorted(Comparator.comparing(AuditRiskRule::code)).toList();
        ensureUniqueRuleCodes(this.rules);
    }

    public AuditRiskReport scan(LocalDateTime beginTime, LocalDateTime endTime) {
        validateRange(beginTime, endTime);
        int queryLimit = policy.maxEventsPerSource() + 1;
        List<LoginRiskEvent> loginEvents = loginLogs.findRiskEvents(beginTime, endTime, queryLimit);
        List<OperationRiskEvent> operationEvents = operationLogs.findRiskEvents(beginTime, endTime, queryLimit);
        rejectOverflow(loginEvents.size(), operationEvents.size());

        return evaluate(beginTime, endTime, loginEvents, operationEvents);
    }

    /** 仅扫描登录日志，供只拥有登录日志权限的 Agent 工具复用。 */
    public AuditRiskReport scanLoginRisks(LocalDateTime beginTime, LocalDateTime endTime) {
        validateRange(beginTime, endTime);
        List<LoginRiskEvent> loginEvents = loginLogs.findRiskEvents(
                beginTime, endTime, policy.maxEventsPerSource() + 1);
        if (loginEvents.size() > policy.maxEventsPerSource()) rejectOverflow(loginEvents.size(), 0);
        return evaluate(beginTime, endTime, loginEvents, List.of());
    }

    /** 仅扫描操作日志，避免操作审计工具越权读取登录日志。 */
    public AuditRiskReport scanOperationRisks(LocalDateTime beginTime, LocalDateTime endTime) {
        validateRange(beginTime, endTime);
        List<OperationRiskEvent> operationEvents = operationLogs.findRiskEvents(
                beginTime, endTime, policy.maxEventsPerSource() + 1);
        if (operationEvents.size() > policy.maxEventsPerSource()) rejectOverflow(0, operationEvents.size());
        return evaluate(beginTime, endTime, List.of(), operationEvents);
    }

    private AuditRiskReport evaluate(LocalDateTime beginTime, LocalDateTime endTime,
                                     List<LoginRiskEvent> loginEvents,
                                     List<OperationRiskEvent> operationEvents) {
        AuditRiskContext context = new AuditRiskContext(beginTime, endTime, loginEvents, operationEvents);
        List<AuditRiskFinding> findings = new ArrayList<>();
        for (AuditRiskRule rule : rules) findings.addAll(rule.evaluate(context));
        findings.sort(Comparator
                .comparingInt((AuditRiskFinding finding) -> finding.severity().priority())
                .thenComparing(AuditRiskFinding::lastSeen, Comparator.reverseOrder())
                .thenComparing(AuditRiskFinding::ruleCode)
                .thenComparing(AuditRiskFinding::subjectId));

        int total = findings.size();
        List<AuditRiskFinding> returned = findings.stream().limit(policy.maxFindings()).toList();
        return new AuditRiskReport(beginTime, endTime, total, returned.size(), total > returned.size(), returned);
    }

    private void validateRange(LocalDateTime beginTime, LocalDateTime endTime) {
        if (beginTime == null || endTime == null) throw new BusinessException("风险扫描开始时间和结束时间不能为空");
        if (!beginTime.isBefore(endTime)) throw new BusinessException("风险扫描开始时间必须早于结束时间");
        if (Duration.between(beginTime, endTime).compareTo(policy.maxScanRange()) > 0) {
            throw new BusinessException("单次风险扫描时间范围不能超过 " + policy.maxScanRange().toHours() + " 小时");
        }
    }

    private void rejectOverflow(int loginCount, int operationCount) {
        if (loginCount > policy.maxEventsPerSource() || operationCount > policy.maxEventsPerSource()) {
            throw new BusinessException("扫描范围内日志超过单类 " + policy.maxEventsPerSource()
                    + " 条，请缩小时间范围后重试");
        }
    }

    private void ensureUniqueRuleCodes(List<AuditRiskRule> rules) {
        Set<String> codes = new HashSet<>();
        for (AuditRiskRule rule : rules) {
            if (rule.code() == null || rule.code().isBlank()) throw new IllegalStateException("风险规则 code 不能为空");
            if (!codes.add(rule.code())) throw new IllegalStateException("存在重复风险规则 code：" + rule.code());
        }
    }
}
