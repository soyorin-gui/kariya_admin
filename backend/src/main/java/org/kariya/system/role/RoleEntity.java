package org.kariya.system.role;

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
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
