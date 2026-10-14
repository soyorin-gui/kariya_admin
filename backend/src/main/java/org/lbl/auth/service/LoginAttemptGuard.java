package org.lbl.auth.service;

import org.lbl.common.exception.TooManyRequestsException;
import org.lbl.system.log.support.RequestInfo;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;

/**
 * 认证类入口的滥用控制，全项目唯一的实现处。
 * <p>
 * 这里集中两类防护，它们共享同一个「来源」口径（{@link #currentSource()}），因此放在一起：
 * <ol>
 *   <li><b>密码校验的失败计数与锁定</b>（{@link #isLocked}/{@link #recordFailure}/{@link #recordSuccess}）：
 *       不只是"登录"要计数。任何"用会话身份 + 密码去校验一个已有账号"的入口都必须走同一套计数，
 *       否则那条入口就是一条绕过锁定器的密码爆破通道 —— 这是它从 {@code AuthService} 里抽出来的原因。</li>
 *   <li><b>来源级固定窗口配额</b>（{@link #requireSourceQuota}）：用于未认证、且每次调用都会产生
 *       副作用（写 Redis / 写库 / 发邮件）的入口。</li>
 * </ol>
 *
 * <h2>为什么锁定分两层</h2>
 * 只按账号锁定会让攻击者轻易锁死已知员工账号，因此同时维护「账号 + 来源」的严格限制，
 * 以及来源级的宽松限制（用于防范"每个账号只试几次"的密码喷洒）。
 *
 * <h2>来源口径</h2>
 * {@link #currentSource()} 用的是 {@link RequestInfo#rateLimitIp()}，与审计日志同源：
 * 只有请求确实来自可信代理时才会采信转发头（见 {@code TrustedProxyResolver}），
 * 因此这个值不可被调用方伪造。Redis key 里不保存 IP 原值，只存它的 SHA-256。
 */
@Component
public class LoginAttemptGuard {
    /** 单个「账号 + 来源」组合允许的连续失败次数，达到即锁定该组合。 */
    private static final long ACCOUNT_SOURCE_FAILURE_LIMIT = 5;
    /** 单个来源允许的失败次数，达到即锁定整个来源。 */
    private static final long SOURCE_FAILURE_LIMIT = 20;
    /** 失败计数的统计窗口，"15 分钟内失败 N 次"里的那个 15 分钟。 */
    private static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);
    /** 触发上限后的锁定时长。 */
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    /** 限流 key 里保存的账号名上限，与 sys_user.username 的 VARCHAR(64) 对齐。 */
    private static final int LOGIN_KEY_MAX_LENGTH = 64;

    private final StringRedisTemplate redis;

    public LoginAttemptGuard(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 归一化账号标识，用作限流 key 的一部分。
     * <p>
     * 必须忽略大小写：MySQL 的用户名比较在默认排序规则下是不区分大小写的，
     * 若限流 key 区分大小写，攻击者只要交替使用 {@code Admin}/{@code admin} 就能把
     * 计数拆成两份，账号级锁定永远攒不满。
     * <p>
     * 同时截断到与数据库列同长：这个值来自请求体，不设上限就能用超长用户名撑大 Redis key。
     */
    public String loginKey(String username) {
        if (username == null) return "";
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        return normalized.length() <= LOGIN_KEY_MAX_LENGTH ? normalized : normalized.substring(0, LOGIN_KEY_MAX_LENGTH);
    }

    /** 当前请求的来源标识（已哈希）。 */
    public String currentSource() {
        return hash(RequestInfo.rateLimitIp());
    }

    /** 该「账号 + 来源」组合当前是否处于锁定状态（账号级锁定或来源级锁定任一命中）。 */
    public boolean isLocked(String loginKey, String source) {
        return Boolean.TRUE.equals(redis.hasKey(sourceLockKey(source)))
                || Boolean.TRUE.equals(redis.hasKey(accountSourceLockKey(loginKey, source)));
    }

    /**
     * 记一次密码校验失败。
     *
     * @return {@code true} 表示本次失败已触发（或此前已处于）锁定状态，调用方应据此拒绝请求并记 LOCKED 日志
     */
    public boolean recordFailure(String loginKey, String source) {
        Long accountCount = incrementInWindow(accountSourceFailKey(loginKey, source));
        Long sourceCount = incrementInWindow(sourceFailKey(source));
        boolean accountLocked = accountCount != null && accountCount >= ACCOUNT_SOURCE_FAILURE_LIMIT;
        boolean sourceLocked = sourceCount != null && sourceCount >= SOURCE_FAILURE_LIMIT;
        if (accountLocked) {
            redis.opsForValue().set(accountSourceLockKey(loginKey, source), "1", LOCK_DURATION);
            redis.delete(accountSourceFailKey(loginKey, source));
        }
        if (sourceLocked) {
            redis.opsForValue().set(sourceLockKey(source), "1", LOCK_DURATION);
            redis.delete(sourceFailKey(source));
        }
        return accountLocked || sourceLocked;
    }

    /**
     * 密码校验成功后清空计数。
     * <p>
     * 来源级计数也要清：办公网/出口 NAT 下所有人共享一个来源，若某个同事反复输错密码，
     * 计数攒满 20 次就会把整个出口 IP 锁 15 分钟。校验成功说明这个来源此刻是正常的，
     * 应当放行；账号级计数也在这里一并清除，不会因此削弱对目标账号的保护。
     */
    public void recordSuccess(String loginKey, String source) {
        redis.delete(accountSourceFailKey(loginKey, source));
        redis.delete(sourceFailKey(source));
    }

    /**
     * 来源级固定窗口配额：同一来源在 {@code window} 内最多放行 {@code limit} 次。
     * <p>
     * 这是"单来源脚本刷接口"的兜底，<b>不是</b>针对分布式来源的防护 ——
     * 攻击者手握大量 IP 时仍需在网关/WAF 层解决。
     *
     * @param bucket 业务分组名，不同入口必须用不同的 bucket，否则会互相占用配额
     */
    public void requireSourceQuota(String bucket, int limit, Duration window) {
        String key = quotaKey(bucket, currentSource());
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) redis.expire(key, window);
        if (count != null && count > limit) {
            throw new TooManyRequestsException("操作过于频繁，请稍后再试");
        }
    }

    private Long incrementInWindow(String key) {
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) redis.expire(key, FAILURE_WINDOW);
        return count;
    }

    /** 限流 key 名中不直接保存可识别身份的 IP 原值。 */
    private String hash(String value) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    // key 前缀沿用原 AuthService 里既有的 auth:login:* 命名空间：
    // 改名不会带来任何收益，却会让上线瞬间所有在途计数"凭空归零"，
    // 增加排查限流问题时的不确定性。查询键时按这个前缀找即可。
    private String accountSourceFailKey(String loginKey, String source) {
        return "auth:login:fail:account-source:" + loginKey + ":" + source;
    }

    private String accountSourceLockKey(String loginKey, String source) {
        return "auth:login:lock:account-source:" + loginKey + ":" + source;
    }

    private String sourceFailKey(String source) {
        return "auth:login:fail:source:" + source;
    }

    private String sourceLockKey(String source) {
        return "auth:login:lock:source:" + source;
    }

    private String quotaKey(String bucket, String source) {
        return "auth:quota:" + bucket + ":" + source;
    }
}
