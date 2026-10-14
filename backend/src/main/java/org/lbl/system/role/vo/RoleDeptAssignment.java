package org.lbl.system.role.vo;

import lombok.Data;

/** 角色自定义部门范围的批量查询投影。 */
@Data
public class RoleDeptAssignment {
    private Long roleId;
    private Long deptId;
}
