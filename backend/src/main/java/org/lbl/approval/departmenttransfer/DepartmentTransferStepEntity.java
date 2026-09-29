package org.lbl.approval.departmenttransfer;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_department_change_step")
public class DepartmentTransferStepEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long requestId;
    private Integer stepOrder;
    private String stepType;
    private Long deptId;
    private Long assignedUserId;
    private String status;
    private Long decidedBy;
    private String decisionReason;
    private LocalDateTime decidedTime;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createdTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updatedTime;
}
