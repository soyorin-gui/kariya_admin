-- Kariya Admin 全新数据库初始数据（先执行 init_schema.sql）
SET NAMES utf8mb4;

INSERT INTO sys_dept (id, parent_id, ancestors, dept_name, dept_code, builtin, sort_order)
VALUES (1, 0, '0', '根部门', 'ROOT', 1, 0),
       (2, 1, '0,1', '技术部', 'TECH', 0, 1),
       (3, 1, '0,1', '产品部', 'PRODUCT', 0, 2);

INSERT INTO sys_role (id, role_name, role_code, data_scope, builtin)
VALUES (1, '超级管理员', 'super_admin', 'ALL', 1),
       (2, '系统管理员', 'system_admin', 'DEPT_AND_CHILDREN', 1),
       (3, '基础用户', 'basic_role', 'SELF', 1);

-- 初始账号 admin / Admin@123；首次登录必须修改密码。
-- 生产部署后请立即完成首次登录，不要长期使用公开的初始密码。
INSERT INTO sys_user (id, username, real_name, dept_id, registration_source, status, auth_version, builtin)
VALUES (1, 'admin', '管理员', 1, 'LOCAL', 1, 1, 1);

INSERT INTO sys_local_credential (user_id, password_hash, password_change_required)
VALUES (1, '$2a$10$lK.K2T/f0lEi2nNUI9UCYu2L/OR1DaF4J7SfiIVRXsbwqDfPqkgvq', 1);

INSERT INTO sys_user_role (user_id, role_id)
VALUES (1, 1);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, route_name, route_path, component, permission_code, icon,
                      sort_order, builtin)
VALUES (1, 0, '首页', 'MENU', 'home', '/home', 'home/index', NULL, 'DashboardOutlined', 1, 1),
       (2, 0, '系统管理', 'DIR', 'system', '/system', NULL, NULL, 'SettingOutlined', 2, 1),
       (3, 2, '用户管理', 'MENU', 'system-user', '/system/user', 'system/user/index', NULL, 'UserOutlined', 1, 1),
       (4, 2, '角色管理', 'MENU', 'system-role', '/system/role', 'system/role/index', NULL, 'SafetyOutlined', 2, 1),
       (5, 2, '部门管理', 'MENU', 'system-dept', '/system/dept', 'system/dept/index', NULL, 'ApartmentOutlined', 3, 1),
       (6, 2, '菜单管理', 'MENU', 'system-menu', '/system/menu', 'system/menu/index', NULL, 'MenuOutlined', 4, 1),
       (13, 2, '登录日志', 'MENU', 'system-login-log', '/system/login-log', 'system/loginlog/index', NULL, 'FileSearchOutlined', 5, 1),
       (14, 2, '操作日志', 'MENU', 'system-operation-log', '/system/operation-log', 'system/operatelog/index', NULL, 'HistoryOutlined', 6, 1);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, permission_code, sort_order, builtin)
VALUES (7, 3, '查询用户', 'BUTTON', 'system:user:list', 1, 1),
       (8, 3, '新增用户', 'BUTTON', 'system:user:add', 2, 1),
       (9, 3, '更新用户', 'BUTTON', 'system:user:update', 3, 1),
       (10, 3, '删除用户', 'BUTTON', 'system:user:delete', 4, 1),
       (11, 3, '重置密码', 'BUTTON', 'system:user:reset-password', 5, 1),
       (12, 3, '导出用户', 'BUTTON', 'system:user:export', 6, 1),
       (15, 13, '查询登录日志', 'BUTTON', 'system:loginlog:list', 1, 1),
       (16, 13, '删除登录日志', 'BUTTON', 'system:loginlog:delete', 2, 1),
       (17, 14, '查询操作日志', 'BUTTON', 'system:operatelog:list', 1, 1),
       (18, 14, '删除操作日志', 'BUTTON', 'system:operatelog:delete', 2, 1),
       (19, 4, '查询角色', 'BUTTON', 'system:role:list', 1, 1),
       (20, 4, '新增角色', 'BUTTON', 'system:role:add', 2, 1),
       (21, 4, '更新角色', 'BUTTON', 'system:role:update', 3, 1),
       (22, 4, '删除角色', 'BUTTON', 'system:role:delete', 4, 1),
       (23, 4, '授权角色', 'BUTTON', 'system:role:grant', 5, 1),
       (24, 5, '查询部门', 'BUTTON', 'system:dept:list', 1, 1),
       (25, 5, '新增部门', 'BUTTON', 'system:dept:add', 2, 1),
       (26, 5, '更新部门', 'BUTTON', 'system:dept:update', 3, 1),
       (27, 5, '删除部门', 'BUTTON', 'system:dept:delete', 4, 1),
       (28, 6, '查询菜单', 'BUTTON', 'system:menu:list', 1, 1),
       (29, 6, '新增菜单', 'BUTTON', 'system:menu:add', 2, 1),
       (30, 6, '更新菜单', 'BUTTON', 'system:menu:update', 3, 1),
       (31, 6, '删除菜单', 'BUTTON', 'system:menu:delete', 4, 1),
       (32, 1, '使用 AI 助手', 'BUTTON', 'agent:chat:use', 1, 1);

-- 超级管理员拥有全部页面和按钮权限。
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu;

-- 系统管理员保留原有用户管理权限，不授予重置密码与导出权限。
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, id FROM sys_menu WHERE id IN (1, 2, 3, 7, 8, 9, 10);

-- 普通注册用户默认只能访问首页。
INSERT INTO sys_role_menu (role_id, menu_id)
VALUES (3, 1);
