-- Run once on databases created before password_change_required was added.
ALTER TABLE sys_user ADD COLUMN password_change_required TINYINT NOT NULL DEFAULT 0 AFTER auth_version;

-- Password resets are reserved for super administrators.
DELETE rm FROM sys_role_menu rm
INNER JOIN sys_role r ON r.id = rm.role_id
INNER JOIN sys_menu m ON m.id = rm.menu_id
WHERE r.role_code = 'system_admin' AND m.permission_code = 'system:user:reset-password';

-- ============================================================================
-- 审计日志：老库升级脚本（在已有数据库上执行一次）
--
-- 新库不需要执行本脚本：init__schema.sql 已包含这些索引，
-- 菜单/按钮权限则由 SystemPermissionInitializer 在应用启动时幂等补建并授权给超级管理员。
-- 索引是 DDL，Java 侧无法代劳，所以只有这一部分需要手工执行。
-- ============================================================================

-- 列表按时间倒序 + 时间范围过滤、以及 LogRetentionJob 的定期清理，都依赖时间索引。
ALTER TABLE sys_login_log ADD INDEX idx_login_log_time (login_time);
ALTER TABLE sys_operation_log ADD INDEX idx_operation_log_time (created_time);

-- 模块是等值筛选，复合索引可同时服务"按模块 + 按时间"的查询。
ALTER TABLE sys_operation_log ADD INDEX idx_operation_log_module (module, created_time);

-- 检查是否已生效：
--   SHOW INDEX FROM sys_login_log;
--   SHOW INDEX FROM sys_operation_log;
