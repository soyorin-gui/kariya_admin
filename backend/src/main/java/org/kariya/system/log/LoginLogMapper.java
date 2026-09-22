package org.kariya.system.log;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface LoginLogMapper extends BaseMapper<LoginLogEntity> {
    /**
     * 分批物理删除过期登录日志。
     * <p>
     * ORDER BY + LIMIT 让删除沿 login_time 索引推进，而不是一次 DELETE 扫全表：后者会长时间持有锁、
     * 撑大 undo log，在日志表很大时还可能拖慢甚至阻塞线上写入。
     * 本方法刻意不加 {@code @Transactional}，交由调用方按批提交（见 LogRetentionJob）。
     */
    @Delete("DELETE FROM sys_login_log WHERE login_time < #{before} ORDER BY login_time LIMIT #{limit}")
    int deleteBefore(@Param("before") LocalDateTime before, @Param("limit") int limit);
}
