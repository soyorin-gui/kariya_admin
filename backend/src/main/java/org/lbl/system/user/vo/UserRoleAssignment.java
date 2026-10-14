package org.lbl.system.user.vo;

import lombok.Data;

/** 批量装配用户列表时使用的 user-role 投影，避免逐用户查询角色。 */
@Data
public class UserRoleAssignment {
    private Long userId;
    private Long roleId;
    private String roleName;
    private String roleCode;
    private String dataScope;
    private Integer status;
    private Integer builtin;
}
