package org.kariya.system.role;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.kariya.common.exception.BusinessException;
import org.kariya.system.menu.MenuEntity;
import org.kariya.system.menu.MenuMapper;
import org.kariya.system.user.UserRoleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RoleService {
    private final RoleMapper roles;
    private final RoleMenuMapper roleMenus;
    private final MenuMapper menus;
    private final UserRoleMapper userRoles;

    public RoleService(RoleMapper roles, RoleMenuMapper roleMenus, MenuMapper menus, UserRoleMapper userRoles) {
        this.roles = roles;
        this.roleMenus = roleMenus;
        this.menus = menus;
        this.userRoles = userRoles;
    }

    public List<RoleVO> list(String keyword) {
        return roles.selectList(new LambdaQueryWrapper<RoleEntity>()
                        .and(keyword != null && !keyword.isBlank(), q -> q.like(RoleEntity::getRoleName, keyword).or().like(RoleEntity::getRoleCode, keyword))
                        .orderByAsc(RoleEntity::getId))
                .stream().map(this::toView).toList();
    }

    @Transactional
    public RoleVO create(RoleRequest request) {
        String code = request.roleCode().trim();
        if (roles.selectCount(new LambdaQueryWrapper<RoleEntity>().eq(RoleEntity::getRoleCode, code)) > 0) {
            throw new BusinessException("角色标识已存在");
        }
        RoleEntity role = new RoleEntity();
        apply(role, request);
        role.setBuiltin(0);
        roles.insert(role);
        return toView(role);
    }

    @Transactional
    public RoleVO update(Long id, RoleRequest request) {
        RoleEntity role = require(id);
        String code = request.roleCode().trim();
        if (roles.selectCount(new LambdaQueryWrapper<RoleEntity>().eq(RoleEntity::getRoleCode, code).ne(RoleEntity::getId, id)) > 0) {
            throw new BusinessException("角色标识已存在");
        }
        if (role.getBuiltin() == 1 && (!role.getRoleCode().equals(code) || request.status() != 1)) {
            throw new BusinessException("内置角色不能修改标识或禁用");
        }
        apply(role, request);
        roles.updateById(role);
        return toView(role);
    }

    @Transactional
    public void remove(Long id) {
        RoleEntity role = require(id);
        if (role.getBuiltin() == 1) throw new BusinessException("内置角色不能删除");
        if (userRoles.countByRoleId(id) > 0) throw new BusinessException("该角色已分配给用户，不能删除");
        roleMenus.deleteByRoleId(id);
        roles.deleteById(id);
    }

    public List<Long> menuIds(Long id) {
        require(id);
        return roleMenus.selectMenuIds(id);
    }

    @Transactional
    public void grantMenus(Long id, List<Long> menuIds) {
        require(id);
        List<Long> ids = menuIds == null ? List.of() : menuIds.stream().filter(value -> value != null && value > 0).distinct().toList();
        if (!ids.isEmpty() && menus.selectCount(new LambdaQueryWrapper<MenuEntity>().in(MenuEntity::getId, ids)) != ids.size()) {
            throw new BusinessException("授权菜单中包含不存在的记录");
        }
        roleMenus.deleteByRoleId(id);
        ids.forEach(menuId -> roleMenus.insert(id, menuId));
    }

    private void apply(RoleEntity role, RoleRequest request) {
        role.setRoleName(request.roleName().trim());
        role.setRoleCode(request.roleCode().trim());
        role.setDataScope(request.dataScope());
        role.setStatus(request.status());
    }

    private RoleEntity require(Long id) {
        RoleEntity role = roles.selectById(id);
        if (role == null) throw new BusinessException("角色不存在");
        return role;
    }

    private RoleVO toView(RoleEntity role) {
        return new RoleVO(role.getId(), role.getRoleName(), role.getRoleCode(), role.getDataScope(), role.getStatus(), role.getBuiltin(), userRoles.countByRoleId(role.getId()), role.getCreatedTime());
    }
}
