package org.kariya.system.menu;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.kariya.common.exception.BusinessException;
import org.kariya.system.role.RoleEntity;
import org.kariya.system.role.RoleMapper;
import org.kariya.system.role.RoleMenuMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.HashSet;
import java.util.Set;

@Service
public class MenuService {
    private static final Set<String> TYPES = Set.of("DIR", "MENU", "BUTTON");
    private static final String SUPER_ADMIN_ROLE_CODE = "super_admin";
    private final MenuMapper menus;
    private final RoleMenuMapper roleMenus;
    private final RoleMapper roles;

    public MenuService(MenuMapper menus, RoleMenuMapper roleMenus, RoleMapper roles) {
        this.menus = menus;
        this.roleMenus = roleMenus;
        this.roles = roles;
    }

    public List<MenuEntity> list() {
        return menus.selectList(new LambdaQueryWrapper<MenuEntity>().orderByAsc(MenuEntity::getParentId).orderByAsc(MenuEntity::getSortOrder).orderByAsc(MenuEntity::getId));
    }

    @Transactional
    public MenuEntity create(MenuRequest request) {
        MenuEntity menu = new MenuEntity();
        apply(menu, request, null);
        menu.setBuiltin(0);
        menus.insert(menu);
        grantToSuperAdmin(menu.getId());
        return menu;
    }

    /**
     * 新建的菜单自动授权给超级管理员。
     * <p>
     * 菜单是"配了才存在、授了才可见"的：sys_menu 里加一条记录并不会自动出现在任何人的菜单树里，
     * 因为菜单树是 role → role_menu → menu 推出来的。如果不做这一步，"在浏览器里新增菜单"这个
     * 刚需流程会卡住——连建它的人自己刷新后都看不到，还得再绕去角色管理给自己授权一次。
     * <p>
     * 没有采用"超级管理员自动拥有全部菜单"的隐式绕过方案，是为了让授权关系始终显式、可审计：
     * 角色管理的授权树里能看到这条新菜单是勾选状态，而不是被一段特殊逻辑藏起来。
     */
    private void grantToSuperAdmin(Long menuId) {
        RoleEntity superAdmin = roles.selectOne(new LambdaQueryWrapper<RoleEntity>().eq(RoleEntity::getRoleCode, SUPER_ADMIN_ROLE_CODE));
        if (superAdmin != null) roleMenus.insert(superAdmin.getId(), menuId);
    }

    @Transactional
    public MenuEntity update(Long id, MenuRequest request) {
        MenuEntity menu = require(id);
        if (menu.getBuiltin() == 1 && !menu.getMenuType().equalsIgnoreCase(request.menuType().trim())) {
            throw new BusinessException("内置菜单不能修改类型");
        }
        apply(menu, request, id);
        menus.updateById(menu);
        return menu;
    }

    @Transactional
    public void remove(Long id) {
        MenuEntity menu = require(id);
        if (menu.getBuiltin() == 1) throw new BusinessException("内置菜单不能删除");
        if (menus.selectCount(new LambdaQueryWrapper<MenuEntity>().eq(MenuEntity::getParentId, id)) > 0) {
            throw new BusinessException("请先删除该菜单下的子菜单");
        }
        roleMenus.deleteByMenuId(id);
        menus.deleteById(id);
    }

    private void apply(MenuEntity menu, MenuRequest request, Long currentId) {
        String type = request.menuType().trim().toUpperCase();
        if (!TYPES.contains(type)) throw new BusinessException("菜单类型必须是目录、菜单或按钮");
        Long parentId = request.parentId() == null ? 0L : request.parentId();
        if (currentId != null && currentId.equals(parentId)) throw new BusinessException("上级菜单不能选择自身");
        if (parentId > 0) {
            MenuEntity parent = require(parentId);
            if ("BUTTON".equals(parent.getMenuType())) throw new BusinessException("按钮不能作为上级菜单");
            if (currentId != null && isDescendantOf(parent, currentId)) throw new BusinessException("不能将菜单移动到自身的子菜单下");
        }
        String routePath = trim(request.routePath());
        String component = trim(request.component());
        String permission = trim(request.permissionCode());
        if ("MENU".equals(type) && routePath == null) throw new BusinessException("菜单类型必须填写路由地址");
        // 页面菜单没有组件就无法渲染。前端对这种情况会显示"组件不存在"诊断页，
        // 但更应该在配置阶段就拦住，避免库里存下一条永远打不开的菜单。
        if ("MENU".equals(type) && component == null) throw new BusinessException("菜单类型必须填写前端组件");
        if ("BUTTON".equals(type) && permission == null) throw new BusinessException("按钮类型必须填写权限标识");
        if (routePath != null && menus.selectCount(new LambdaQueryWrapper<MenuEntity>().eq(MenuEntity::getRoutePath, routePath).ne(currentId != null, MenuEntity::getId, currentId)) > 0) {
            throw new BusinessException("路由地址已存在");
        }
        if (permission != null && menus.selectCount(new LambdaQueryWrapper<MenuEntity>().eq(MenuEntity::getPermissionCode, permission).ne(currentId != null, MenuEntity::getId, currentId)) > 0) {
            throw new BusinessException("权限标识已存在");
        }
        menu.setParentId(parentId);
        menu.setMenuName(request.menuName().trim());
        menu.setMenuType(type);
        menu.setRouteName(trim(request.routeName()));
        menu.setRoutePath(routePath);
        menu.setComponent(component);
        menu.setPermissionCode(permission);
        menu.setIcon(trim(request.icon()));
        menu.setSortOrder(request.sortOrder());
        menu.setVisible(request.visible());
        menu.setStatus(request.status());
        menu.setKeepAlive(request.keepAlive());
    }

    private MenuEntity require(Long id) {
        MenuEntity menu = menus.selectById(id);
        if (menu == null) throw new BusinessException("菜单不存在");
        return menu;
    }

    private boolean isDescendantOf(MenuEntity menu, Long ancestorId) {
        Set<Long> visited = new HashSet<>();
        MenuEntity current = menu;
        while (current.getParentId() != null && current.getParentId() > 0 && visited.add(current.getId())) {
            if (ancestorId.equals(current.getParentId())) return true;
            current = menus.selectById(current.getParentId());
            if (current == null) return false;
        }
        return false;
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
