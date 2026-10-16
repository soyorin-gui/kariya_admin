-- 清理曾用于开发/验证的“交易监控大屏”独立菜单及授权（MySQL 8.0+）。
-- 当前大屏由“交易统计分析”页的按钮以无 Layout 新窗口打开，不再需要独立菜单或权限。
-- 仅对已执行过旧版 02_statistics_screen_menu.sql 的开发/验证数据库执行。

SET @statistics_screen_menu_id = (
    SELECT id FROM sys_menu
    WHERE route_path = '/intranet/statistics/screen' AND deleted = 0
    ORDER BY id LIMIT 1
);

DELETE FROM sys_role_menu
WHERE menu_id = @statistics_screen_menu_id;

DELETE FROM sys_menu
WHERE id = @statistics_screen_menu_id
  AND route_path = '/intranet/statistics/screen';

SELECT @statistics_screen_menu_id AS removed_statistics_screen_menu_id;
