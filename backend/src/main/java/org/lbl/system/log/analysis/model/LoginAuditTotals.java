package org.lbl.system.log.analysis.model;

import lombok.Data;

import java.time.LocalDateTime;

/** 登录日志在一个时间窗口内的确定性汇总。 */
@Data
public class LoginAuditTotals {
    private long totalAttempts;
    private long successCount;
    private long failureCount;
    private long lockedCount;
    /** 按日志中的 username 去重；未知账号的失败尝试也会被统计。 */
    private long distinctAccountCount;
    private long distinctIpCount;
    private LocalDateTime firstLoginTime;
    private LocalDateTime lastLoginTime;
}
