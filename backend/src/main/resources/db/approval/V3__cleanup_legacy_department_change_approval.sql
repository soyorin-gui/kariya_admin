-- 前置条件：AP1、AP2 已执行，且 AP2 的两个缺失计数均为 0。
-- 当前项目确认旧部门调动表无业务数据，因此本脚本不包含额外数据迁移。
-- 先精简旧业务表，再改名；最后删步骤表。若中途失败，尽量保留可识别的旧表名和步骤表便于排查。
ALTER TABLE sys_department_change_request
    DROP INDEX idx_dept_change_requester,
    DROP INDEX idx_dept_change_status,
    DROP COLUMN status,
    DROP COLUMN current_step,
    DROP COLUMN version,
    DROP COLUMN finished_time,
    ADD INDEX idx_dept_change_requester (requester_id);

RENAME TABLE sys_department_change_request TO sys_department_change;

DROP TABLE sys_department_change_step;
