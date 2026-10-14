package org.lbl.system.dept.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_dept")
public class DeptEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentId;
    private String ancestors;
    private String deptName;
    private String deptCode;
    private Long leaderUserId;
    private Integer sortOrder;
    private Integer status;
    private Integer builtin;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
    private LocalDateTime deletedTime;
}
