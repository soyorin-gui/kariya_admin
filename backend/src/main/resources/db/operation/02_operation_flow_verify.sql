-- operation M1 菜单和权限验证（只读，可重复执行）

SELECT
    id, parent_id, menu_name, menu_type, route_name, route_path,
    component, permission_code, icon, sort_order, visible, status, deleted
FROM sys_menu
WHERE route_path IN ('/intranet/operation', '/intranet/operation/flow', '/intranet/operation/logs')
   OR permission_code IN ('intranet:operation:flow:list', 'intranet:operation:log:list', 'intranet:operation:log:download')
ORDER BY parent_id, sort_order, id;

SELECT
    r.id AS role_id,
    r.role_code,
    r.role_name,
    m.id AS menu_id,
    m.menu_name,
    m.route_path,
    m.permission_code
FROM sys_role_menu rm
INNER JOIN sys_role r ON r.id = rm.role_id
INNER JOIN sys_menu m ON m.id = rm.menu_id
WHERE m.route_path IN ('/intranet/operation', '/intranet/operation/flow', '/intranet/operation/logs')
   OR m.permission_code = 'intranet:operation:log:download'
ORDER BY r.role_code, m.sort_order, m.id;

-- 如果需要确认某个用户是否绑定了上面的角色，请替换用户名后执行：
-- SELECT u.username, r.role_code
-- FROM sys_user u
-- INNER JOIN sys_user_role ur ON ur.user_id = u.id
-- INNER JOIN sys_role r ON r.id = ur.role_id
-- WHERE u.username = '你的用户名' AND u.deleted = 0 AND r.deleted = 0;
