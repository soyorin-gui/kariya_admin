package org.lbl.agent.domain;

import java.util.List;

/**
 * 发起本轮请求时的界面位置。
 *
 * <p>这是客户端提供的辅助语境，不是授权凭据。Agent 可以用它理解“这个页面”“当前模块”等指代，
 * 但工具是否可见、能否执行以及数据范围仍只能由 {@link AgentActor} 和业务授权层决定。</p>
 */
public record AgentPageContext(String routePath, Long menuId, String pageTitle, List<String> breadcrumb) {
    public AgentPageContext {
        breadcrumb = breadcrumb == null ? List.of() : List.copyOf(breadcrumb);
    }
}
