package org.kariya.system.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kariya.auth.session.SessionService;
import org.kariya.common.exception.BusinessException;
import org.kariya.security.context.AccessPolicy;
import org.kariya.system.dept.DeptMapper;
import org.kariya.system.role.RoleEntity;
import org.kariya.system.role.RoleMapper;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserServiceSecurityTest {
    private final UserMapper users = mock(UserMapper.class);
    private final UserRoleMapper userRoles = mock(UserRoleMapper.class);
    private final DeptMapper depts = mock(DeptMapper.class);
    private final RoleMapper roles = mock(RoleMapper.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final SessionService sessions = mock(SessionService.class);
    private final AccessPolicy access = mock(AccessPolicy.class);
    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(users, userRoles, depts, roles, passwords, sessions, access);
        UserEntity target = user(2L);
        when(users.selectById(2L)).thenReturn(target);
    }

    @Test
    void nonSuperAdminCannotResetAnotherUsersPassword() {
        when(access.actor()).thenReturn(actor(false));
        assertThrows(BusinessException.class, () -> service.resetPassword(2L));
        verify(users, never()).updateById(any(UserEntity.class));
        verify(sessions, never()).removeAll(anyLong());
    }

    @Test
    void superAdminPasswordCannotBeResetThroughAdminEndpoint() {
        when(access.actor()).thenReturn(actor(true));
        RoleEntity superRole = new RoleEntity();
        superRole.setRoleCode("super_admin");
        when(roles.selectAssignedByUserId(2L)).thenReturn(List.of(superRole));
        assertThrows(BusinessException.class, () -> service.resetPassword(2L));
        verify(users, never()).updateById(any(UserEntity.class));
    }

    @Test
    void changingOwnPasswordRevokesPreviousSessions() {
        UserEntity actor = user(1L);
        actor.setPasswordHash("old-hash");
        actor.setAuthVersion(4L);
        when(access.actor()).thenReturn(new AccessPolicy.Actor(actor, false, Set.of(), false, true, Set.of(), 1));
        when(passwords.matches("old-password", "old-hash")).thenReturn(true);
        when(passwords.encode("new-password-123")).thenReturn("new-hash");

        service.changeOwnPassword(new PasswordChangeRequest("old-password", "new-password-123"));

        assertEquals("new-hash", actor.getPasswordHash());
        assertEquals(0, actor.getPasswordChangeRequired());
        assertEquals(5L, actor.getAuthVersion());
        verify(sessions).removeAll(1L);
    }

    private AccessPolicy.Actor actor(boolean superAdmin) {
        return new AccessPolicy.Actor(user(1L), superAdmin, Set.of("system:user:reset-password"), true, false, Set.of(), 4);
    }

    private UserEntity user(Long id) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setAuthVersion(1L);
        return user;
    }
}
