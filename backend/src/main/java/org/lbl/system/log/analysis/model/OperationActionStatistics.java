package org.lbl.system.log.analysis.model;

import lombok.Data;

/** 某一种“模块 + 动作”在一个时间窗口内的操作统计。 */
@Data
public class OperationActionStatistics {
    private String module;
    private String action;
    private long totalOperations;
    private long successCount;
    private long failureCount;
    private long distinctOperatorCount;
}
