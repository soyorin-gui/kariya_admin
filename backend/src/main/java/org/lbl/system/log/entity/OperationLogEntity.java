package org.lbl.system.log.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import org.lbl.system.log.support.LogResult;
import org.lbl.system.log.aspect.OperationLog;

import java.time.LocalDateTime;

/**
 * 操作日志，对应 sys_operation_log。同样不设逻辑删除，只做物理删除。
 */
@Data
@TableName("sys_operation_log")
public class OperationLogEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String username;
    /** 业务模块，取自 {@link OperationLog#module()}。 */
    private String module;
    /** 具体动作，取自 {@link OperationLog#action()}。 */
    private String action;
    private String requestIp;
    /** SUCCESS / FAILURE，取值见 {@link LogResult}。 */
    private String result;
    /** 方法执行耗时（毫秒），用于发现慢操作。 */
    private Long durationMs;
    private LocalDateTime createdTime;
}
