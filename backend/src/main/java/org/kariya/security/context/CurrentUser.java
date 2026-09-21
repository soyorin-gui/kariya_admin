package org.kariya.security.context;

public record CurrentUser(Long id, String username, Long deptId, boolean superAdmin) {
}
