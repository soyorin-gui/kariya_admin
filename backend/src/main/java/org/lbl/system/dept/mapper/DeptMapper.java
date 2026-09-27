package org.lbl.system.dept.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.lbl.system.dept.entity.DeptEntity;

@Mapper
public interface DeptMapper extends BaseMapper<DeptEntity> {
    /**
     * 统计占用该部门编码的记录数，<b>包含已被逻辑删除的记录</b>。
     * <p>
     * 口径必须与 {@code sys_dept.dept_code} 上的唯一索引一致：逻辑删除只把 {@code deleted} 置 1，
     * 行与索引项都还在表里，所以"删掉一个部门再用同一个编码建一个"必然撞唯一键。
     * 而 MyBatis-Plus 的 {@code selectCount} 会自动追加 {@code deleted = 0}，
     * 用它做预检就会出现"校验通过、插入失败"——用户看到的是无法理解的失败。
     * 与 {@code UserMapper#countIncludingDeletedByUsername} 是同一约定，原因也相同。
     *
     * @param excludeId 编辑场景下需排除的自身 id；新增时传 {@code 0}
     *                  （id 是自增正数，从 1 开始，不会命中 0）
     */
    @Select("""
            SELECT COUNT(1) FROM sys_dept
            WHERE dept_code = #{deptCode} AND id != #{excludeId}
            """)
    long countIncludingDeletedByDeptCode(@Param("deptCode") String deptCode, @Param("excludeId") long excludeId);
}
