package org.lbl.approval.task;

import lombok.Data;

import java.time.LocalDateTime;

/** 审批中心的轻量列表投影；详情仍复用具体业务的查询接口。 */
@Data
public class ApprovalTaskView {
    private Long requestId;
    private String businessType;
    private String requesterName;
    private String fromDeptName;
    private String targetDeptName;
    private String reason;
    private String requestStatus;
    private Integer currentStep;
    private String stepType;
    private String stepStatus;
    private String assignedUserName;
    private String decidedByName;
    private String decisionReason;
    private LocalDateTime createdTime;
    private LocalDateTime decidedTime;
}
