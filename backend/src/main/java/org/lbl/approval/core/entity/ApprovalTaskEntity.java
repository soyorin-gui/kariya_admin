package org.lbl.approval.core.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_approval_task")
public class ApprovalTaskEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long approvalInstanceId;
    private Integer approvalRound;
    private Integer stepOrder;
    private String stepType;
    private String stepName;
    private Long assignedUserId;
    private String assignedUserName;
    private Long candidateDeptId;
    private String candidateDeptName;
    private String candidateRoleCode;
    private String candidateRoleName;
    private String status;
    private Long decidedBy;
    private String decidedByName;
    private String decisionReason;
    private LocalDateTime decidedTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
