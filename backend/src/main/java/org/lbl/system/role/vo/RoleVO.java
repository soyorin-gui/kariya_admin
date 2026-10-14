package org.lbl.system.role.vo;

import java.time.LocalDateTime;
import java.util.List;

public record RoleVO(Long id, String roleName, String roleCode, String dataScope, Integer status,
                     Integer builtin, long userCount, LocalDateTime createdTime, boolean manageable,
                     List<Long> customDeptIds, boolean scopeEditable) {
}
