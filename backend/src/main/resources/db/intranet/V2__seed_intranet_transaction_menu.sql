INSERT INTO sys_menu (parent_id, menu_name, menu_type, route_name, route_path, icon, sort_order, visible, status, keep_alive, builtin)
SELECT 0, '内网业务', 'DIR', 'intranet', '/intranet', 'ApartmentOutlined', 100, 1, 1, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE route_path = '/intranet');

INSERT INTO sys_menu (parent_id, menu_name, menu_type, route_name, route_path, component, icon, sort_order, visible, status, keep_alive, builtin)
SELECT parent.id, '交易资产管理', 'MENU', 'intranet-transaction', '/intranet/transaction', 'intranet/transaction/index', 'SwapOutlined', 1, 1, 1, 0, 0
FROM sys_menu parent WHERE parent.route_path = '/intranet' AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE route_path = '/intranet/transaction');

INSERT INTO sys_menu (parent_id, menu_name, menu_type, permission_code, sort_order, visible, status, keep_alive, builtin)
SELECT parent.id, seed.menu_name, 'BUTTON', seed.permission_code, seed.sort_order, 1, 1, 0, 0
FROM sys_menu parent JOIN (
 SELECT '查询交易' menu_name, 'intranet:transaction:list' permission_code, 1 sort_order
 UNION ALL SELECT '新增交易', 'intranet:transaction:add', 2
 UNION ALL SELECT '更新交易', 'intranet:transaction:update', 3
 UNION ALL SELECT '删除交易', 'intranet:transaction:delete', 4
) seed
WHERE parent.route_path = '/intranet/transaction' AND NOT EXISTS (SELECT 1 FROM sys_menu existing WHERE existing.permission_code = seed.permission_code);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT role.id, menu.id FROM sys_role role JOIN sys_menu menu ON menu.route_path IN ('/intranet', '/intranet/transaction') OR menu.permission_code IN ('intranet:transaction:list', 'intranet:transaction:add', 'intranet:transaction:update', 'intranet:transaction:delete')
WHERE role.role_code = 'super_admin' AND NOT EXISTS (SELECT 1 FROM sys_role_menu granted WHERE granted.role_id = role.id AND granted.menu_id = menu.id);
