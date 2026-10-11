INSERT INTO sys_menu (parent_id, menu_name, menu_type, route_name, route_path, component, icon, sort_order, visible, status, keep_alive, builtin)
SELECT parent.id, '数据资产管理', 'MENU', 'intranet-dataasset', '/intranet/dataasset', 'intranet/dataasset/index', 'DatabaseOutlined', 2, 1, 1, 0, 0
FROM sys_menu parent
WHERE parent.route_path = '/intranet'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE route_path = '/intranet/dataasset');

INSERT INTO sys_menu (parent_id, menu_name, menu_type, permission_code, sort_order, visible, status, keep_alive, builtin)
SELECT parent.id, seed.menu_name, 'BUTTON', seed.permission_code, seed.sort_order, 1, 1, 0, 0
FROM sys_menu parent
JOIN (
 SELECT '查询数据资产' menu_name, 'intranet:dataasset:list' permission_code, 1 sort_order
 UNION ALL SELECT '新增数据资产', 'intranet:dataasset:add', 2
 UNION ALL SELECT '更新数据资产', 'intranet:dataasset:update', 3
 UNION ALL SELECT '删除数据资产', 'intranet:dataasset:delete', 4
) seed
WHERE parent.route_path = '/intranet/dataasset'
  AND NOT EXISTS (SELECT 1 FROM sys_menu existing WHERE existing.permission_code = seed.permission_code);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT role.id, menu.id
FROM sys_role role
JOIN sys_menu menu ON menu.route_path = '/intranet/dataasset'
    OR menu.permission_code IN ('intranet:dataasset:list', 'intranet:dataasset:add', 'intranet:dataasset:update', 'intranet:dataasset:delete')
WHERE role.role_code = 'super_admin'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu granted WHERE granted.role_id = role.id AND granted.menu_id = menu.id);
