package org.lbl.system.log.adapter.agent;

import org.lbl.common.exception.BusinessException;
import org.lbl.system.log.adapter.agent.config.AuditAgentPolicy;
import org.lbl.system.log.adapter.agent.model.AuditRangeRequest;
import org.lbl.system.log.adapter.agent.model.ResolvedAuditRange;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 在服务端统一解释“今天/昨天/最近 N 小时”，不让模型猜时区或日期边界。 */
@Component
public class AuditTimeRangeResolver {
    private final AuditAgentPolicy policy;
    private final Clock clock;

    @Autowired
    public AuditTimeRangeResolver(AuditAgentPolicy policy) {
        this(policy, Clock.system(policy.zoneId()));
    }

    AuditTimeRangeResolver(AuditAgentPolicy policy, Clock clock) {
        this.policy = policy;
        this.clock = clock;
    }

    public ResolvedAuditRange resolve(AuditRangeRequest request) {
        if (request == null || request.range() == null) throw new BusinessException("必须指定时间范围");
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = LocalDate.now(clock);
        LocalDateTime begin;
        LocalDateTime end;
        switch (request.range()) {
            case TODAY -> {
                begin = today.atStartOfDay();
                end = now;
            }
            case YESTERDAY -> {
                begin = today.minusDays(1).atStartOfDay();
                end = today.atStartOfDay();
            }
            case RECENT_HOURS -> {
                Integer hours = request.recentHours();
                if (hours == null || hours < 1 || hours > 24) {
                    throw new BusinessException("RECENT_HOURS 必须提供 1 到 24 的 recentHours");
                }
                begin = now.minusHours(hours);
                end = now;
            }
            case CUSTOM -> {
                begin = request.beginTime();
                end = request.endTime();
                if (begin == null || end == null) {
                    throw new BusinessException("CUSTOM 必须同时提供 beginTime 和 endTime");
                }
            }
            default -> throw new BusinessException("不支持的时间范围");
        }
        if (!begin.isBefore(end)) throw new BusinessException("开始时间必须早于结束时间");
        return new ResolvedAuditRange(begin, end, policy.zoneId().getId());
    }
}
