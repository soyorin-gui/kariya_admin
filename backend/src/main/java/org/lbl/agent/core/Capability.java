package org.lbl.agent.core;

import java.util.List;
import java.util.Set;

/**
 * 一个"AI 能力" = 一组工具 + 一段系统提示 + 一个权限门槛。
 * <p>
 * 这是多能力平台（对比 / 日志排查 / 投产文档 / 血缘…）的插件契约：新增一个能力，
 * 就是新建一个包并实现本接口；{@link CapabilityRegistry} 启动时自动聚合所有实现，
 * agent 框架本身零改动。
 */
public interface Capability {

    /** 能力唯一标识，例如 {@code compare}。 */
    String id();

    /** 能力名，例如「双系统对比」。 */
    String name();

    /** 给 LLM 路由用的能力说明：用户问什么时应该用这个能力。 */
    String description();

    /** 追加到系统提示里的能力专属片段（告诉模型这个能力的规则、边界、输出要求）。 */
    String systemPrompt();

    /** 该能力暴露的工具。 */
    List<Tool> tools();

    /**
     * 使用该能力所需的权限码。空集合 = 任何已登录用户可用；
     * 非空 = 需拥有全部权限码（超管自动放行，见 {@code AgentContext#hasAll}）。
     */
    Set<String> requiredPermissions();
}
