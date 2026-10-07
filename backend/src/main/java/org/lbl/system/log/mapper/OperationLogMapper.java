package org.lbl.system.log.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.lbl.system.log.analysis.model.OperationActionStatistics;
import org.lbl.system.log.analysis.model.OperationAuditTotals;
import org.lbl.system.log.analysis.model.OperationUserStatistics;
import org.lbl.system.log.entity.OperationLogEntity;
import org.lbl.system.log.risk.model.OperationRiskEvent;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLogEntity> {
    String USER_STATISTICS = """
            SELECT user_id AS userId, COALESCE(username, '-') AS username,
                   COUNT(*) AS totalOperations,
                   SUM(CASE WHEN result = 'SUCCESS' THEN 1 ELSE 0 END) AS successCount,
                   SUM(CASE WHEN result = 'FAILURE' THEN 1 ELSE 0 END) AS failureCount,
                   COUNT(DISTINCT module, action) AS distinctActionCount,
                   MIN(created_time) AS firstOperationTime,
                   MAX(created_time) AS lastOperationTime
              FROM sys_operation_log
             WHERE created_time >= #{beginTime} AND created_time < #{endTime}
             GROUP BY user_id, username
            """;

    @Select("""
            SELECT COUNT(*) AS totalOperations,
                   SUM(CASE WHEN result = 'SUCCESS' THEN 1 ELSE 0 END) AS successCount,
                   SUM(CASE WHEN result = 'FAILURE' THEN 1 ELSE 0 END) AS failureCount,
                   COUNT(DISTINCT COALESCE(username, '-')) AS distinctOperatorCount,
                   COUNT(DISTINCT module, action) AS distinctActionCount,
                   MIN(created_time) AS firstOperationTime,
                   MAX(created_time) AS lastOperationTime
              FROM sys_operation_log
             WHERE created_time >= #{beginTime} AND created_time < #{endTime}
            """)
    OperationAuditTotals summarize(@Param("beginTime") LocalDateTime beginTime,
                                   @Param("endTime") LocalDateTime endTime);

    @Select(USER_STATISTICS + " ORDER BY totalOperations DESC, lastOperationTime DESC LIMIT #{limit}")
    List<OperationUserStatistics> topByOperations(@Param("beginTime") LocalDateTime beginTime,
                                                  @Param("endTime") LocalDateTime endTime,
                                                  @Param("limit") int limit);

    @Select(USER_STATISTICS + " HAVING failureCount > 0 ORDER BY failureCount DESC, totalOperations DESC, lastOperationTime DESC LIMIT #{limit}")
    List<OperationUserStatistics> topByFailures(@Param("beginTime") LocalDateTime beginTime,
                                                @Param("endTime") LocalDateTime endTime,
                                                @Param("limit") int limit);

    @Select("""
            SELECT module, action,
                   COUNT(*) AS totalOperations,
                   SUM(CASE WHEN result = 'SUCCESS' THEN 1 ELSE 0 END) AS successCount,
                   SUM(CASE WHEN result = 'FAILURE' THEN 1 ELSE 0 END) AS failureCount,
                   COUNT(DISTINCT COALESCE(username, '-')) AS distinctOperatorCount
              FROM sys_operation_log
             WHERE created_time >= #{beginTime} AND created_time < #{endTime}
             GROUP BY module, action
             ORDER BY totalOperations DESC, module, action
             LIMIT #{limit}
            """)
    List<OperationActionStatistics> topActions(@Param("beginTime") LocalDateTime beginTime,
                                                @Param("endTime") LocalDateTime endTime,
                                                @Param("limit") int limit);

    /** 风险扫描使用的精简、按时间排序事件流。 */
    @Select("""
            SELECT id, user_id AS userId, username, module, action,
                   target_type AS targetType, target_id AS targetId, result,
                   created_time AS createdTime
              FROM sys_operation_log
             WHERE created_time >= #{beginTime} AND created_time < #{endTime}
             ORDER BY created_time, id
             LIMIT #{limit}
            """)
    List<OperationRiskEvent> findRiskEvents(@Param("beginTime") LocalDateTime beginTime,
                                            @Param("endTime") LocalDateTime endTime,
                                            @Param("limit") int limit);

    /** Agent 用户下钻：只返回解释操作行为所需字段。 */
    @Select("""
            <script>
            SELECT id, user_id, username, module, action, target_type, target_id, target_name,
                   request_id, result, duration_ms, created_time
              FROM sys_operation_log
             WHERE created_time &gt;= #{beginTime} AND created_time &lt; #{endTime}
               AND
               <choose>
                 <when test="userId != null">user_id = #{userId}</when>
                 <otherwise>username = #{username}</otherwise>
               </choose>
             ORDER BY created_time DESC, id DESC
             LIMIT #{limit}
            </script>
            """)
    List<OperationLogEntity> findUserTimeline(@Param("userId") Long userId,
                                              @Param("username") String username,
                                              @Param("beginTime") LocalDateTime beginTime,
                                              @Param("endTime") LocalDateTime endTime,
                                              @Param("limit") int limit);

    /** 分批物理删除过期操作日志，说明同 {@link LoginLogMapper#deleteBefore}。 */
    @Delete("DELETE FROM sys_operation_log WHERE created_time < #{before} ORDER BY created_time LIMIT #{limit}")
    int deleteBefore(@Param("before") LocalDateTime before, @Param("limit") int limit);
}
