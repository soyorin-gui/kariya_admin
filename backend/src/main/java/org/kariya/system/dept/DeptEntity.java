package org.kariya.system.dept;

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
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
