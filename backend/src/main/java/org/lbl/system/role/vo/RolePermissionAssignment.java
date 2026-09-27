package org.lbl.system.role.vo;

import lombok.Data;

/** 多角色权限的批量查询结果。 */
@Data
public class RolePermissionAssignment {
    private Long roleId;
    private String permissionCode;
}
