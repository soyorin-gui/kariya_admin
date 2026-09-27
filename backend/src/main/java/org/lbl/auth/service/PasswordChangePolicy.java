package org.lbl.auth.service;

import org.lbl.auth.identity.LocalCredentialEntity;
import org.lbl.config.SecurityProperties;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 判断"当前这次会话是否必须先改密码"，把两件性质不同的事收敛到同一个出口：
 * <ol>
 *   <li><b>管理员重置过密码</b>：{@code password_change_required = 1}。管理员手上有一个
 *       刚刚生成/指定的密码，属于"管理员已知的凭据"，必须先换掉。</li>
 *   <li><b>密码超期</b>：{@code password-max-age-days} 为正数时，距上次改密超过该天数即视为过期
 *       （0 = 关闭，当前默认值）。</li>
 * </ol>
 *
 * <h2>完整的"要不要强制改密" = 本类判断 AND "这次是密码登录"</h2>
 * 本类<b>只</b>回答"凭据是否已过期/被重置"，<b>不</b>回答"这次是怎么登录进来的" ——
 * 后者由调用方自己与 {@code authMethod} 求与。**分开写是刻意的**：规则无法收敛成单一方法，
 * 因为 4 个调用点里有 2 个只能读会话快照而不能读凭据。
 * 完整的调用点清单与原因见 {@link #requiresChange} 的 javadoc。
 *
 * <h2>为什么只对"用密码登录"的会话生效</h2>
 * 这是产品决定，不是遗漏：<b>强制改密只针对用账号密码登录的用户；通过第三方（GitHub/微信/UIAS）
 * 登录的用户不受约束</b> —— 他们不想改密码，就一直用第三方登录即可，系统不拦。
 * <p>
 * 直接后果有两条，都是明确接受了的：
 * <ul>
 *   <li>一个既绑定了第三方、密码又已过期的用户，可以改用第三方登录绕过这道闸门；</li>
 *   <li>因此密码过期策略对"有第三方登录方式"的账号事实上没有强制力。</li>
 * </ul>
 * <b>不要把这里的 {@code authMethod} 判断当成 bug 去掉</b>：那是"对所有登录方式一律强制改密"，
 * 与上面的产品决定正好相反，而且对只绑了第三方、没有密码的账号根本无从执行。
 */
@Component
public class PasswordChangePolicy {
    private final long maxAgeDays;

    public PasswordChangePolicy(SecurityProperties security) {
        this.maxAgeDays = security.passwordMaxAgeDays();
    }

    /**
     * 该凭据是否处于"必须改密"状态（不含登录方式判断）。
     * <p>
     * 没有凭据、或凭据被停用（该账号用不了密码登录）时返回 {@code false}：
     * 对一个连密码都没有的账号要求"改密码"是无意义的。
     *
     * <h2>⚠️ 完整规则 = "本次是密码登录" AND 本方法</h2>
     * 本方法<b>刻意</b>不含 {@code authMethod} 判断，调用方必须自己求与。
     * 这条规则<b>没有</b>、也<b>不可能有</b>单一入口方法，因为 4 个调用点里有 2 个拿不到凭据：
     *
     * <table border="1">
     *   <caption>全项目的 4 个判定点</caption>
     *   <tr><th>调用点</th><th>数据来源</th><th>为什么</th></tr>
     *   <tr><td>{@code AuthService.refresh}</td><td>凭据（查库）</td>
     *       <td>刷新会话时顺手重算一次到期状态</td></tr>
     *   <tr><td>{@code AuthService.me}（{@code /auth/me}）</td><td><b>会话快照</b></td>
     *       <td rowspan="2">这两处必须读 {@code LoginSession.passwordChangeRequired()} 快照，
     *           而不是重新查凭据：否则用户操作到一半，凭据状态一变就会<b>突然掉权限</b>；
     *           而且 {@code JwtAuthenticationFilter} 在每个请求上跑，读库会拖慢整条链路</td></tr>
     *   <tr><td>{@code JwtAuthenticationFilter}</td><td><b>会话快照</b></td></tr>
     *   <tr><td>{@code RegistrationService.issue}</td><td>凭据（刚查过）</td>
     *       <td>会话刚创建，凭据就在手上</td></tr>
     * </table>
     *
     * <b>改这条规则时必须同时改这 4 处。</b>
     * （历史记录：曾经有一个 {@code appliesToPasswordLogin(authMethod, credential)} 方法想做单一入口，
     * 但因为上述 2 处拿不到凭据而<b>从未被调用过</b>，已于本次清理中删除。详见 {@code docs/ARCHIVE-removed-code.md}。）
     */
    public boolean requiresChange(LocalCredentialEntity credential) {
        if (credential == null || !Integer.valueOf(1).equals(credential.getEnabled())) return false;
        if (Integer.valueOf(1).equals(credential.getPasswordChangeRequired())) return true;
        if (maxAgeDays <= 0) return false;
        LocalDateTime changedTime = credential.getPasswordChangedTime();
        // changedTime 为 null 时按"已过期"处理：宁可让用户改一次密码，
        // 也不要因为一条脏数据就永久跳过密码有效期检查。
        return changedTime == null || !changedTime.plusDays(maxAgeDays).isAfter(LocalDateTime.now());
    }
}
