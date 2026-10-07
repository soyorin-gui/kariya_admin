package org.lbl.system.log.adapter.agent.model;

import java.time.LocalDateTime;

/** 已按服务端时区确定的左闭右开时间范围。 */
public record ResolvedAuditRange(LocalDateTime beginTime, LocalDateTime endTime, String zoneId) {
}
