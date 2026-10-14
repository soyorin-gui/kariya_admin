package org.lbl.system.log.risk.model;

import lombok.Data;

import java.time.LocalDateTime;

/** 操作风险规则所需的最小数据库投影。 */
@Data
public class OperationRiskEvent {
    private Long id;
    private Long userId;
    private String username;
    private String module;
    private String action;
    private String targetType;
    private String targetId;
    private String result;
    private LocalDateTime createdTime;
}
