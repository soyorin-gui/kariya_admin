package org.lbl.approval.departmenttransfer;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_department_change")
public class DepartmentTransferRequestEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long requesterId;
    private Long fromDeptId;
    private Long targetDeptId;
    private String reason;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createdTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updatedTime;
}
