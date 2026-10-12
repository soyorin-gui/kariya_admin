-- 测试审批部门；为真实环境初始化前可按需删除或重建。
INSERT INTO sys_dept (parent_id, ancestors, dept_name, dept_code, sort_order, status, builtin)
SELECT 1, '0,1', '内网需求审批部', 'INTRANET_REQUIREMENT_APPROVAL', 90, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_dept WHERE dept_code='INTRANET_REQUIREMENT_APPROVAL');

INSERT INTO sys_dept (parent_id, ancestors, dept_name, dept_code, sort_order, status, builtin)
SELECT 1, '0,1', '内网研发部', 'INTRANET_DEVELOPMENT', 91, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_dept WHERE dept_code='INTRANET_DEVELOPMENT');

INSERT INTO sys_role (role_name, role_code, data_scope, status, builtin)
SELECT '内网开发人员', 'intranet_developer', 'SELF', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code='intranet_developer');

-- 将 department_approver 角色授予该部门内的测试审批人，示例：
-- INSERT INTO sys_user_role (user_id, role_id)
-- SELECT <审批用户ID>, r.id FROM sys_role r WHERE r.role_code='department_approver';

-- 将 intranet_developer 角色授予“内网研发部”的开发人员，示例：
-- INSERT INTO sys_user_role (user_id, role_id)
-- SELECT <开发用户ID>, r.id FROM sys_role r WHERE r.role_code='intranet_developer';
