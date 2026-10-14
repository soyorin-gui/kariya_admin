package org.lbl.system.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class UserEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String employeeNo;
    private Integer employeeNoVerified;
    private String realName;
    private String phone;
    private String email;
    private Long deptId;
    private String registrationSource;
    private Integer status;
    private Long authVersion;
    private Integer builtin;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
    private LocalDateTime deletedTime;
}
