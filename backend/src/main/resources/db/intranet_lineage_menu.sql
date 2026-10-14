-- 血缘分析菜单与只读权限（MySQL 8.0+，仅限已存在内网目录菜单的环境）
-- 前提：前端产物已包含 src/pages/intranet/lineage/index.tsx；执行后请重新登录。
-- 本脚本不删除或更新既有菜单，仅在不存在时创建并授予 super_admin。

SET @intranet_parent_id = (SELECT id FROM sys_menu WHERE route_path = '/intranet' AND deleted = 0 ORDER BY id LIMIT 1);
SET @super_admin_role_id = (SELECT id FROM sys_role WHERE role_code = 'super_admin' AND deleted = 0 ORDER BY id LIMIT 1);

-- 若 @intranet_parent_id 为 NULL，请先在菜单管理中创建“内网管理”目录菜单（路由 /intranet）。
INSERT INTO sys_menu (parent_id, menu_name, menu_type, route_name, route_path, component, permission_code, icon, sort_order, visible, status, keep_alive, builtin)
SELECT @intranet_parent_id, '血缘分析', 'MENU', 'intranet-lineage', '/intranet/lineage', 'intranet/lineage/index', 'intranet:lineage:list', 'ApartmentOutlined', 40, 1, 1, 0, 0
WHERE @intranet_parent_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE route_path = '/intranet/lineage');

SET @lineage_menu_id = (SELECT id FROM sys_menu WHERE route_path = '/intranet/lineage' AND deleted = 0 ORDER BY id LIMIT 1);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT @super_admin_role_id, @lineage_menu_id
WHERE @super_admin_role_id IS NOT NULL AND @lineage_menu_id IS NOT NULL;

-- 需要授予其它角色时，取消下一行注释并替换 role_code。
-- INSERT IGNORE INTO sys_role_menu (role_id, menu_id) SELECT id, @lineage_menu_id FROM sys_role WHERE role_code = '你的角色编码' AND deleted = 0;

SELECT @intranet_parent_id AS intranet_parent_id, @lineage_menu_id AS lineage_menu_id, @super_admin_role_id AS granted_role_id;
