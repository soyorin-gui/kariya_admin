package org.lbl.system.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lbl.auth.session.SessionService;
import org.lbl.common.exception.BusinessException;
import org.lbl.common.exception.TooManyRequestsException;
import org.lbl.security.context.AccessPolicy;
import org.lbl.system.dept.mapper.DeptMapper;
import org.lbl.system.menu.mapper.MenuMapper;
import org.lbl.system.role.entity.RoleEntity;
import org.lbl.system.role.mapper.RoleMapper;
import org.lbl.system.role.mapper.RoleMenuMapper;
import org.lbl.system.user.entity.UserEntity;
import org.lbl.system.user.mapper.UserMapper;
import org.lbl.system.user.mapper.UserRoleMapper;
import org.lbl.system.user.request.PasswordChangeRequest;
import org.lbl.system.user.service.UserService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserServiceSecurityTest {
    private final UserMapper users = mock(UserMapper.class);
    private final UserRoleMapper userRoles = mock(UserRoleMapper.class);
    private final DeptMapper depts = mock(DeptMapper.class);
    private final RoleMapper roles = mock(RoleMapper.class);
    private final RoleMenuMapper roleMenus = mock(RoleMenuMapper.class);
    private final MenuMapper allMenus = mock(MenuMapper.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final SessionService sessions = mock(SessionService.class);
    private final AccessPolicy access = mock(AccessPolicy.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> redisValues = mock(ValueOperations.class);
    private UserService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(redis.opsForValue()).thenReturn(redisValues);
        service = new UserService(users, userRoles, depts, roles, roleMenus, allMenus, passwords, sessions, access, redis);
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
        assertThrows(BusinessException.class, () -> service.resetPassword(2L));
        verify(users, never()).updateById(any(UserEntity.class));
    }

    /** 停用的 super_admin 角色不授予权限，因此它不该让目标用户变成"不可重置"的超管。 */
    @Test
    void disabledSuperAdminRoleDoesNotProtectTargetFromReset() {
        when(access.actor()).thenReturn(actor(true));
        RoleEntity disabledSuperAdmin = new RoleEntity();
        disabledSuperAdmin.setRoleCode("super_admin");
        disabledSuperAdmin.setStatus(0);
        when(roles.selectAssignedByUserId(2L)).thenReturn(List.of(disabledSuperAdmin));
        when(passwords.encode(anyString())).thenReturn("temp-hash");

        assertDoesNotThrow(() -> service.resetPassword(2L));
        verify(users).updateById(any(UserEntity.class));
    }

    /** 生效的 super_admin 角色必须挡住重置，否则等于可以接管最高权限账号。 */
    @Test
    void activeSuperAdminRoleBlocksReset() {
        when(access.actor()).thenReturn(actor(true));
        RoleEntity activeSuperAdmin = new RoleEntity();
        activeSuperAdmin.setRoleCode("super_admin");
        activeSuperAdmin.setStatus(1);
        when(roles.selectAssignedByUserId(2L)).thenReturn(List.of(activeSuperAdmin));

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
        when(passwords.matches("new-password-123", "old-hash")).thenReturn(false);
        when(passwords.encode("new-password-123")).thenReturn("new-hash");

        service.changeOwnPassword(new PasswordChangeRequest("old-password", "new-password-123"));

        assertEquals("new-hash", actor.getPasswordHash());
        assertEquals(0, actor.getPasswordChangeRequired());
        assertEquals(5L, actor.getAuthVersion());
        verify(sessions).removeAll(1L);
        // 改成功后必须清掉失败计数，否则用户之前打错的几次会一直累计到锁定。
        verify(redis).delete("auth:password-change:fail:1");
    }

    /**
     * 原密码错误累计到上限后必须拒绝，且不再做哈希校验。
     * 这是本接口唯一的爆破防护：它不需要权限码，只有会话就能调用。
     */
    @Test
    void tooManyWrongOldPasswordsBlocksFurtherAttempts() {
        UserEntity actor = user(1L);
        actor.setPasswordHash("old-hash");
        when(access.actor()).thenReturn(new AccessPolicy.Actor(actor, false, Set.of(), false, true, Set.of(), 1));
        when(redisValues.get("auth:password-change:fail:1")).thenReturn("5");

        assertThrows(TooManyRequestsException.class,
                () -> service.changeOwnPassword(new PasswordChangeRequest("guess", "new-password-123")));
        // 已经被限流时不应再消耗一次 BCrypt 运算（否则限流本身也能被用来打满 CPU）。
        verify(passwords, never()).matches(anyString(), anyString());
        verify(users, never()).updateById(any(UserEntity.class));
    }

    @Test
    void wrongOldPasswordIncrementsFailureCounter() {
        UserEntity actor = user(1L);
        actor.setPasswordHash("old-hash");
        when(access.actor()).thenReturn(new AccessPolicy.Actor(actor, false, Set.of(), false, true, Set.of(), 1));
        when(redisValues.get("auth:password-change:fail:1")).thenReturn("2");
        when(passwords.matches("wrong", "old-hash")).thenReturn(false);

        assertThrows(BusinessException.class,
                () -> service.changeOwnPassword(new PasswordChangeRequest("wrong", "new-password-123")));
        verify(redisValues).increment("auth:password-change:fail:1");
        verify(users, never()).updateById(any(UserEntity.class));
    }

    private AccessPolicy.Actor actor(boolean superAdmin) {
        return new AccessPolicy.Actor(user(1L), superAdmin, Set.of("system:user:reset-password"), true, false, Set.of(), 4);
    }

    private UserEntity user(Long id) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setAuthVersion(1L);
        user.setBuiltin(0);
        return user;
    }
}
