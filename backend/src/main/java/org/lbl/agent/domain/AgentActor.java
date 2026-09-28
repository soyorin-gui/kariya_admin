package org.lbl.agent.domain;

import java.util.Set;

/**
 * 一次 Agent 运行使用的调用者快照。
 *
 * <p>它在 HTTP 请求线程中从 {@code AccessPolicy.Actor} 构造，再显式传入后台任务。
 * Agent 核心和工具因此不依赖 SecurityContext 的线程本地状态。</p>
 */
public record AgentActor(Long userId, String username, boolean superAdmin, Set<String> permissions) {
    public AgentActor {
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    public boolean has(String permission) {
        return superAdmin || permissions.contains(permission);
    }

    public boolean hasAll(Set<String> required) {
        return required == null || required.isEmpty() || required.stream().allMatch(this::has);
    }
}
