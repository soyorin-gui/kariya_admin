package org.lbl.system.log.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.lbl.common.exception.BusinessException;
import org.lbl.common.result.PageResult;
import org.lbl.system.log.support.RequestInfo;
import org.lbl.system.log.entity.LoginLogEntity;
import org.lbl.system.log.mapper.LoginLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class LoginLogService {
    private static final Logger log = LoggerFactory.getLogger(LoginLogService.class);
    private static final int MAX_DELETE_BATCH = 1000;
    private static final int USERNAME_MAX_LENGTH = 64;
    private static final int MESSAGE_MAX_LENGTH = 255;

    private final LoginLogMapper mapper;

    public LoginLogService(LoginLogMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 写入一条登录日志（成功、失败、锁定都记）。
     * <p>
     * 两个关键点：
     * <ol>
     *   <li>{@code REQUIRES_NEW}：登录失败时 AuthService.login 是抛异常回滚的，如果日志和业务共用一个事务，
     *       失败记录会被一起回滚掉——而"谁在什么时候尝试登录失败了"恰恰是登录日志最该留下的部分。</li>
     *   <li>内部吞掉异常：审计写入失败绝不能反过来让用户登不进来。</li>
     * </ol>
     * 注意：因为记录每次失败尝试，攻击者持续用错误密码刷接口会推高这张表的增长速度，
     * 这也是必须配保留策略（LogRetentionJob）的原因之一。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String username, Long userId, String result, String message) {
        try {
            LoginLogEntity entry = new LoginLogEntity();
            entry.setUsername(StringUtils.hasText(username) ? truncate(username, USERNAME_MAX_LENGTH) : "-");
            entry.setUserId(userId);
            entry.setLoginIp(RequestInfo.clientIp());
            entry.setUserAgent(RequestInfo.userAgent());
            entry.setResult(result);
            entry.setMessage(truncate(message, MESSAGE_MAX_LENGTH));
            entry.setLoginTime(LocalDateTime.now());
            mapper.insert(entry);
        } catch (Exception ex) {
            log.warn("Failed to persist login log for user '{}'", username, ex);
        }
    }

    public PageResult<LoginLogEntity> page(long pageNum, long pageSize, String username, String result,
                                          LocalDateTime beginTime, LocalDateTime endTime) {
        if (pageNum < 1 || pageSize < 1 || pageSize > 100) throw new BusinessException("分页参数无效，每页最多 100 条");
        LambdaQueryWrapper<LoginLogEntity> query = new LambdaQueryWrapper<LoginLogEntity>()
                .like(StringUtils.hasText(username), LoginLogEntity::getUsername, username)
                .eq(StringUtils.hasText(result), LoginLogEntity::getResult, result)
                .ge(beginTime != null, LoginLogEntity::getLoginTime, beginTime)
                .le(endTime != null, LoginLogEntity::getLoginTime, endTime)
                .orderByDesc(LoginLogEntity::getLoginTime)
                .orderByDesc(LoginLogEntity::getId);
        Page<LoginLogEntity> page = mapper.selectPage(Page.of(pageNum, pageSize), query);
        return new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    public int remove(List<Long> ids) {
        List<Long> targets = ids == null ? List.of() : ids.stream().filter(Objects::nonNull).distinct().toList();
        if (targets.isEmpty()) throw new BusinessException("请选择要删除的日志");
        if (targets.size() > MAX_DELETE_BATCH) throw new BusinessException("单次最多删除 " + MAX_DELETE_BATCH + " 条日志");
        return mapper.deleteByIds(targets);
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
