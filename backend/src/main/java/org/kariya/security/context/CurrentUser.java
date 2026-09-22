package org.kariya.security.context;

import java.security.Principal;

/**
 * 认证通过后的当前用户身份，作为 SecurityContext 中的 principal。
 * <p>
 * 之所以实现 {@link Principal}：{@code UsernamePasswordAuthenticationToken.getName()} 在 principal 不是
 * UserDetails 时会走 Principal 分支，这样既有的 {@code authentication.getName()}（见 AccessPolicy.actor()）
 * 仍然返回用户名，不会因为换 principal 类型而悄悄失效。
 * <p>
 * 顺带把用户 id 带在身份里，操作日志等场景就不必再为拿一个 id 回查一次数据库。
 */
public record CurrentUser(Long id, String username, Long deptId) implements Principal {
    @Override
    public String getName() {
        return username;
    }
}
