-- ============================================================================
--  消息通知数据表（sys_notification）的测试插入 SQL
--  用途：验证「顶栏铃铛下拉」与「消息中心页」的样式、未读红点、点击效果。
--
--  ⚠️ 插入后不会触发 WebSocket 实时推送（那是应用层写库时才做的），
--     所以：插入完成后【刷新页面】，铃铛和消息中心就会看到。切走再切回标签页也会刷新。
--
--  ⚠️ 把 recipient_id 改成你自己的登录用户 id（默认管理员 admin = 1）。
-- ============================================================================

-- ────────────────────────────────────────────────────────────────────────────
-- 第 1 组：普通通知（无 business_id）
-- 点击效果：标记已读 → 跳到 /account/notifications（不弹任何详情）
-- 用它验证：列表条目样式、未读蓝点/蓝底、标题+正文+时间的排版、"全部标为已读"。
-- ────────────────────────────────────────────────────────────────────────────
INSERT INTO sys_notification (recipient_id, type, title, content, business_type, business_id)
VALUES
    (1, 'SYSTEM_NOTICE', '系统维护通知', '系统将于本周六 22:00–23:00 进行维护升级，期间服务将短暂不可用，请提前保存好手头工作。', NULL, NULL),
    (1, 'SECURITY_ALERT', '安全提醒', '检测到你的账号近期在多台设备登录。如非本人操作，请尽快修改密码并退出不认识的设备。', NULL, NULL),
    (1, 'TODO_REMIND', '待办提醒', '你有一条新的审批待办等待处理，请及时前往处理，避免流程积压。', NULL, NULL);

-- ────────────────────────────────────────────────────────────────────────────
-- 第 2 组：一条「部门变更审批」通知（business_type = 'DEPARTMENT_CHANGE'）
-- 点击效果：跳到 /account/notifications?businessId=<id>，并弹出「部门变更审批进度」弹窗
--          （展示申请人与 Steps 进度；若当前登录者是超管或当前步骤审批人，还会出现 通过/拒绝 按钮）。
--
-- 需要先造一条真实的部门变更申请，再挂通知。分三步：
-- ⚠️ requester_id 务必填一个【不是当前登录账号】的用户 id（系统禁止审批自己的申请，
--    否则弹窗能打开，但没有"通过/拒绝"按钮）。下面的 2 仅作示例，请改成真实存在的用户 id。
-- ⚠️ from_dept_id / target_dept_id 填真实存在的部门 id（默认种子数据里 1=根部门 2=技术部 3=产品部）。
-- ────────────────────────────────────────────────────────────────────────────

-- ① 申请主单：当前停在"目标部门审批"阶段（PENDING_TARGET，current_step=2）
INSERT INTO sys_department_change_request (requester_id, from_dept_id, target_dept_id, reason, status, current_step, version)
VALUES (2, 2, 3, 'SQL 插入的测试申请：希望从技术部转到产品部，用于验证消息通知点击效果。', 'PENDING_TARGET', 2, 1);

SET @request_id = LAST_INSERT_ID();

-- ② 两条审批步骤：原部门已通过，目标部门待审批（assigned_user_id 为 NULL = 等超管处理）
INSERT INTO sys_department_change_step (request_id, step_order, step_type, dept_id, assigned_user_id, status, decided_by, decision_reason, decided_time)
VALUES
    (@request_id, 1, 'SOURCE', 2, 1, 'APPROVED', 1, '同意转出', NOW()),
    (@request_id, 2, 'TARGET', 3, NULL, 'PENDING', NULL, NULL, NULL);

-- ③ 给当前登录用户（这里写 1 = admin）挂一条对应的通知
INSERT INTO sys_notification (recipient_id, type, title, content, business_type, business_id)
VALUES (1, 'DEPARTMENT_APPROVAL_REQUIRED', '待审批的部门变更申请', '有用户提交了部门变更申请，当前等待目标部门审批，请处理当前步骤。', 'DEPARTMENT_CHANGE', @request_id);

-- ────────────────────────────────────────────────────────────────────────────
-- 验证 / 清理
-- ────────────────────────────────────────────────────────────────────────────

-- 看刚插入的通知（按 id 倒序，前 5 条）
SELECT id, recipient_id, type, title, business_type, business_id, read_time, created_time
FROM sys_notification
ORDER BY id DESC
LIMIT 5;

-- 测试完想清掉（把 @request_id 换成上面的实际值；通知直接按 id 删）
-- DELETE FROM sys_notification WHERE id IN (刚插入的 3 个普通通知 id);
-- DELETE FROM sys_notification WHERE business_type = 'DEPARTMENT_CHANGE' AND business_id = @request_id;
-- DELETE FROM sys_department_change_step WHERE request_id = @request_id;
-- DELETE FROM sys_department_change_request WHERE id = @request_id;
