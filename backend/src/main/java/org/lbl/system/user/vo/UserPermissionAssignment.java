package org.lbl.system.user.vo;

import lombok.Data;

/** 批量权限查询结果。 */
@Data
public class UserPermissionAssignment {
    private Long userId;
    private String permissionCode;
}
