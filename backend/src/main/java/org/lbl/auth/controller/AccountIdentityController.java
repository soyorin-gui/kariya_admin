package org.lbl.auth.controller;

import org.lbl.auth.external.ExternalLoginService;
import org.lbl.auth.uias.UiasLoginService;
import org.lbl.auth.identity.*;
import org.lbl.common.exception.BusinessException;
import org.lbl.common.result.Result;
import org.lbl.security.context.CurrentUser;
import org.lbl.system.log.aspect.OperationLog;
import org.lbl.system.user.mapper.UserMapper;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 「登录与安全」页面的后端：查看 / 绑定 / 解绑第三方登录方式。
 * <p>
 * 两个写操作都标了 {@link OperationLog}：它们直接改变"这个账号能被谁登录进来"，
 * 属于账号安全里最该留痕的动作。发起绑定时记一条，回调真正绑上时另在
 * {@code sys_login_log} 里记一条成功日志（见 ExternalLoginPersistence），
 * 因此"谁在什么时候给哪个账号加了哪种登录方式"是可完整还原的。
 */
@RestController
@RequestMapping("/api/account/identities")
@PreAuthorize("isAuthenticated() and !hasAuthority('onboarding:access')")
public class AccountIdentityController {
    private final ExternalIdentityMapper identities;
    private final LocalCredentialMapper credentials;
    private final ExternalLoginService external;
    private final UiasLoginService uias;
    private final UserMapper users;

    public AccountIdentityController(ExternalIdentityMapper identities, LocalCredentialMapper credentials,
                                     ExternalLoginService external, UiasLoginService uias, UserMapper users) {
        this.identities = identities;
        this.credentials = credentials;
        this.external = external;
        this.uias = uias;
        this.users = users;
    }

    @GetMapping
    public Result<List<Map<String, Object>>> list(@AuthenticationPrincipal CurrentUser user) {
        return Result.ok(identities.findByUserId(user.id()).stream().map(value -> Map.<String, Object>of(
                "providerKey", value.getProviderKey(),
                "displayName", value.getDisplayNameSnapshot() == null ? "" : value.getDisplayNameSnapshot(),
                "email", value.getEmailSnapshot() == null ? "" : value.getEmailSnapshot(),
                "createdTime", value.getCreatedTime())).toList());
    }

    @PostMapping("/{provider}/start")
    @OperationLog(module = "登录与安全", action = "发起第三方登录绑定")
    public Result<Map<String, String>> startBinding(@PathVariable String provider,
                                                    Authentication authentication,
                                                    @AuthenticationPrincipal CurrentUser user) {
        // 刷新 Cookie 的 Path 是 /api/auth，刻意不会出现在 /api/account 请求中。
        // 此端点已经由 Bearer token 认证，JWT 过滤器把对应 sid 写进 Authentication.details；
        // 从这里取值既能可靠完成绑定，也不必扩大刷新凭据 Cookie 的发送范围。
        String sid = authentication.getDetails() instanceof String value ? value : null;
        String authorizationUrl = "uias".equalsIgnoreCase(provider)
                ? uias.beginBinding(user.id(), sid)
                : external.beginBinding(provider, user.id(), sid);
        return Result.ok(Map.of("authorizationUrl", authorizationUrl));
    }

    /**
     * 解除某个第三方登录方式的绑定。
     * <p>
     * 校验顺序很重要，之前是反的：先判"必须至少保留一种可用的登录方式"，再判"这种方式到底绑没绑"。
     * 结果一个只绑定过 GitHub 的用户，去解绑一个<b>从未绑定</b>的微信时，收到的是
     * "必须至少保留一种可用的登录方式" —— 一句与事实无关的话，用户会以为"系统不让我解绑"，
     * 而不是"我压根没绑过它"。所以必须先确认"确实绑了"，再谈"解绑后还剩下什么"。
     */
    @DeleteMapping("/{provider}")
    @OperationLog(module = "登录与安全", action = "解除第三方登录绑定")
    @Transactional
    public Result<Void> unbind(@PathVariable String provider, @AuthenticationPrincipal CurrentUser user) {
        if (users.selectByIdForUpdate(user.id()) == null) throw new BusinessException("账号不存在或已停用");
        List<ExternalIdentityEntity> current = identities.findByUserId(user.id());
        if (current.stream().noneMatch(item -> item.getProviderKey().equals(provider))) {
            throw new BusinessException("该登录方式尚未绑定");
        }
        LocalCredentialEntity credential = credentials.selectById(user.id());
        boolean hasPassword = credential != null && Integer.valueOf(1).equals(credential.getEnabled());
        boolean hasAnotherUsableExternal = current.stream()
                .filter(item -> !item.getProviderKey().equals(provider))
                .anyMatch(item -> external.isProviderUsable(item.getProviderKey()));
        if (!hasPassword && !hasAnotherUsableExternal) {
            throw new BusinessException("解绑后将不再有可用的登录方式，请先设置密码或绑定其他已启用的登录方式");
        }
        if (identities.deleteBinding(user.id(), provider) == 0) {
            // 上面刚查过它存在，删到 0 行只可能是并发解绑。给出与"未绑定"一致的提示即可。
            throw new BusinessException("该登录方式尚未绑定");
        }
        return Result.ok(null, "已解除绑定");
    }
}
