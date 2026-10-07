package org.lbl.system.log.adapter.agent.model;

import java.time.LocalDateTime;

/** 给 Agent 的登录证据最小投影，不暴露 user-agent 等无关大字段。 */
public record LoginTimelineEvent(
        Long logId,
        Long userId,
        String username,
        String loginIp,
        String result,
        String message,
        LocalDateTime occurredAt) {
}
