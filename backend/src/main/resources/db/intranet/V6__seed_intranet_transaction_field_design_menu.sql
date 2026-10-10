-- 隐藏字段设计页：不显示在侧边栏，但必须随角色授权，才能通过地址访问。
INSERT INTO sys_menu (parent_id, menu_name, menu_type, route_name, route_path, component, icon, sort_order, visible, status, keep_alive, builtin)
SELECT parent.id, '交易字段设计', 'MENU', 'intranet-transaction-fields', '/intranet/transaction/fields', 'intranet/transaction/fields/index', 'ProfileOutlined', 2, 0, 1, 0, 0
FROM sys_menu parent
WHERE parent.route_path = '/intranet/transaction'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE route_path = '/intranet/transaction/fields');

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT role.id, menu.id
FROM sys_role role
JOIN sys_menu menu ON menu.route_path = '/intranet/transaction/fields'
WHERE role.role_code = 'super_admin'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu granted WHERE granted.role_id = role.id AND granted.menu_id = menu.id);
