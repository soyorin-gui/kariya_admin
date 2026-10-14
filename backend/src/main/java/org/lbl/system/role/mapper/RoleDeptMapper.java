package org.lbl.system.role.mapper;

import org.apache.ibatis.annotations.*;
import org.lbl.system.role.vo.RoleDeptAssignment;

import java.util.List;

@Mapper
public interface RoleDeptMapper {
    @Select("SELECT dept_id FROM sys_role_dept WHERE role_id = #{roleId} ORDER BY dept_id")
    List<Long> selectDeptIds(Long roleId);

    @Select("""
            <script>
            SELECT rd.role_id AS roleId, rd.dept_id AS deptId
            FROM sys_role_dept rd JOIN sys_dept d ON d.id = rd.dept_id AND d.deleted = 0
            WHERE rd.role_id IN
            <foreach collection='roleIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>
            </script>
            """)
    List<RoleDeptAssignment> selectByRoleIds(@Param("roleIds") List<Long> roleIds);

    @Delete("DELETE FROM sys_role_dept WHERE role_id = #{roleId}")
    void deleteByRoleId(Long roleId);

    @Select("SELECT COUNT(1) FROM sys_role_dept WHERE dept_id = #{deptId}")
    long countByDeptId(Long deptId);

    @Insert("INSERT INTO sys_role_dept (role_id, dept_id) VALUES (#{roleId}, #{deptId})")
    void insert(@Param("roleId") Long roleId, @Param("deptId") Long deptId);
}
