-- 在执行本脚本前备份 sys_approval_task。部门审批人角色需由管理员分配给各部门候选人。
ALTER TABLE sys_approval_task
    ADD COLUMN candidate_dept_id BIGINT NULL AFTER assigned_user_name,
    ADD COLUMN candidate_dept_name VARCHAR(80) NULL AFTER candidate_dept_id,
    ADD COLUMN candidate_role_code VARCHAR(80) NULL AFTER candidate_dept_name,
    ADD COLUMN candidate_role_name VARCHAR(80) NULL AFTER candidate_role_code,
    ADD INDEX idx_approval_task_candidate (candidate_dept_id, candidate_role_code, status);

INSERT INTO sys_role (role_name, role_code, data_scope, status, builtin)
SELECT '部门审批人', 'department_approver', 'SELF', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code='department_approver');

-- 将已有部门调动待办转为候选组；已办理记录保留原办理人快照。
UPDATE sys_approval_task t
JOIN sys_approval_instance a ON a.id=t.approval_instance_id
JOIN sys_department_change r ON r.id=a.business_id AND a.business_type='DEPARTMENT_CHANGE'
LEFT JOIN sys_dept d ON d.id=CASE WHEN t.step_type='SOURCE' THEN r.from_dept_id ELSE r.target_dept_id END
SET t.assigned_user_id=NULL, t.assigned_user_name=NULL,
    t.candidate_dept_id=d.id, t.candidate_dept_name=d.dept_name,
    t.candidate_role_code='department_approver', t.candidate_role_name='部门审批人'
WHERE a.status='PENDING' AND t.status IN ('PENDING', 'WAITING');
