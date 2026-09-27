package org.lbl.system.user.mapper;

import org.apache.ibatis.annotations.*;

import java.util.List;
import org.lbl.system.role.vo.RoleUserCount;

@Mapper
public interface UserRoleMapper {
    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    void deleteByUserId(Long userId);

    @Insert("INSERT INTO sys_user_role (user_id, role_id) VALUES (#{userId}, #{roleId})")
    void insert(@Param("userId") Long userId, @Param("roleId") Long roleId);

    @Select("SELECT COUNT(1) FROM sys_user_role WHERE role_id = #{roleId}")
    long countByRoleId(Long roleId);

    @Select("""
            <script>
            SELECT role_id AS roleId, COUNT(1) AS userCount
            FROM sys_user_role
            WHERE role_id IN
            <foreach collection='roleIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>
            GROUP BY role_id
            </script>
            """)
    List<RoleUserCount> countByRoleIds(@Param("roleIds") List<Long> roleIds);
}
