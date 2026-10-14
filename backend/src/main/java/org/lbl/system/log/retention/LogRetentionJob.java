package org.lbl.system.log.retention;

import org.lbl.system.log.mapper.LoginLogMapper;
import org.lbl.system.log.mapper.OperationLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 审计日志保留策略：定期分批物理删除过期日志，防止 sys_login_log / sys_operation_log 无限膨胀。
 * <p>
 * 为什么必须要有这个任务：日志表的增长速度不由业务量决定，而由"记了多少种操作 × 被调用多少次数"决定。
 * 一个 50 人的内部系统，如果连查询都记，一天轻松上万行，一年就是几百万行、几个 GB——
 * 而这些数据 95% 从来没人查过，却会让备份变慢、让"翻某天的日志"变成全表扫描。
 * <p>
 * 设计取舍：
 * <ul>
 *   <li><b>分批删除</b>：每批 {@code batchSize} 行，批间 sleep 一下。一次性 DELETE 几百万行会长时间持锁、
 *       撑大 undo log，还可能阻塞线上写入。</li>
 *   <li><b>不加 @Transactional</b>：每批各自提交。若整个清理过程包在一个事务里，就退化成了"一次性大删除"，
 *       并且中途失败会全部回滚、白删一遍。</li>
 *   <li><b>有上限</b>：{@code maxBatches} 限制单次任务的删除量，避免第一次上线时把库跑满。
 *       到上限会打 WARN，剩余部分留给下一次。</li>
 *   <li><b>低峰执行</b>：默认 03:30，可通过 lbl.log.retention.cron 调整。</li>
 * </ul>
 */
@Component
public class LogRetentionJob {
    private static final Logger log = LoggerFactory.getLogger(LogRetentionJob.class);
    private static final long BATCH_PAUSE_MILLIS = 200;

    private final LogRetentionProperties properties;
    private final LoginLogMapper loginLogs;
    private final OperationLogMapper operationLogs;

    public LogRetentionJob(LogRetentionProperties properties, LoginLogMapper loginLogs, OperationLogMapper operationLogs) {
        this.properties = properties;
        this.loginLogs = loginLogs;
        this.operationLogs = operationLogs;
    }

    @Scheduled(cron = "${lbl.log.retention.cron:0 30 3 * * *}")
    public void purge() {
        if (!properties.enabled()) {
            log.info("Audit log retention is disabled, nothing purged");
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        int loginRemoved = purge(now.minusDays(keepDays(properties.loginDays())), loginLogs::deleteBefore);
        int operationRemoved = purge(now.minusDays(keepDays(properties.operationDays())), operationLogs::deleteBefore);
        log.info("Audit log retention finished: login removed={} (keep {}d), operation removed={} (keep {}d)",
                loginRemoved, properties.loginDays(), operationRemoved, properties.operationDays());
    }

    /** 至少保留 1 天：配置写错成 0 或负数时，绝不允许退化成"清空整张表"。 */
    private int keepDays(int configured) {
        if (configured < 1) {
            log.warn("Invalid retention days {}, fallback to 1 to avoid wiping the whole table", configured);
            return 1;
        }
        return configured;
    }

    private int purge(LocalDateTime before, BatchDelete delete) {
        int total = 0;
        for (int round = 0; round < properties.maxBatches(); round++) {
            int affected;
            try {
                affected = delete.deleteBefore(before, properties.batchSize());
            } catch (Exception ex) {
                log.error("Audit log purge aborted on round {} (before={})", round + 1, before, ex);
                break;
            }
            total += affected;
            if (affected < properties.batchSize()) return total;
            if (!pauseQuietly()) break;
        }
        log.warn("Audit log purge reached maxBatches={} (before={}), remaining rows will be handled on the next run", properties.maxBatches(), before);
        return total;
    }

    private boolean pauseQuietly() {
        try {
            Thread.sleep(BATCH_PAUSE_MILLIS);
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @FunctionalInterface
    private interface BatchDelete {
        int deleteBefore(LocalDateTime before, int limit);
    }
}
