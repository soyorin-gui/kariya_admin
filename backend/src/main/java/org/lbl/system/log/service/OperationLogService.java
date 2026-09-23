package org.lbl.system.log.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.lbl.common.exception.BusinessException;
import org.lbl.common.result.PageResult;
import org.lbl.security.context.CurrentUser;
import org.lbl.system.log.aspect.OperationLogAspect;
import org.lbl.system.log.support.RequestInfo;
import org.lbl.system.log.entity.OperationLogEntity;
import org.lbl.system.log.mapper.OperationLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class OperationLogService {
    private static final Logger log = LoggerFactory.getLogger(OperationLogService.class);
    private static final int MAX_DELETE_BATCH = 1000;
    private static final int MODULE_MAX_LENGTH = 80;
    private static final int ACTION_MAX_LENGTH = 80;

    private final OperationLogMapper mapper;

    public OperationLogService(OperationLogMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 写入一条操作日志。由 {@link OperationLogAspect} 调用。
     * <p>
     * {@code REQUIRES_NEW} 的原因：业务操作失败时外层事务会回滚，审计记录必须独立提交，
     * 否则"失败的操作"永远留不下痕迹，而这恰恰是排查问题时最需要的那部分。
     * 方法内部吞掉所有异常——审计失败不能让用户的操作跟着失败。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String module, String action, String result, long durationMs) {
        try {
            OperationLogEntity entry = new OperationLogEntity();
            fillOperator(entry);
            entry.setModule(truncate(module, MODULE_MAX_LENGTH));
            entry.setAction(truncate(action, ACTION_MAX_LENGTH));
            entry.setRequestIp(RequestInfo.clientIp());
            entry.setResult(result);
            entry.setDurationMs(durationMs);
            entry.setCreatedTime(LocalDateTime.now());
            mapper.insert(entry);
        } catch (Exception ex) {
            log.warn("Failed to persist operation log: {} / {}", module, action, ex);
        }
    }

    public PageResult<OperationLogEntity> page(long pageNum, long pageSize, String keyword, String module, String result,
                                              LocalDateTime beginTime, LocalDateTime endTime) {
        if (pageNum < 1 || pageSize < 1 || pageSize > 100) throw new BusinessException("分页参数无效，每页最多 100 条");
        LambdaQueryWrapper<OperationLogEntity> query = new LambdaQueryWrapper<OperationLogEntity>()
                .and(StringUtils.hasText(keyword), condition -> condition
                        .like(OperationLogEntity::getUsername, keyword)
                        .or().like(OperationLogEntity::getAction, keyword))
                .eq(StringUtils.hasText(module), OperationLogEntity::getModule, module)
                .eq(StringUtils.hasText(result), OperationLogEntity::getResult, result)
                .ge(beginTime != null, OperationLogEntity::getCreatedTime, beginTime)
                .le(endTime != null, OperationLogEntity::getCreatedTime, endTime)
                .orderByDesc(OperationLogEntity::getCreatedTime)
                .orderByDesc(OperationLogEntity::getId);
        Page<OperationLogEntity> page = mapper.selectPage(Page.of(pageNum, pageSize), query);
        return new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    public int remove(List<Long> ids) {
        List<Long> targets = ids == null ? List.of() : ids.stream().filter(Objects::nonNull).distinct().toList();
        if (targets.isEmpty()) throw new BusinessException("请选择要删除的日志");
        if (targets.size() > MAX_DELETE_BATCH) throw new BusinessException("单次最多删除 " + MAX_DELETE_BATCH + " 条日志");
        return mapper.deleteByIds(targets);
    }

    /**
     * 从 SecurityContext 取操作人。principal 是 {@link CurrentUser} 时能直接拿到 id，
     * 省掉一次按用户名回查用户表的开销。
     */
    private void fillOperator(OperationLogEntity entry) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) return;
        if (authentication.getPrincipal() instanceof CurrentUser current) {
            entry.setUserId(current.id());
            entry.setUsername(current.username());
        } else {
            entry.setUsername(authentication.getName());
        }
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
