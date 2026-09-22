package org.kariya.system.menu;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

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
}
