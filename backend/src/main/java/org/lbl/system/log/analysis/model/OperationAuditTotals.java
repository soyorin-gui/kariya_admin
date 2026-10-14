package org.lbl.system.log.analysis.model;

import lombok.Data;

import java.time.LocalDateTime;

/** 操作日志在一个时间窗口内的确定性汇总。 */
@Data
public class OperationAuditTotals {
    private long totalOperations;
    private long successCount;
    private long failureCount;
    private long distinctOperatorCount;
    private long distinctActionCount;
    private LocalDateTime firstOperationTime;
    private LocalDateTime lastOperationTime;
}
