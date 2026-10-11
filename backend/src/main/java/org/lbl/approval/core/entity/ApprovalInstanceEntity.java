package org.lbl.approval.core.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_approval_instance")
public class ApprovalInstanceEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String businessType;
    private Long businessId;
    private String businessTitle;
    private String businessSummary;
    private Long requesterId;
    private String requesterName;
    private String status;
    private Integer currentStep;
    private Integer approvalRound;
    private LocalDateTime submittedTime;
    private LocalDateTime finishedTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
