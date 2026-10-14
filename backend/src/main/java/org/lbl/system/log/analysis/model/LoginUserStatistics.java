package org.lbl.system.log.analysis.model;

import lombok.Data;

import java.time.LocalDateTime;

/** 单个账号在一个时间窗口内的登录统计；只包含聚合结果，不包含原始日志。 */
@Data
public class LoginUserStatistics {
    private Long userId;
    private String username;
    private long totalAttempts;
    private long successCount;
    private long failureCount;
    private long lockedCount;
    private long distinctIpCount;
    private LocalDateTime firstLoginTime;
    private LocalDateTime lastLoginTime;

    /** FAILURE 和 LOCKED 都表示本次登录没有成功。 */
    public long getRejectedCount() {
        return failureCount + lockedCount;
    }
}
