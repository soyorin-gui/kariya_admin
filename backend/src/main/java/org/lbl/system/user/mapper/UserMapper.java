package org.lbl.system.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.lbl.system.user.entity.UserEntity;
import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {
    /**
     * 统计同名用户，<b>包含已被逻辑删除的记录</b>。
     * <p>
     * 为什么必须包含已删除的：sys_user.username 上是物理唯一索引，逻辑删除只是把 deleted 置 1，
     * 行仍然在表里、索引项也仍然在。所以"删掉用户后再建一个同名用户"必然在 INSERT 时撞唯一键；
     * 而 MyBatis-Plus 的 selectCount 会自动追加 deleted = 0，查不到这些行，于是业务校验通过、插入却失败。
     * <p>
     * 校验口径必须和唯一索引的口径一致，否则要么给出错误提示，要么直接 500。
     */
    @Select("SELECT COUNT(1) FROM sys_user WHERE username = #{username}")
    long countIncludingDeletedByUsername(@Param("username") String username);

    /** 串行化同一账号的登录方式变更，避免两个并发解绑请求同时通过“仍有另一种方式”的检查。 */
    @Select("SELECT * FROM sys_user WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    UserEntity selectByIdForUpdate(@Param("id") Long id);

    @Select("""
            SELECT DISTINCT u.id FROM sys_user u
            JOIN sys_user_role ur ON ur.user_id=u.id
            JOIN sys_role r ON r.id=ur.role_id
            WHERE r.role_code='super_admin' AND r.status=1 AND r.deleted=0
              AND u.status=1 AND u.deleted=0
            """)
    List<Long> selectActiveSuperAdminIds();
}
