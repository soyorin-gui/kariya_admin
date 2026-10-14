package org.lbl.system.user.vo;

import java.time.LocalDateTime;

/** 用户列表专用投影：只包含表格展示和操作按钮判断需要的数据。 */
public record UserListVO(Long id, String username, String realName, String phone, String deptName,
                         String roleNames, Integer status, LocalDateTime createdTime,
                         boolean manageable, boolean deletable, boolean resettable) {
}
