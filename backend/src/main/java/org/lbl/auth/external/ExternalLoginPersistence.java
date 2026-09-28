package org.lbl.auth.external;

import org.lbl.auth.identity.ExternalIdentityEntity;
import org.lbl.auth.identity.ExternalIdentityMapper;
import org.lbl.auth.identity.VerifiedIdentity;
import org.lbl.auth.model.SessionGrant;
import org.lbl.auth.session.LoginSession;
import org.lbl.auth.session.SessionService;
import org.lbl.common.exception.BusinessException;
import org.lbl.security.jwt.JwtService;
import org.lbl.system.user.entity.UserEntity;
import org.lbl.system.user.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 外部登录/绑定在<b>数据库里</b>要做的那部分：查绑定关系、落库、发会话、写审计日志。
 *
 * <h2>为什么单独一个类（而不是留在 ExternalLoginService 里）</h2>
 * 因为外部登录里有两次网络往返（换令牌、取用户资料），各自超时 15 秒，
 * 最坏能把一次请求拖到 30 秒以上。这些网络调用<b>绝不能</b>被包在一个数据库事务里：
 * 事务一旦开启就会占住一条 Hikari 连接（当前 {@code maximum-pool-size: 10}），
 * 几个并发的外部登录就能把连接池占满，让<b>全站所有请求</b>在 connection-timeout 之后集体失败。
 * <p>
 * 所以边界是：{@link ExternalLoginService} 负责协议与网络，本类负责落库，两者之间用
 * {@link VerifiedIdentity} 这个纯数据对象衔接。<b>本类刻意不持有 HttpClient</b> ——
 * 这不是巧合，而是为了让"事务里不许有网络 IO"这条约束变成结构上的事实：
 * 以后想在事务里加一次 HTTP 调用，得先想办法把 HttpClient 弄进来，而这一步足够显眼。
 *
 * <h2>为什么成功日志不写在这里</h2>
 * 审计写入是 {@code REQUIRES_NEW}，如果在本类的事务里写，一个请求就会同时占两条数据库连接。
 * 因此本类只负责"产出该记什么"（{@link ExternalLoginService.LoginAudit}），
 * 由 {@link ExternalLoginService#complete} 在事务提交之后真正落库。
 */
@Service
public class ExternalLoginPersistence {
    private final ExternalIdentityMapper identities;
    private final UserMapper users;
    private final SessionService sessions;
    private final JwtService jwt;

    public ExternalLoginPersistence(ExternalIdentityMapper identities, UserMapper users, SessionService sessions,
                                    JwtService jwt) {
        this.identities = identities;
        this.users = users;
        this.sessions = sessions;
        this.jwt = jwt;
    }

    /**
     * 把一次已经验证通过的外部身份落到数据库上，并签发会话。
     *
     * @param verified   网络阶段已经完成、身份已确认的结果
     * @param currentSid 发起本次流程的浏览器当前持有的会话 id，仅绑定流程会用到，可为 null
     */
    @Transactional
    public ExternalLoginService.Completion settle(String providerKey, LoginTransaction transaction,
                                                  VerifiedIdentity verified, String currentSid) {
        SessionGrant grant;
        ExternalLoginService.LoginAudit audit;

        if (transaction.targetUserId() != null) {
            // 绑定：把外部身份挂到一个已经在登录态的账号上
            UserEntity target = users.selectById(transaction.targetUserId());
            if (target == null || target.getStatus() != 1) throw new BusinessException("待绑定账号不存在或已停用");
            LoginSession initiating = currentSid == null ? null : sessions.find(currentSid);
            if (!java.util.Objects.equals(transaction.initiatingSid(), currentSid) || initiating == null
                    || initiating.onboarding() || !target.getId().equals(initiating.userId())
                    || target.getAuthVersion() == null || target.getAuthVersion() != initiating.authVersion()) {
                throw new BusinessException("发起绑定的原登录会话已失效或发生变化，请重新发起绑定");
            }
            bind(target.getId(), verified);
            grant = reuseOrIssue(target, currentSid, providerKey);
            audit = new ExternalLoginService.LoginAudit(target.getId(), target.getUsername(),
                    "绑定第三方登录成功（" + providerKey + "）");
        } else {
            ExternalIdentityEntity linked = identities.find(providerKey, verified.issuer(), verified.subject());
            if (linked == null) {
                // 未绑定的外部身份只能进入开户确认流程，不能访问主系统。
                // rememberMe 必须原样带过去：用户在第三方登录入口勾了"记住我"，
                // 走完开户确认（创建/绑定）后拿到的正式会话就该是记住我会话；
                // 此前这里丢了它，回调后只能按"没有勾选"处理（见 RegistrationService）。
                String sid = sessions.createOnboarding(providerKey, verified.issuer(), verified.subject(),
                        verified.displayName(), verified.email(), verified.employeeNo(), transaction.rememberMe());
                grant = new SessionGrant(sid, jwt.issue(sid, sessions.find(sid)), null, true);
                audit = new ExternalLoginService.LoginAudit(null, "onboarding:" + providerKey,
                        "外部认证成功，等待创建或绑定账号（外部标识=" + verified.subject() + "）");
            } else {
                UserEntity user = users.selectById(linked.getUserId());
                if (user == null || user.getStatus() != 1) throw new BusinessException("绑定账号不存在或已停用");
                linked.setDisplayNameSnapshot(verified.displayName());
                linked.setEmailSnapshot(verified.email());
                linked.setLastLoginTime(LocalDateTime.now());
                identities.updateById(linked);
                grant = issueMember(user, transaction.rememberMe(), providerKey);
                audit = new ExternalLoginService.LoginAudit(user.getId(), user.getUsername(),
                        "外部登录成功（" + providerKey + "）");
            }
        }
        return new ExternalLoginService.Completion(grant, transaction.returnTo(), audit);
    }

    /**
     * 内部统一认证按已验证工号匹配到本地员工后的登录入口。
     * <p>
     * 它与普通外部登录的区别是：UIAS 不允许未绑定身份直接进入自助开户页，而是必须先由管理员
     * 为工号开通本地账号、部门和角色。身份绑定仍复用同一张表与唯一性规则。
     */
    @Transactional
    public ExternalLoginService.Completion settleEmployeeLogin(LoginTransaction transaction,
                                                               VerifiedIdentity verified, UserEntity user) {
        if (user == null || user.getStatus() != 1) throw new BusinessException("员工账号不存在或已停用");
        bind(user.getId(), verified);
        SessionGrant grant = issueMember(user, transaction.rememberMe(), verified.providerKey());
        ExternalLoginService.LoginAudit audit = new ExternalLoginService.LoginAudit(user.getId(), user.getUsername(),
                "通过内部统一认证登录成功");
        return new ExternalLoginService.Completion(grant, transaction.returnTo(), audit);
    }

    private void bind(Long userId, VerifiedIdentity verified) {
        ExternalIdentityEntity bySubject = identities.find(verified.providerKey(), verified.issuer(), verified.subject());
        if (bySubject != null && !bySubject.getUserId().equals(userId)) throw new BusinessException("该外部身份已经绑定其他账号");
        ExternalIdentityEntity sameProvider = identities.findByUserId(userId).stream()
                .filter(item -> item.getProviderKey().equals(verified.providerKey())).findFirst().orElse(null);
        if (sameProvider != null && !sameProvider.getSubject().equals(verified.subject())) {
            throw new BusinessException("当前账号已经绑定了另一个" + verified.providerKey() + "身份，请先解绑");
        }
        if (bySubject != null) return;
        ExternalIdentityEntity entity = new ExternalIdentityEntity();
        entity.setUserId(userId);
        entity.setProviderKey(verified.providerKey());
        entity.setIssuer(verified.issuer());
        entity.setSubject(verified.subject());
        entity.setDisplayNameSnapshot(verified.displayName());
        entity.setEmailSnapshot(verified.email());
        entity.setCreatedTime(LocalDateTime.now());
        entity.setLastLoginTime(LocalDateTime.now());
        identities.insert(entity);
    }

    private SessionGrant issueMember(UserEntity user, boolean rememberMe, String providerKey) {
        String sid = sessions.createMember(user.getId(), user.getUsername(), user.getAuthVersion(), rememberMe,
                "EXTERNAL", providerKey);
        LoginSession session = sessions.find(sid);
        return new SessionGrant(sid, jwt.issue(sid, session), null, false);
    }

    /**
     * 绑定流程的收尾：优先在<b>发起绑定时那个浏览器已经持有的会话</b>上继续，而不是另开一个。
     * <p>
     * 之前这里无条件调 {@link #issueMember(UserEntity, boolean, String)}，带来两个副作用：
     * <ol>
     *   <li>一个"给账号加个登录方式"的附加操作，会把用户的会话整个换掉 ——
     *       绑定前后浏览器手里的凭据不是同一个，多设备/多标签下也会莫名其妙多出一个会话；</li>
     *   <li>它把 rememberMe 写死成 false，于是原本勾了"记住我"的用户，做完一次绑定之后
     *       Cookie 从 14 天变成会话级，关掉浏览器就掉登录态，而且没有任何提示。
     *       复用原会话之后，Cookie 有效期由原会话自身的 rememberMe 决定，不会被降级。</li>
     * </ol>
     * <p>
     * 复用前必须确认三件事，否则宁可新建：会话存在、不是开户临时会话、且 <b>属于同一个用户</b>。
     * 最后一条不能省：同一个浏览器里可以先让 A 发起绑定、中途换成 B 登录，
     * 此时回调带回来的 Cookie 已经是 B 的了，直接拿来用就等于把 B 的会话当成 A 绑定的结果返回。
     * 另外 authVersion 也要一致，否则这个会话在过滤器里本来就通不过，
     * 复用等于让用户看到"绑定成功但仍是未登录状态"。
     */
    private SessionGrant reuseOrIssue(UserEntity user, String candidateSid, String providerKey) {
        LoginSession existing = candidateSid == null || candidateSid.isBlank() ? null : sessions.find(candidateSid);
        boolean reusable = existing != null && !existing.onboarding()
                && user.getId().equals(existing.userId())
                && user.getAuthVersion() != null && user.getAuthVersion() == existing.authVersion();
        if (!reusable) return issueMember(user, false, providerKey);
        return new SessionGrant(candidateSid, jwt.issue(candidateSid, existing), null, false);
    }
}
