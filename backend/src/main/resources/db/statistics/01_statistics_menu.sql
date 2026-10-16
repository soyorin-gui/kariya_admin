-- 交易统计分析菜单（MySQL 8.0+，仅用于开发/验证环境）
-- 不创建业务表、不配置 Redis、不创建统计任务；页面数据仅来自前端集中 Mock。
-- 执行前请确认当前连接的是可验证的开发数据库，并自行备份 sys_menu / sys_role_menu。

SET @statistics_role_code = 'super_admin';

SET @intranet_parent_id = (
    SELECT id FROM sys_menu
    WHERE route_path = '/intranet' AND deleted = 0
    ORDER BY id LIMIT 1
);

SET @statistics_role_id = (
    SELECT id FROM sys_role
    WHERE role_code = @statistics_role_code AND deleted = 0 AND status = 1
    ORDER BY id LIMIT 1
);

-- “内网管理”目录（/intranet）必须已存在；本脚本不自动猜测或创建父级目录。
INSERT INTO sys_menu
    (parent_id, menu_name, menu_type, route_name, route_path, component, permission_code,
     icon, sort_order, visible, status, keep_alive, builtin)
SELECT
    @intranet_parent_id, '交易统计分析', 'MENU', 'intranet-statistics',
    '/intranet/statistics', 'intranet/statistics/index', 'intranet:statistics:list',
    'DashboardOutlined', 70, 1, 1, 0, 0
WHERE @intranet_parent_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE route_path = '/intranet/statistics' AND deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission_code = 'intranet:statistics:list' AND deleted = 0);

SET @statistics_menu_id = (
    SELECT id FROM sys_menu
    WHERE route_path = '/intranet/statistics' AND deleted = 0
    ORDER BY id LIMIT 1
);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT @statistics_role_id, @statistics_menu_id
WHERE @statistics_role_id IS NOT NULL AND @statistics_menu_id IS NOT NULL;

SELECT
    @intranet_parent_id AS intranet_parent_id,
    @statistics_menu_id AS statistics_menu_id,
    @statistics_role_id AS granted_role_id,
    @statistics_role_code AS granted_role_code;
