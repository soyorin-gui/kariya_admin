-- operation M1/M2 菜单与权限（MySQL 8.0+，仅用于开发/验证环境）
--
-- 作用：让前端动态菜单发现已经实现的流水查询页面，并把访问权限授予指定角色。
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
    @operation_directory_id, '日志追踪', 'MENU', 'intranet-operation-logs',
    '/intranet/operation/logs', 'intranet/operation/logs/index', 'intranet:operation:log:list',
    'FileSearchOutlined', 20, 1, 1, 0, 0
WHERE @operation_directory_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE route_path = '/intranet/operation/logs')
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission_code = 'intranet:operation:log:list');

SET @operation_log_menu_id = (
    SELECT id FROM sys_menu
    WHERE route_path = '/intranet/operation/logs' AND deleted = 0
    ORDER BY id LIMIT 1
);

INSERT INTO sys_menu
    (parent_id, menu_name, menu_type, route_name, route_path, component, permission_code,
     icon, sort_order, visible, status, keep_alive, builtin)
SELECT
    @operation_log_menu_id, '下载历史日志', 'BUTTON', NULL, NULL, NULL,
    'intranet:operation:log:download', NULL, 10, 1, 1, 0, 0
WHERE @operation_log_menu_id IS NOT NULL
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
SELECT @operation_role_id, @operation_log_menu_id
WHERE @operation_role_id IS NOT NULL AND @operation_log_menu_id IS NOT NULL;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT @operation_role_id, @operation_log_download_id
WHERE @operation_role_id IS NOT NULL AND @operation_log_download_id IS NOT NULL;

-- 三个 id 都非 NULL 才表示菜单与授权前提满足。执行后退出登录并重新登录。
SELECT
    @intranet_parent_id AS intranet_parent_id,
    @operation_directory_id AS operation_directory_id,
    @operation_flow_menu_id AS operation_flow_menu_id,
    @operation_log_menu_id AS operation_log_menu_id,
    @operation_log_download_id AS operation_log_download_id,
    @operation_role_id AS granted_role_id,
    @operation_role_code AS granted_role_code;
