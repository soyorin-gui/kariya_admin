package org.lbl.system.menu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.lbl.system.menu.entity.MenuEntity;
import org.lbl.system.user.vo.UserPermissionAssignment;

import java.util.List;

@Mapper
public interface MenuMapper extends BaseMapper<MenuEntity> {
    @Select("""
            WITH RECURSIVE granted AS (
                SELECT DISTINCT m.* FROM sys_menu m
                INNER JOIN sys_role_menu rm ON rm.menu_id = m.id
                INNER JOIN sys_user_role ur ON ur.role_id = rm.role_id
                INNER JOIN sys_role r ON r.id = ur.role_id
                WHERE ur.user_id = #{userId} AND m.deleted = 0 AND m.status = 1
                  AND r.deleted = 0 AND r.status = 1
            ), menu_tree AS (
                SELECT * FROM granted
                UNION
                SELECT parent.* FROM sys_menu parent
                INNER JOIN menu_tree child ON child.parent_id = parent.id
                WHERE parent.deleted = 0 AND parent.status = 1
            )
            SELECT DISTINCT * FROM menu_tree
            ORDER BY parent_id, sort_order, id
            """)
    List<MenuEntity> selectByUserId(Long userId);

    /** 一次计算一批用户的有效权限，供列表权限按钮批量装配。 */
    @Select("""
            <script>
            WITH RECURSIVE granted AS (
                SELECT DISTINCT ur.user_id, m.id, m.parent_id, m.permission_code
                FROM sys_user_role ur
                INNER JOIN sys_role r ON r.id = ur.role_id AND r.deleted = 0 AND r.status = 1
                INNER JOIN sys_role_menu rm ON rm.role_id = ur.role_id
                INNER JOIN sys_menu m ON m.id = rm.menu_id AND m.deleted = 0 AND m.status = 1
                WHERE ur.user_id IN
                <foreach collection='userIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>
            ), menu_tree AS (
                SELECT * FROM granted
                UNION
                SELECT child.user_id, parent.id, parent.parent_id, parent.permission_code
                FROM sys_menu parent
                INNER JOIN menu_tree child ON child.parent_id = parent.id
                WHERE parent.deleted = 0 AND parent.status = 1
            )
            SELECT DISTINCT user_id AS userId, permission_code AS permissionCode
            FROM menu_tree
            WHERE permission_code IS NOT NULL AND permission_code != ''
            </script>
            """)
    List<UserPermissionAssignment> selectPermissionCodesByUserIds(@Param("userIds") List<Long> userIds);

    /**
     * 统计占用该路由地址的菜单数，<b>包含已被逻辑删除的记录</b>。
     * <p>
     * 口径必须与 {@code sys_menu} 上的唯一索引一致：逻辑删除只把 {@code deleted} 置 1，
     * 行和索引项都还在表里，因此"删掉一个菜单再建一条同路径的"必然撞唯一键。
     * 而 MyBatis-Plus 的 {@code selectCount} 会自动追加 {@code deleted = 0}，
     * 用它做预检会出现"校验通过、插入失败"——用户看到的是一个无法理解的失败。
     * 与 {@code UserMapper#countIncludingDeletedByUsername} 同一约定，原因也相同。
     * <p>
     * 动态 SQL 说明：这里刻意用纯静态 SQL + "排除 0 号 id"表达"不排除任何行"。
     * 注解式动态 SQL 需要 {@code <script>} 包裹，而 Java 文本块的第一行必然是换行
     * （JLS 要求开分隔符后跟行终止符），能否被正确识别取决于 MyBatis 内部对脚本的 trim 行为；
     * 一旦不成立，出错方式是把 {@code <script>} 当 SQL 发给数据库，很难排查。
     *
     * @param excludeId 编辑场景下需排除的自身 id；新增时传 {@code 0}（id 自增从 1 开始，不会命中）
     */
    @Select("""
            SELECT COUNT(1) FROM sys_menu
            WHERE route_path = #{routePath} AND id != #{excludeId}
            """)
    long countIncludingDeletedByRoutePath(@Param("routePath") String routePath, @Param("excludeId") long excludeId);

    /** 统计占用该权限标识的菜单数，含已逻辑删除，口径同 {@link #countIncludingDeletedByRoutePath}。 */
    @Select("""
            SELECT COUNT(1) FROM sys_menu
            WHERE permission_code = #{permissionCode} AND id != #{excludeId}
            """)
    long countIncludingDeletedByPermissionCode(@Param("permissionCode") String permissionCode, @Param("excludeId") long excludeId);

    /**
     * 按路由地址取一条记录，<b>包含已逻辑删除的行</b>，未删除的优先、其次 id 最小。
     * <p>
     * 只给 {@code SystemPermissionInitializer} 的异常分支用：当插入撞上唯一索引、
     * 而按"未删除"口径又查不到时，需要判断是不是一条<b>已被逻辑删除</b>的历史菜单
     * 仍占着这个地址（UNIQUE 索引看不到 {@code deleted}）。
     * 分得清这一点，启动日志才能给出"是哪条历史记录挡住了"，而不是一句无法定位的报错。
     */
    @Select("SELECT * FROM sys_menu WHERE route_path = #{routePath} ORDER BY deleted, id LIMIT 1")
    MenuEntity selectAnyByRoutePath(@Param("routePath") String routePath);

    /** 按权限标识取一条记录，含已逻辑删除，用途同 {@link #selectAnyByRoutePath}。 */
    @Select("SELECT * FROM sys_menu WHERE permission_code = #{permissionCode} ORDER BY deleted, id LIMIT 1")
    MenuEntity selectAnyByPermissionCode(@Param("permissionCode") String permissionCode);
}
