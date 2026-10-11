-- P1-03 数据资产字段设计隐藏页：不显示在侧边栏，但必须存在路由菜单才能通过地址访问。
INSERT INTO sys_menu (parent_id, menu_name, menu_type, route_name, route_path, component, icon, sort_order, visible, status, keep_alive, builtin)
SELECT parent.id, '数据资产字段设计', 'MENU', 'intranet-dataasset-fields', '/intranet/dataasset/fields', 'intranet/dataasset/fields/index', 'ProfileOutlined', 1, 0, 1, 0, 0
FROM sys_menu parent
WHERE parent.route_path = '/intranet/dataasset'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE route_path = '/intranet/dataasset/fields');

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT role.id, menu.id
FROM sys_role role
JOIN sys_menu menu ON menu.route_path = '/intranet/dataasset/fields'
WHERE role.role_code = 'super_admin'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu granted WHERE granted.role_id = role.id AND granted.menu_id = menu.id);
