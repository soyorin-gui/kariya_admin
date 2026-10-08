package org.lbl.system.log.adapter.agent;

import org.lbl.common.exception.BusinessException;
import org.lbl.system.log.adapter.agent.config.AuditAgentPolicy;
import org.lbl.system.log.adapter.agent.model.AuditUserTimeline;
import org.lbl.system.log.adapter.agent.model.AuditUserTimelineInput;
import org.lbl.system.log.adapter.agent.model.LoginTimelineEvent;
import org.lbl.system.log.adapter.agent.model.OperationTimelineEvent;
import org.lbl.system.log.adapter.agent.model.ResolvedAuditRange;
import org.lbl.system.log.entity.LoginLogEntity;
import org.lbl.system.log.entity.OperationLogEntity;
import org.lbl.system.log.mapper.LoginLogMapper;
import org.lbl.system.log.mapper.OperationLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;

/** 用户事件下钻用例；负责身份、范围和结果数量边界，工具本身只做适配。 */
@Service
public class AuditTimelineService {
    private final LoginLogMapper loginLogs;
    private final OperationLogMapper operationLogs;
    private final AuditAgentPolicy policy;

    public AuditTimelineService(LoginLogMapper loginLogs, OperationLogMapper operationLogs,
                                AuditAgentPolicy policy) {
        this.loginLogs = loginLogs;
        this.operationLogs = operationLogs;
        this.policy = policy;
    }

    public AuditUserTimeline<LoginTimelineEvent> loginTimeline(AuditUserTimelineInput input,
                                                                ResolvedAuditRange range) {
        Identity identity = identity(input);
        int limit = validate(range, input.limit());
        List<LoginLogEntity> rows = loginLogs.findUserTimeline(identity.userId(), identity.username(),
                range.beginTime(), range.endTime(), limit + 1);
        boolean truncated = rows.size() > limit;
        List<LoginTimelineEvent> events = rows.stream().limit(limit).map(this::toLoginEvent).toList();
        return new AuditUserTimeline<>(range, identity.userId(), identity.username(),
                events.size(), truncated, events);
    }

    public AuditUserTimeline<OperationTimelineEvent> operationTimeline(AuditUserTimelineInput input,
                                                                        ResolvedAuditRange range) {
        Identity identity = identity(input);
        int limit = validate(range, input.limit());
        List<OperationLogEntity> rows = operationLogs.findUserTimeline(identity.userId(), identity.username(),
                range.beginTime(), range.endTime(), limit + 1);
        boolean truncated = rows.size() > limit;
        List<OperationTimelineEvent> events = rows.stream().limit(limit).map(this::toOperationEvent).toList();
        return new AuditUserTimeline<>(range, identity.userId(), identity.username(),
                events.size(), truncated, events);
    }

    private Identity identity(AuditUserTimelineInput input) {
        if (input.userId() != null) return new Identity(input.userId(), normalized(input.username()));
        String username = normalized(input.username());
        if (!StringUtils.hasText(username)) throw new BusinessException("userId 和 username 至少提供一个");
        return new Identity(null, username);
    }

    private int validate(ResolvedAuditRange range, Integer requestedLimit) {
        if (Duration.between(range.beginTime(), range.endTime()).compareTo(policy.maxTimelineRange()) > 0) {
            throw new BusinessException("用户时间线单次最多查询 " + rangeLabel(policy.maxTimelineRange()));
        }
        int limit = requestedLimit == null ? policy.defaultTimelineLimit() : requestedLimit;
        if (limit < 1 || limit > policy.maxTimelineLimit()) {
            throw new BusinessException("用户时间线返回数量必须在 1 到 " + policy.maxTimelineLimit() + " 之间");
        }
        return limit;
    }

    private String rangeLabel(Duration duration) {
        return duration.toHours() % 24 == 0
                ? duration.toDays() + " 天"
                : duration.toHours() + " 小时";
    }

    private LoginTimelineEvent toLoginEvent(LoginLogEntity row) {
        return new LoginTimelineEvent(row.getId(), row.getUserId(), row.getUsername(), row.getLoginIp(),
                row.getResult(), row.getMessage(), row.getLoginTime());
    }

    private OperationTimelineEvent toOperationEvent(OperationLogEntity row) {
        return new OperationTimelineEvent(row.getId(), row.getUserId(), row.getUsername(), row.getModule(),
                row.getAction(), row.getTargetType(), row.getTargetId(), row.getTargetName(),
                row.getRequestId(), row.getResult(), row.getDurationMs(), row.getCreatedTime());
    }

    private String normalized(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record Identity(Long userId, String username) {
    }
}
