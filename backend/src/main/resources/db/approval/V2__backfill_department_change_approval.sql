-- 执行前必须先执行 V1。该脚本只回填公共审批表，不删除或修改旧部门调动数据。
INSERT INTO sys_approval_instance
    (business_type, business_id, business_title, business_summary,
     requester_id, requester_name, status, current_step, approval_round,
     submitted_time, finished_time, created_time, updated_time)
SELECT 'DEPARTMENT_CHANGE', r.id,
       CONCAT(COALESCE(u.real_name, '未知用户'), '：',
              COALESCE(fd.dept_name, '未分配部门'), ' → ', COALESCE(td.dept_name, '已删除部门')),
       r.reason, r.requester_id, COALESCE(u.real_name, '未知用户'),
       CASE
           WHEN r.status IN ('PENDING_SOURCE', 'PENDING_TARGET') THEN 'PENDING'
           ELSE r.status
       END,
       r.current_step, 1, r.created_time, r.finished_time, r.created_time, r.updated_time
  FROM sys_department_change_request r
  LEFT JOIN sys_user u ON u.id = r.requester_id
  LEFT JOIN sys_dept fd ON fd.id = r.from_dept_id
  LEFT JOIN sys_dept td ON td.id = r.target_dept_id
 WHERE NOT EXISTS (
       SELECT 1 FROM sys_approval_instance a
        WHERE a.business_type = 'DEPARTMENT_CHANGE' AND a.business_id = r.id
 );

INSERT INTO sys_approval_task
    (approval_instance_id, approval_round, step_order, step_type, step_name,
     assigned_user_id, assigned_user_name, status,
     decided_by, decided_by_name, decision_reason, decided_time,
     created_time, updated_time)
SELECT a.id, 1, s.step_order, s.step_type,
       CASE s.step_type WHEN 'SOURCE' THEN '原部门审批' WHEN 'TARGET' THEN '目标部门审批' ELSE s.step_type END,
       s.assigned_user_id, au.real_name,
       CASE
           WHEN r.status IN ('CANCELLED', 'REJECTED') AND s.status IN ('PENDING', 'WAITING') THEN 'CANCELLED'
           ELSE s.status
       END,
       s.decided_by, du.real_name, s.decision_reason, s.decided_time,
       s.created_time, s.updated_time
  FROM sys_department_change_step s
  JOIN sys_department_change_request r ON r.id = s.request_id
  JOIN sys_approval_instance a ON a.business_type = 'DEPARTMENT_CHANGE' AND a.business_id = r.id
  LEFT JOIN sys_user au ON au.id = s.assigned_user_id
  LEFT JOIN sys_user du ON du.id = s.decided_by
 WHERE NOT EXISTS (
       SELECT 1 FROM sys_approval_task t
        WHERE t.approval_instance_id = a.id
          AND t.approval_round = 1
          AND t.step_order = s.step_order
 );

-- 两个结果均应为 0；不为 0 时不得部署切换后的应用代码。
SELECT COUNT(*) AS missing_instance_count
  FROM sys_department_change_request r
 WHERE NOT EXISTS (
       SELECT 1 FROM sys_approval_instance a
        WHERE a.business_type = 'DEPARTMENT_CHANGE' AND a.business_id = r.id
 );

SELECT COUNT(*) AS missing_task_count
  FROM sys_department_change_step s
  JOIN sys_approval_instance a ON a.business_type = 'DEPARTMENT_CHANGE' AND a.business_id = s.request_id
 WHERE NOT EXISTS (
       SELECT 1 FROM sys_approval_task t
        WHERE t.approval_instance_id = a.id
          AND t.approval_round = 1
          AND t.step_order = s.step_order
 );
