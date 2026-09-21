INSERT INTO sys_dept (id, parent_id, ancestors, dept_name, dept_code, builtin, sort_order)
VALUES (1, 0, '0', '根部门', 'ROOT', 1, 0),
       (2, 1, '0,1', '技术部', 'TECH', 0, 1),
       (3, 1, '0,1', '产品部', 'PRODUCT', 0, 2);
INSERT INTO sys_role (id, role_name, role_code, data_scope, builtin)
VALUES (1, '超级管理员', 'super_admin', 'ALL', 1),
       (2, '系统管理员', 'system_admin', 'DEPT_AND_CHILDREN', 1);
INSERT INTO sys_user (id, username, password_hash, real_name, phone, email, dept_id, status, auth_version, builtin)
VALUES (1, 'admin', '$2a$10$.Dq4aa2TDEz3Zqj1N2fvZe1tlJo1NpR2xeDcRgGAa8X40kVkVIKLa', '管理员', '13800000001',
        'admin@kariya.local', 1, 1, 1, 1),
       (2, 'zhangsan', '$2a$10$.Dq4aa2TDEz3Zqj1N2fvZe1tlJo1NpR2xeDcRgGAa8X40kVkVIKLa', '张三', '13800000002',
        'zhangsan@kariya.local', 3, 1, 1, 0);
INSERT INTO sys_user_role
VALUES (1, 1),
       (2, 2);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, route_name, route_path, component, permission_code, icon,
                      sort_order, builtin)
VALUES (1, 0, '首页', 'MENU', 'home', '/home', 'home/index', NULL, 'DashboardOutlined', 1, 1),
       (2, 0, '系统管理', 'DIR', 'system', '/system', NULL, NULL, 'SettingOutlined', 2, 1),
       (3, 2, '用户管理', 'MENU', 'system-user', '/system/user', 'system/user/index', NULL, 'UserOutlined', 1, 1),
       (4, 2, '角色管理', 'MENU', 'system-role', '/system/role', 'system/role/index', NULL, 'SafetyOutlined', 2, 1),
       (5, 2, '部门管理', 'MENU', 'system-dept', '/system/dept', 'system/dept/index', NULL, 'ApartmentOutlined', 3, 1),
       (6, 2, '菜单管理', 'MENU', 'system-menu', '/system/menu', 'system/menu/index', NULL, 'MenuOutlined', 4, 1),
       (7, 3, '查询用户', 'BUTTON', NULL, NULL, NULL, 'system:user:list', NULL, 1, 1),
       (8, 3, '新增用户', 'BUTTON', NULL, NULL, NULL, 'system:user:add', NULL, 2, 1),
       (9, 3, '更新用户', 'BUTTON', NULL, NULL, NULL, 'system:user:update', NULL, 3, 1),
       (10, 3, '删除用户', 'BUTTON', NULL, NULL, NULL, 'system:user:delete', NULL, 4, 1),
       (11, 3, '重置密码', 'BUTTON', NULL, NULL, NULL, 'system:user:reset-password', NULL, 5, 1);
INSERT INTO sys_role_menu
SELECT 1, id
FROM sys_menu;
INSERT INTO sys_role_menu
SELECT 2, id
FROM sys_menu
WHERE id IN (1, 2, 3, 7, 8, 9, 10, 11);
