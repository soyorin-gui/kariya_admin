package org.lbl.agent.core;

import org.lbl.agent.core.model.AgentModels.AgentContext;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 能力注册表：启动时聚合所有 {@link Capability} 实现，提供"按当前用户权限过滤后的
 * 工具清单 + 系统提示 + 工具名反查"。
 * <p>
 * 新增能力 = 新增一个 {@code @Component} 的 Capability 实现，本类与 AgentService 均无需改动。
 */
@Component
public class CapabilityRegistry {
    private final List<Capability> capabilities;
    private final Map<String, Tool> toolsByName;

    public CapabilityRegistry(List<Capability> capabilities) {
        this.capabilities = List.copyOf(capabilities);
        Map<String, Tool> index = new HashMap<>();
        for (Capability capability : capabilities) {
            for (Tool tool : capability.tools()) {
                Tool previous = index.putIfAbsent(tool.name(), tool);
                if (previous != null) {
                    throw new IllegalStateException("工具名重复：" + tool.name() + "（能力 " + capability.id() + " 与其它能力冲突）");
                }
            }
        }
        this.toolsByName = Map.copyOf(index);
    }

    /** 当前用户可见（= 权限允许使用的能力）的工具集合。 */
    public List<Tool> visibleTools(AgentContext context) {
        return capabilities.stream()
                .filter(capability -> context.hasAll(capability.requiredPermissions()))
                .flatMap(capability -> capability.tools().stream())
                .toList();
    }

    /** 组装系统提示：基础提示 + 每个可见能力的能力片段。 */
    public String systemPrompt(AgentContext context) {
        StringBuilder prompt = new StringBuilder(BASE_PROMPT);
        capabilities.stream()
                .filter(capability -> context.hasAll(capability.requiredPermissions()))
                .forEach(capability -> prompt.append("\n\n").append(capability.systemPrompt()));
        return prompt.toString();
    }

    /** 按工具名反查工具（跨所有能力，含不可见的，用于兜底校验模型是否幻觉出未知工具）。 */
    public Tool toolByName(String name) {
        return toolsByName.get(name);
    }

    public List<Capability> all() {
        return capabilities;
    }

    private static final String BASE_PROMPT = """
            你是公司内部管理系统里的 AI 助手，负责协助用户完成系统操作、数据排查与材料准备。
            遵循以下规则：
            1. 优先使用提供的工具去获取真实数据，绝不编造工具返回里没有的事实。
            2. 工具返回的 summary 是结构化事实，你的任务是在其上给出结论与建议，不要凭空补充未经验证的推断。
            3. 全程使用简体中文回答；结论先行，再给依据。
            4. 若某个工具你无权调用，直接告知用户"该能力未对你开放"。
            5. 回答保持简洁，避免无关的客套话。""";
}
