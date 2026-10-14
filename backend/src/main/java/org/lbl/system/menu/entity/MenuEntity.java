package org.lbl.system.menu.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_menu")
public class MenuEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentId;
    private String menuName;
    private String menuType;
    private String routeName;
    private String routePath;
    private String component;
    private String permissionCode;
    private String icon;
    private Integer sortOrder;
    private Integer visible;
    private Integer status;
    private Integer keepAlive;
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
