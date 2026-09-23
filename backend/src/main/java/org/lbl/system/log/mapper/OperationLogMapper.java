package org.lbl.system.log.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.lbl.system.log.entity.OperationLogEntity;

import java.time.LocalDateTime;

@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLogEntity> {
    /** 分批物理删除过期操作日志，说明同 {@link LoginLogMapper#deleteBefore}。 */
    @Delete("DELETE FROM sys_operation_log WHERE created_time < #{before} ORDER BY created_time LIMIT #{limit}")
    int deleteBefore(@Param("before") LocalDateTime before, @Param("limit") int limit);
}
