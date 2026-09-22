package org.kariya.system.dept;

import java.time.LocalDateTime;

public record DeptVO(Long id, Long parentId, String ancestors, String deptName, String deptCode, Long leaderUserId,
                     String leaderName, Integer sortOrder, Integer status, Integer builtin, LocalDateTime createdTime) {
}
