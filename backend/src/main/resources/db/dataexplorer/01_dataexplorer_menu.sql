-- 数据查询工作台菜单（MySQL 8.0+，仅用于开发/验证环境）
--
-- 作用：创建“数据查询工作台”页面菜单，并授权指定角色访问前端 Mock 工作台。
-- 不创建业务表、不配置真实 Elasticsearch/HBase/Redis 连接，也不插入任何业务数据。
-- 执行前请确认当前连接的是可验证的开发数据库，并自行备份 sys_menu / sys_role_menu。

-- 默认授权超级管理员；需要给其它角色验证时，只修改这里的角色编码。
SET @dataexplorer_role_code = 'super_admin';

SET @intranet_parent_id = (
    SELECT id FROM sys_menu
    WHERE route_path = '/intranet' AND deleted = 0
    ORDER BY id LIMIT 1
);

SET @dataexplorer_role_id = (
    SELECT id FROM sys_role
    WHERE role_code = @dataexplorer_role_code AND deleted = 0 AND status = 1
    ORDER BY id LIMIT 1
);

-- 前提：系统中已经存在“内网管理”目录（/intranet）。目录不存在时不自动新建，避免猜测整体菜单结构。
INSERT INTO sys_menu
    (parent_id, menu_name, menu_type, route_name, route_path, component, permission_code,
     icon, sort_order, visible, status, keep_alive, builtin)
SELECT
    @intranet_parent_id, '数据查询工作台', 'MENU', 'intranet-dataexplorer',
    '/intranet/dataexplorer', 'intranet/dataexplorer/index', 'intranet:dataexplorer:list',
    'FileSearchOutlined', 60, 1, 1, 0, 0
WHERE @intranet_parent_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE route_path = '/intranet/dataexplorer' AND deleted = 0)
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission_code = 'intranet:dataexplorer:list' AND deleted = 0);

SET @dataexplorer_menu_id = (
    SELECT id FROM sys_menu
    WHERE route_path = '/intranet/dataexplorer' AND deleted = 0
    ORDER BY id LIMIT 1
);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT @dataexplorer_role_id, @dataexplorer_menu_id
WHERE @dataexplorer_role_id IS NOT NULL AND @dataexplorer_menu_id IS NOT NULL;

-- 执行后确认三个 id 都非 NULL，然后退出登录并重新登录刷新菜单和权限。
SELECT
    @intranet_parent_id AS intranet_parent_id,
    @dataexplorer_menu_id AS dataexplorer_menu_id,
    @dataexplorer_role_id AS granted_role_id,
    @dataexplorer_role_code AS granted_role_code;
