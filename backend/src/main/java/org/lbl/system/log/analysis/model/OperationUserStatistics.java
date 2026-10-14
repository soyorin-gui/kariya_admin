package org.lbl.system.log.analysis.model;

import lombok.Data;

import java.time.LocalDateTime;

/** 单个操作人在一个时间窗口内的操作统计。 */
@Data
public class OperationUserStatistics {
    private Long userId;
    private String username;
    private long totalOperations;
    private long successCount;
    private long failureCount;
    private long distinctActionCount;
    private LocalDateTime firstOperationTime;
    private LocalDateTime lastOperationTime;
}
