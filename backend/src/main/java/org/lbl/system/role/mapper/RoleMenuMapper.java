package org.lbl.system.role.mapper;

import org.apache.ibatis.annotations.*;

import java.util.List;
import org.lbl.system.role.vo.RolePermissionAssignment;

@Mapper
public interface RoleMenuMapper {
    @Select("SELECT menu_id FROM sys_role_menu WHERE role_id = #{roleId}")
    List<Long> selectMenuIds(Long roleId);

    @Select("""
            SELECT m.permission_code FROM sys_role_menu rm
            INNER JOIN sys_menu m ON m.id = rm.menu_id
            WHERE rm.role_id = #{roleId} AND m.deleted = 0 AND m.status = 1
              AND m.permission_code IS NOT NULL AND m.permission_code != ''
            """)
    List<String> selectPermissionCodes(Long roleId);

    @Select("""
            <script>
            SELECT rm.role_id AS roleId, m.permission_code AS permissionCode
            FROM sys_role_menu rm
            INNER JOIN sys_menu m ON m.id = rm.menu_id
            WHERE m.deleted = 0 AND m.status = 1
              AND m.permission_code IS NOT NULL AND m.permission_code != ''
              AND rm.role_id IN
              <foreach collection='roleIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>
            </script>
            """)
    List<RolePermissionAssignment> selectPermissionCodesByRoleIds(@Param("roleIds") List<Long> roleIds);

    @Delete("DELETE FROM sys_role_menu WHERE role_id = #{roleId}")
    void deleteByRoleId(Long roleId);

    @Delete("DELETE FROM sys_role_menu WHERE menu_id = #{menuId}")
    void deleteByMenuId(Long menuId);

    @Insert("INSERT INTO sys_role_menu (role_id, menu_id) VALUES (#{roleId}, #{menuId})")
    void insert(@Param("roleId") Long roleId, @Param("menuId") Long menuId);
}
