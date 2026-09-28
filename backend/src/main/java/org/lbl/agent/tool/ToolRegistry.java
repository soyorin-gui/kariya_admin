package org.lbl.agent.tool;

import org.lbl.agent.domain.AgentActor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** 自动发现所有 AgentTool，校验名称唯一性，并按当前调用者权限筛选。 */
@Component
public class ToolRegistry {
    private static final Pattern NAME = Pattern.compile("^[a-z][a-z0-9_]{0,63}$");
    private final Map<String, AgentTool<?, ?>> tools;

    public ToolRegistry(List<AgentTool<?, ?>> tools) {
        Map<String, AgentTool<?, ?>> index = new HashMap<>();
        for (AgentTool<?, ?> tool : tools) {
            String name = tool.descriptor().name();
            if (name == null || !NAME.matcher(name).matches()) {
                throw new IllegalStateException("Agent 工具名不合法：" + name);
            }
            if (tool.descriptor().description() == null || tool.descriptor().description().isBlank()) {
                throw new IllegalStateException("Agent 工具缺少 description：" + name);
            }
            AgentTool<?, ?> previous = index.putIfAbsent(name, tool);
            if (previous != null) throw new IllegalStateException("Agent 工具名重复：" + name);
        }
        this.tools = Map.copyOf(index);
    }

    public List<AgentTool<?, ?>> visibleTo(AgentActor actor) {
        return tools.values().stream()
                .filter(tool -> actor.hasAll(tool.descriptor().permissions()))
                .sorted((left, right) -> left.descriptor().name().compareTo(right.descriptor().name()))
                .toList();
    }

    public AgentTool<?, ?> find(String name) {
        return tools.get(name);
    }
}
