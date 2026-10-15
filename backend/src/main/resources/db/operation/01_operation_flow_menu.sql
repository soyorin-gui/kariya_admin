-- operation 页面、日志按钮及响应对比权限（MySQL 8.0+，仅用于开发/验证环境）
--
-- 作用：让前端动态菜单发现流水查询、在线调试和多环境响应对比页面，并将日志按钮权限授予指定角色。
-- 不创建业务表、不插入流水数据；页面数据由 local profile 的外网模拟源提供。
-- 执行前请确认当前连接的是可验证的开发数据库，并自行备份 sys_menu / sys_role_menu。

-- 默认授权超级管理员；需要给其它角色验证时，只修改这里的角色编码。
SET @operation_role_code = 'super_admin';

SET @intranet_parent_id = (
    SELECT id FROM sys_menu
    WHERE route_path = '/intranet' AND deleted = 0
    ORDER BY id LIMIT 1
);

SET @operation_role_id = (
    SELECT id FROM sys_role
    WHERE role_code = @operation_role_code AND deleted = 0 AND status = 1
    ORDER BY id LIMIT 1
);

-- 前提：系统中已经存在“内网管理”目录（/intranet）。目录不存在时不自动新建，避免猜测整体菜单结构。
INSERT INTO sys_menu
    (parent_id, menu_name, menu_type, route_name, route_path, component, permission_code,
     icon, sort_order, visible, status, keep_alive, builtin)
SELECT
    @intranet_parent_id, '交易运行中心', 'DIR', 'intranet-operation', '/intranet/operation', NULL, NULL,
    'DashboardOutlined', 50, 1, 1, 0, 0
WHERE @intranet_parent_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE route_path = '/intranet/operation');

SET @operation_directory_id = (
    SELECT id FROM sys_menu
    WHERE route_path = '/intranet/operation' AND deleted = 0
    ORDER BY id LIMIT 1
);

INSERT INTO sys_menu
    (parent_id, menu_name, menu_type, route_name, route_path, component, permission_code,
     icon, sort_order, visible, status, keep_alive, builtin)
SELECT
    @operation_directory_id, '流水查询', 'MENU', 'intranet-operation-flow',
    '/intranet/operation/flow', 'intranet/operation/flow/index', 'intranet:operation:flow:list',
    'FileSearchOutlined', 10, 1, 1, 0, 0
WHERE @operation_directory_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE route_path = '/intranet/operation/flow')
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission_code = 'intranet:operation:flow:list');

SET @operation_flow_menu_id = (
    SELECT id FROM sys_menu
    WHERE route_path = '/intranet/operation/flow' AND deleted = 0
    ORDER BY id LIMIT 1
);

INSERT INTO sys_menu
    (parent_id, menu_name, menu_type, route_name, route_path, component, permission_code,
     icon, sort_order, visible, status, keep_alive, builtin)
SELECT
    @operation_directory_id, '交易在线调试', 'MENU', 'intranet-operation-invoke',
    '/intranet/operation/invoke', 'intranet/operation/invoke/index', 'intranet:operation:invoke:list',
    'ApiOutlined', 20, 1, 1, 0, 0
WHERE @operation_directory_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE route_path = '/intranet/operation/invoke')
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission_code = 'intranet:operation:invoke:list');

SET @operation_invoke_menu_id = (
    SELECT id FROM sys_menu
    WHERE route_path = '/intranet/operation/invoke' AND deleted = 0
    ORDER BY id LIMIT 1
);

INSERT INTO sys_menu
    (parent_id, menu_name, menu_type, route_name, route_path, component, permission_code,
     icon, sort_order, visible, status, keep_alive, builtin)
SELECT
    @operation_directory_id, '多环境响应对比', 'MENU', 'intranet-operation-compare',
    '/intranet/operation/compare', 'intranet/operation/compare/index', 'intranet:operation:compare:list',
    'ApartmentOutlined', 30, 1, 1, 0, 0
WHERE @operation_directory_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE route_path = '/intranet/operation/compare')
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission_code = 'intranet:operation:compare:list');

SET @operation_compare_menu_id = (
    SELECT id FROM sys_menu
    WHERE route_path = '/intranet/operation/compare' AND deleted = 0
    ORDER BY id LIMIT 1
);

INSERT INTO sys_menu
    (parent_id, menu_name, menu_type, route_name, route_path, component, permission_code,
     icon, sort_order, visible, status, keep_alive, builtin)
SELECT
    @operation_invoke_menu_id, '管理测试案例', 'BUTTON', NULL, NULL, NULL,
    'intranet:operation:test-case:manage', NULL, 10, 1, 1, 0, 0
WHERE @operation_invoke_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission_code = 'intranet:operation:test-case:manage' AND deleted = 0);

SET @operation_test_case_manage_id = (
    SELECT id FROM sys_menu
    WHERE permission_code = 'intranet:operation:test-case:manage' AND deleted = 0
    ORDER BY id LIMIT 1
);

-- 清理早期独立“日志追踪”菜单，日志功能现仅作为流水页内抽屉。
SET @legacy_log_menu_id = (
    SELECT id FROM sys_menu
    WHERE route_path = '/intranet/operation/logs' AND deleted = 0
    ORDER BY id LIMIT 1
);

SET @legacy_log_download_id = (
    SELECT id FROM sys_menu
    WHERE parent_id = @legacy_log_menu_id AND permission_code = 'intranet:operation:log:download' AND deleted = 0
    ORDER BY id LIMIT 1
);

DELETE FROM sys_role_menu WHERE menu_id IN (@legacy_log_download_id, @legacy_log_menu_id);

DELETE FROM sys_menu
WHERE id = @legacy_log_download_id AND builtin = 0;

DELETE FROM sys_menu
WHERE id = @legacy_log_menu_id AND route_path = '/intranet/operation/logs' AND builtin = 0;

INSERT INTO sys_menu
    (parent_id, menu_name, menu_type, route_name, route_path, component, permission_code,
     icon, sort_order, visible, status, keep_alive, builtin)
SELECT
    @operation_flow_menu_id, '查看日志', 'BUTTON', NULL, NULL, NULL,
    'intranet:operation:log:list', NULL, 10, 1, 1, 0, 0
WHERE @operation_flow_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission_code = 'intranet:operation:log:list' AND deleted = 0);

SET @operation_log_list_id = (
    SELECT id FROM sys_menu
    WHERE permission_code = 'intranet:operation:log:list' AND deleted = 0
    ORDER BY id LIMIT 1
);

INSERT INTO sys_menu
    (parent_id, menu_name, menu_type, route_name, route_path, component, permission_code,
     icon, sort_order, visible, status, keep_alive, builtin)
SELECT
    @operation_flow_menu_id, '下载日志', 'BUTTON', NULL, NULL, NULL,
    'intranet:operation:log:download', NULL, 20, 1, 1, 0, 0
WHERE @operation_flow_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission_code = 'intranet:operation:log:download');

SET @operation_log_download_id = (
    SELECT id FROM sys_menu
    WHERE permission_code = 'intranet:operation:log:download' AND deleted = 0
    ORDER BY id LIMIT 1
);

-- 同时授权目录和页面，便于角色权限树直接显示完整层级。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT @operation_role_id, @operation_directory_id
WHERE @operation_role_id IS NOT NULL AND @operation_directory_id IS NOT NULL;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT @operation_role_id, @operation_flow_menu_id
WHERE @operation_role_id IS NOT NULL AND @operation_flow_menu_id IS NOT NULL;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT @operation_role_id, @operation_invoke_menu_id
WHERE @operation_role_id IS NOT NULL AND @operation_invoke_menu_id IS NOT NULL;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT @operation_role_id, @operation_compare_menu_id
WHERE @operation_role_id IS NOT NULL AND @operation_compare_menu_id IS NOT NULL;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT @operation_role_id, @operation_test_case_manage_id
WHERE @operation_role_id IS NOT NULL AND @operation_test_case_manage_id IS NOT NULL;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT @operation_role_id, @operation_log_list_id
WHERE @operation_role_id IS NOT NULL AND @operation_log_list_id IS NOT NULL;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT @operation_role_id, @operation_log_download_id
WHERE @operation_role_id IS NOT NULL AND @operation_log_download_id IS NOT NULL;

-- 页面、按钮和角色 id 均应非 NULL。执行后退出登录并重新登录。
SELECT
    @intranet_parent_id AS intranet_parent_id,
    @operation_directory_id AS operation_directory_id,
    @operation_flow_menu_id AS operation_flow_menu_id,
    @operation_invoke_menu_id AS operation_invoke_menu_id,
    @operation_compare_menu_id AS operation_compare_menu_id,
    @operation_test_case_manage_id AS operation_test_case_manage_id,
    @operation_log_list_id AS operation_log_list_id,
    @operation_log_download_id AS operation_log_download_id,
    @operation_role_id AS granted_role_id,
    @operation_role_code AS granted_role_code;
