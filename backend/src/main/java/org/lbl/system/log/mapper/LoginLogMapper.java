package org.lbl.system.log.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.lbl.system.log.analysis.model.LoginAuditTotals;
import org.lbl.system.log.analysis.model.LoginUserStatistics;
import org.lbl.system.log.entity.LoginLogEntity;
import org.lbl.system.log.risk.model.LoginRiskEvent;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface LoginLogMapper extends BaseMapper<LoginLogEntity> {
    String USER_STATISTICS = """
            SELECT user_id AS userId, username,
                   COUNT(*) AS totalAttempts,
                   SUM(CASE WHEN result = 'SUCCESS' THEN 1 ELSE 0 END) AS successCount,
                   SUM(CASE WHEN result = 'FAILURE' THEN 1 ELSE 0 END) AS failureCount,
                   SUM(CASE WHEN result = 'LOCKED' THEN 1 ELSE 0 END) AS lockedCount,
                   COUNT(DISTINCT login_ip) AS distinctIpCount,
                   MIN(login_time) AS firstLoginTime,
                   MAX(login_time) AS lastLoginTime
              FROM sys_login_log
             WHERE login_time >= #{beginTime} AND login_time < #{endTime}
             GROUP BY user_id, username
            """;

    @Select("""
            SELECT COUNT(*) AS totalAttempts,
                   SUM(CASE WHEN result = 'SUCCESS' THEN 1 ELSE 0 END) AS successCount,
                   SUM(CASE WHEN result = 'FAILURE' THEN 1 ELSE 0 END) AS failureCount,
                   SUM(CASE WHEN result = 'LOCKED' THEN 1 ELSE 0 END) AS lockedCount,
                   COUNT(DISTINCT username) AS distinctAccountCount,
                   COUNT(DISTINCT login_ip) AS distinctIpCount,
                   MIN(login_time) AS firstLoginTime,
                   MAX(login_time) AS lastLoginTime
              FROM sys_login_log
             WHERE login_time >= #{beginTime} AND login_time < #{endTime}
            """)
    LoginAuditTotals summarize(@Param("beginTime") LocalDateTime beginTime,
                               @Param("endTime") LocalDateTime endTime);

    @Select(USER_STATISTICS + " ORDER BY totalAttempts DESC, lastLoginTime DESC LIMIT #{limit}")
    List<LoginUserStatistics> topByAttempts(@Param("beginTime") LocalDateTime beginTime,
                                            @Param("endTime") LocalDateTime endTime,
                                            @Param("limit") int limit);

    @Select(USER_STATISTICS + " HAVING successCount > 0 ORDER BY successCount DESC, totalAttempts DESC, lastLoginTime DESC LIMIT #{limit}")
    List<LoginUserStatistics> topBySuccesses(@Param("beginTime") LocalDateTime beginTime,
                                             @Param("endTime") LocalDateTime endTime,
                                             @Param("limit") int limit);

    @Select(USER_STATISTICS + " HAVING (failureCount + lockedCount) > 0 ORDER BY (failureCount + lockedCount) DESC, totalAttempts DESC, lastLoginTime DESC LIMIT #{limit}")
    List<LoginUserStatistics> topByRejected(@Param("beginTime") LocalDateTime beginTime,
                                            @Param("endTime") LocalDateTime endTime,
                                            @Param("limit") int limit);

    /** 风险扫描只取规则需要的字段；limit 由 Service 传“最大值 + 1”用于检测数据是否超限。 */
    @Select("""
            SELECT id, user_id AS userId, username, login_ip AS loginIp, user_agent AS userAgent,
                   result, login_time AS loginTime
              FROM sys_login_log
             WHERE login_time >= #{beginTime} AND login_time < #{endTime}
             ORDER BY login_time, id
             LIMIT #{limit}
            """)
    List<LoginRiskEvent> findRiskEvents(@Param("beginTime") LocalDateTime beginTime,
                                        @Param("endTime") LocalDateTime endTime,
                                        @Param("limit") int limit);

    /** Agent 用户下钻：精确匹配 userId（优先）或用户名，并限制返回量。 */
    @Select("""
            <script>
            SELECT id, user_id, username, login_ip, result, message, login_time
              FROM sys_login_log
             WHERE login_time &gt;= #{beginTime} AND login_time &lt; #{endTime}
               AND
               <choose>
                 <when test="userId != null">user_id = #{userId}</when>
                 <otherwise>username = #{username}</otherwise>
               </choose>
             ORDER BY login_time DESC, id DESC
             LIMIT #{limit}
            </script>
            """)
    List<LoginLogEntity> findUserTimeline(@Param("userId") Long userId,
                                          @Param("username") String username,
                                          @Param("beginTime") LocalDateTime beginTime,
                                          @Param("endTime") LocalDateTime endTime,
                                          @Param("limit") int limit);

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
