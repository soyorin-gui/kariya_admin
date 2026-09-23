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
    private String passwordHash;
    private String realName;
    private String phone;
    private String email;
    private Long deptId;
    private Integer status;
    private Long authVersion;
    private Integer passwordChangeRequired;
    private Integer builtin;
    @TableLogic
    private Integer deleted;
    private Long createdBy;
    private LocalDateTime createdTime;
    private Long updatedBy;
    private LocalDateTime updatedTime;
    private LocalDateTime deletedTime;
}
