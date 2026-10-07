package org.lbl.system.log.adapter.agent.model;

import java.time.LocalDateTime;

/** 供统计与下钻输入复用的时间范围协议。 */
public interface AuditRangeRequest {
    AuditRangePreset range();
    Integer recentHours();
    LocalDateTime beginTime();
    LocalDateTime endTime();
}
