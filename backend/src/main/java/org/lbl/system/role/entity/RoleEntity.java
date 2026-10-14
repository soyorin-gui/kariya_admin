package org.lbl.system.role.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_role")
public class RoleEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String roleName;
    private String roleCode;
    private String dataScope;
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
