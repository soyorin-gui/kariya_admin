package org.lbl.system.role.vo;

import lombok.Data;

/** 多角色用户数量的批量统计结果。 */
@Data
public class RoleUserCount {
    private Long roleId;
    private Long userCount;
}
