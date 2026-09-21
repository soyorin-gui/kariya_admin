package org.kariya.system.user;

import java.time.LocalDateTime;
import java.util.List;

public record UserVO(Long id, String username, String realName, String phone, String email, Long deptId, String deptName, List<Long> roleIds, String roleNames, Integer status,
                     LocalDateTime createdTime) {
}
