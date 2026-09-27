package org.lbl.system.role.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.lbl.system.role.entity.RoleEntity;
import org.lbl.system.user.vo.UserRoleAssignment;

import java.util.List;

@Mapper
public interface RoleMapper extends BaseMapper<RoleEntity> {
    @Select("""
            SELECT r.* FROM sys_role r
            INNER JOIN sys_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId} AND r.deleted = 0 AND r.status = 1
            ORDER BY r.id
            """)
    List<RoleEntity> selectByUserId(Long userId);

    @Select("""
            SELECT r.* FROM sys_role r
            INNER JOIN sys_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId} AND r.deleted = 0
            ORDER BY r.id
            """)
    List<RoleEntity> selectAssignedByUserId(Long userId);

    @Select("""
            <script>
            SELECT ur.user_id AS userId, r.id AS roleId, r.role_name AS roleName,
                   r.role_code AS roleCode, r.data_scope AS dataScope, r.status, r.builtin
            FROM sys_user_role ur
            INNER JOIN sys_role r ON r.id = ur.role_id
            WHERE r.deleted = 0 AND ur.user_id IN
            <foreach collection='userIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>
            ORDER BY ur.user_id, r.id
            </script>
            """)
    List<UserRoleAssignment> selectAssignedByUserIds(@Param("userIds") List<Long> userIds);

    /**
     * 统计占用该角色标识的记录数，<b>包含已被逻辑删除的记录</b>。
     * <p>
     * 口径必须与 {@code sys_role.role_code} 上的唯一索引一致：逻辑删除只把 {@code deleted} 置 1，
     * 行与索引项都还在，删掉一个角色再用同一个 role_code 建一个必然撞唯一键；
     * 而 {@code selectCount} 会自动附加 {@code deleted = 0}，导致"预检通过、插入失败"。
     * 与 {@code UserMapper#countIncludingDeletedByUsername} 同一约定。
     *
     * @param excludeId 编辑场景下需排除的自身 id；新增时传 {@code 0}（id 自增从 1 开始，不会命中）
     */
    @Select("""
            SELECT COUNT(1) FROM sys_role
            WHERE role_code = #{roleCode} AND id != #{excludeId}
            """)
    long countIncludingDeletedByRoleCode(@Param("roleCode") String roleCode, @Param("excludeId") long excludeId);
}
