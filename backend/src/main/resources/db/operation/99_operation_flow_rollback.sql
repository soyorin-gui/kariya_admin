-- operation 菜单回退（仅用于开发/验证环境）
-- 会物理删除本脚本创建的流水菜单、日志按钮及其所有角色授权，以释放唯一路由和权限码供下次验证。
-- 不删除“内网管理”父目录，不处理任何 operation 业务数据（M1 没有业务表）。

SET @operation_flow_menu_id = (
    SELECT id FROM sys_menu
    WHERE route_path = '/intranet/operation/flow' AND builtin = 0
    ORDER BY id LIMIT 1
);

SET @operation_directory_id = (
    SELECT id FROM sys_menu
    WHERE route_path = '/intranet/operation' AND builtin = 0
    ORDER BY id LIMIT 1
);

SET @operation_invoke_menu_id = (
    SELECT id FROM sys_menu
    WHERE route_path = '/intranet/operation/invoke' AND builtin = 0
    ORDER BY id LIMIT 1
);

SET @operation_log_list_id = (
    SELECT id FROM sys_menu
    WHERE permission_code = 'intranet:operation:log:list' AND builtin = 0
    ORDER BY id LIMIT 1
);

SET @operation_log_download_id = (
    SELECT id FROM sys_menu
    WHERE permission_code = 'intranet:operation:log:download' AND builtin = 0
    ORDER BY id LIMIT 1
);

DELETE FROM sys_role_menu
WHERE menu_id IN (@operation_flow_menu_id, @operation_invoke_menu_id, @operation_log_list_id, @operation_log_download_id, @operation_directory_id);

DELETE FROM sys_menu
WHERE id = @operation_invoke_menu_id
  AND route_path = '/intranet/operation/invoke'
  AND builtin = 0;

DELETE FROM sys_menu
WHERE id = @operation_log_list_id
  AND permission_code = 'intranet:operation:log:list'
  AND builtin = 0;

DELETE FROM sys_menu
WHERE id = @operation_log_download_id
  AND permission_code = 'intranet:operation:log:download'
  AND builtin = 0;

DELETE FROM sys_menu
WHERE id = @operation_flow_menu_id
  AND route_path = '/intranet/operation/flow'
  AND builtin = 0;

-- 只有目录下已无其它子菜单时才删除目录，避免未来日志追踪/在线验证菜单被连带影响。
DELETE FROM sys_menu
WHERE id = @operation_directory_id
  AND route_path = '/intranet/operation'
  AND builtin = 0
  AND NOT EXISTS (
      SELECT 1 FROM (
          SELECT id FROM sys_menu WHERE parent_id = @operation_directory_id
      ) AS operation_children
  );

SELECT
    @operation_flow_menu_id AS removed_flow_menu_id,
    @operation_invoke_menu_id AS removed_invoke_menu_id,
    @operation_log_list_id AS removed_log_list_id,
    @operation_log_download_id AS removed_log_download_id,
    @operation_directory_id AS operation_directory_candidate_id;
