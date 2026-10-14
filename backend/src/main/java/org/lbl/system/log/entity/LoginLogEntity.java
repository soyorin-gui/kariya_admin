package org.lbl.system.log.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import org.lbl.system.log.support.LogResult;
import org.lbl.system.log.retention.LogRetentionJob;

import java.time.LocalDateTime;

/**
 * 登录日志，对应 sys_login_log。
 * <p>
 * 该表刻意没有 deleted 列：日志只做物理删除（保留策略见 {@link LogRetentionJob}）。
 * 如果给审计表加逻辑删除，"删掉的日志还留在表里"，表会只增不减，清理就失去意义了。
 */
@Data
@TableName("sys_login_log")
public class LoginLogEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private Long userId;
    private String loginIp;
    private String userAgent;
    /** SUCCESS / FAILURE / LOCKED，取值见 {@link LogResult}。 */
    private String result;
    /** 结果说明，例如"用户名或密码错误""连续失败次数过多，账户已锁定 15 分钟"。 */
    private String message;
    private LocalDateTime loginTime;
}
