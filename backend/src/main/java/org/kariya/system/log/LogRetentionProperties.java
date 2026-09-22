package org.kariya.system.log;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 审计日志保留策略。
 * <p>
 * 每个字段都给了 {@link DefaultValue}：万一配置缺失或写错了 key，也不会退化成
 * "保留 0 天"从而把整张日志表清空——这类"配置事故导致数据被删"的代价远高于几个默认值。
 * 真正的清理逻辑见 {@link LogRetentionJob}。
 *
 * @param enabled       是否启用定期清理；关闭后日志只增不删，需要自行安排归档
 * @param loginDays     登录日志保留天数
 * @param operationDays 操作日志保留天数（写操作比登录更值得长期留痕，默认比登录日志长）
 * @param batchSize     每批删除行数；分批是为了避免一条 DELETE 长时间持锁、撑大 undo log
 * @param maxBatches    单次任务最多执行多少批，用于给极端情况兜一个上限
 * @param cron          清理任务的 cron 表达式，由 LogRetentionJob 上的 @Scheduled 读取
 */
@ConfigurationProperties(prefix = "kariya.log.retention")
public record LogRetentionProperties(boolean enabled,
                                     @DefaultValue("180") int loginDays,
                                     @DefaultValue("365") int operationDays,
                                     @DefaultValue("2000") int batchSize,
                                     @DefaultValue("50") int maxBatches,
                                     @DefaultValue("0 30 3 * * *") String cron) {
}
