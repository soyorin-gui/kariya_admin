INSERT INTO sys_menu (parent_id, menu_name, menu_type, route_name, route_path, component, icon, sort_order, visible, status, keep_alive, builtin)
SELECT parent.id, '需求管理', 'MENU', 'intranet-requirement', '/intranet/requirement', 'intranet/requirement/index', 'FileTextOutlined', 2, 1, 1, 0, 0
FROM sys_menu parent
WHERE parent.route_path = '/intranet'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE route_path = '/intranet/requirement');

INSERT INTO sys_menu (parent_id, menu_name, menu_type, permission_code, sort_order, visible, status, keep_alive, builtin)
SELECT parent.id, value.menu_name, 'BUTTON', CONCAT('intranet:requirement:', value.action), value.sort_order, 1, 1, 0, 0
FROM sys_menu parent
JOIN (
    SELECT '查询需求' menu_name, 'list' action, 1 sort_order
    UNION ALL SELECT '新建需求', 'add', 2
    UNION ALL SELECT '编辑需求', 'update', 3
    UNION ALL SELECT '提交审批', 'submit', 4
    UNION ALL SELECT '分配开发人员', 'assign', 5
) value
WHERE parent.route_path = '/intranet/requirement'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission_code = CONCAT('intranet:requirement:', value.action));

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT role.id, menu.id
FROM sys_role role JOIN sys_menu menu ON menu.route_path IN ('/intranet/requirement')
    OR menu.permission_code LIKE 'intranet:requirement:%'
WHERE role.role_code = 'super_admin'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = role.id AND rm.menu_id = menu.id);
