package org.lbl.system.log.adapter.agent;

import org.lbl.common.exception.BusinessException;
import org.lbl.system.log.adapter.agent.config.AuditAgentPolicy;
import org.lbl.system.log.adapter.agent.model.ChunkedRiskScanResult;
import org.lbl.system.log.risk.AuditRiskAnalysisService;
import org.lbl.system.log.risk.config.AuditRiskPolicy;
import org.lbl.system.log.risk.model.AuditRiskFinding;
import org.lbl.system.log.risk.model.AuditRiskReport;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * 在后端确定性地完成多日风险扫描。每个主分片向前补齐最大规则窗口，随后按 lastSeen
 * 归属分片并合并重叠命中，避免模型逐日调用和午夜边界漏判。
 */
@Service
public class ChunkedAuditRiskScanService {
    private final AuditRiskAnalysisService analysis;
    private final AuditRiskPolicy riskPolicy;
    private final AuditAgentPolicy agentPolicy;

    public ChunkedAuditRiskScanService(AuditRiskAnalysisService analysis, AuditRiskPolicy riskPolicy,
                                       AuditAgentPolicy agentPolicy) {
        this.analysis = analysis;
        this.riskPolicy = riskPolicy;
        this.agentPolicy = agentPolicy;
    }

    public ChunkedRiskScanResult scanLogin(LocalDateTime beginTime, LocalDateTime endTime) {
        return scan(beginTime, endTime, analysis::scanLoginRiskChunk);
    }

    public ChunkedRiskScanResult scanOperation(LocalDateTime beginTime, LocalDateTime endTime) {
        return scan(beginTime, endTime, analysis::scanOperationRiskChunk);
    }

    private ChunkedRiskScanResult scan(LocalDateTime beginTime, LocalDateTime endTime,
                                       BiFunction<LocalDateTime, LocalDateTime, AuditRiskReport> scanner) {
        validate(beginTime, endTime);
        List<AuditRiskFinding> assigned = new ArrayList<>();
        LocalDateTime segmentStart = beginTime;
        int segments = 0;
        while (segmentStart.isBefore(endTime)) {
            LocalDateTime segmentEnd = min(segmentStart.plus(riskPolicy.maxScanRange()), endTime);
            LocalDateTime queryBegin = segmentStart.equals(beginTime)
                    ? beginTime
                    : max(beginTime, segmentStart.minus(riskPolicy.maxRuleWindow()));
            AuditRiskReport partial = scanner.apply(queryBegin, segmentEnd);
            LocalDateTime ownerStart = segmentStart;
            assigned.addAll(partial.findings().stream()
                    .filter(finding -> !finding.lastSeen().isBefore(ownerStart))
                    .filter(finding -> finding.lastSeen().isBefore(segmentEnd))
                    .toList());
            segments++;
            segmentStart = segmentEnd;
        }

        List<AuditRiskFinding> merged = mergeOverlapping(assigned);
        merged.sort(displayOrder());
        int total = merged.size();
        List<AuditRiskFinding> returned = merged.stream().limit(riskPolicy.maxFindings()).toList();
        return new ChunkedRiskScanResult(new AuditRiskReport(beginTime, endTime, total, returned.size(),
                total > returned.size(), returned), segments);
    }

    private List<AuditRiskFinding> mergeOverlapping(List<AuditRiskFinding> source) {
        List<AuditRiskFinding> ordered = source.stream().sorted(Comparator
                .comparing(AuditRiskFinding::ruleCode)
                .thenComparing(AuditRiskFinding::subjectId)
                .thenComparing(AuditRiskFinding::firstSeen)
                .thenComparing(AuditRiskFinding::lastSeen)).toList();
        List<AuditRiskFinding> merged = new ArrayList<>();
        for (AuditRiskFinding current : ordered) {
            if (!merged.isEmpty()) {
                AuditRiskFinding previous = merged.get(merged.size() - 1);
                if (sameSubject(previous, current) && !current.firstSeen().isAfter(previous.lastSeen())) {
                    merged.set(merged.size() - 1, merge(previous, current));
                    continue;
                }
            }
            merged.add(current);
        }
        return merged;
    }

    private AuditRiskFinding merge(AuditRiskFinding left, AuditRiskFinding right) {
        LinkedHashSet<Long> evidence = new LinkedHashSet<>(left.evidenceLogIds());
        evidence.addAll(right.evidenceLogIds());
        List<Long> evidenceIds = evidence.stream().limit(riskPolicy.maxEvidenceIds()).toList();
        Map<String, Object> facts = new LinkedHashMap<>(left.facts());
        facts.putAll(right.facts());
        AuditRiskFinding representative = right.actualValue() >= left.actualValue() ? right : left;
        return new AuditRiskFinding(left.ruleCode(), left.ruleName(), left.severity(), left.subjectType(),
                left.subjectId(), left.subjectName(), representative.summary(),
                Math.max(left.actualValue(), right.actualValue()), left.threshold(),
                min(left.firstSeen(), right.firstSeen()), max(left.lastSeen(), right.lastSeen()),
                evidenceIds, facts);
    }

    private boolean sameSubject(AuditRiskFinding left, AuditRiskFinding right) {
        return left.ruleCode().equals(right.ruleCode()) && left.subjectId().equals(right.subjectId());
    }

    private Comparator<AuditRiskFinding> displayOrder() {
        return Comparator.comparingInt((AuditRiskFinding finding) -> finding.severity().priority())
                .thenComparing(AuditRiskFinding::lastSeen, Comparator.reverseOrder())
                .thenComparing(AuditRiskFinding::ruleCode)
                .thenComparing(AuditRiskFinding::subjectId);
    }

    private void validate(LocalDateTime beginTime, LocalDateTime endTime) {
        if (beginTime == null || endTime == null || !beginTime.isBefore(endTime)) {
            throw new BusinessException("风险报告开始时间必须早于结束时间");
        }
        if (Duration.between(beginTime, endTime).compareTo(agentPolicy.maxReportRange()) > 0) {
            throw new BusinessException("审计报告时间范围不能超过 " + agentPolicy.maxReportRange().toDays() + " 天");
        }
    }

    private LocalDateTime min(LocalDateTime left, LocalDateTime right) {
        return left.isBefore(right) ? left : right;
    }

    private LocalDateTime max(LocalDateTime left, LocalDateTime right) {
        return left.isAfter(right) ? left : right;
    }
}
