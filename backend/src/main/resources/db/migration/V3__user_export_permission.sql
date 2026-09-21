INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, permission_code, sort_order, visible, status, keep_alive,
                      builtin, deleted)
VALUES (12, 3, '导出用户', 'BUTTON', 'system:user:export', 6, 1, 1, 0, 1, 0);
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT id, 12
FROM sys_role
WHERE role_code = 'super_admin';
