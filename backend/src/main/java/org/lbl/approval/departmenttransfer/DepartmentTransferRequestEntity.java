package org.lbl.approval.departmenttransfer;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_department_change_request")
public class DepartmentTransferRequestEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long requesterId;
    private Long fromDeptId;
    private Long targetDeptId;
    private String reason;
    private String status;
    private Integer currentStep;
    private Long version;
    private LocalDateTime finishedTime;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createdTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updatedTime;
}
