package org.lbl.capability.compare;

import org.lbl.agent.core.Capability;
import org.lbl.agent.core.Tool;
import org.lbl.capability.compare.tool.CompareSystemsTool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 能力：双系统对比。
 * <p>
 * 这是"一个能力"的标准形态：一个 {@code @Component} 实现 {@link Capability}，
 * 声明自己的工具 + 系统提示 + 权限门槛。新增其它能力照抄这个结构即可。
 */
@Component
public class CompareCapability implements Capability {
    private final CompareSystemsTool tool;

    public CompareCapability(CompareSystemsTool tool) {
        this.tool = tool;
    }

    @Override
    public String id() {
        return "compare";
    }

    @Override
    public String name() {
        return "双系统对比";
    }

    @Override
    public String description() {
        return "把同一查询请求发送到 solr+hbase 与 es+hbase 两套系统并对比结果是否一致。";
    }

    @Override
    public String systemPrompt() {
        return """
                【能力：双系统对比】
                当用户要求对比/核对两套查询系统（solr+hbase 与 es+hbase）的返回是否一致时，
                调用 compare_systems 工具。
                对比结果分四类：数据不一致（需修复）、仅排序不一致（数据一致，可接受）、记录缺失、完全一致。
                你的职责：拿到 DiffReport 后，先用一句话给结论（是否一致、差异类型、差异数量），
                再按需列出关键差异样例。若为"仅排序不一致"，明确告知用户"数据本身一致，无需修复"。""";
    }

    @Override
    public List<Tool> tools() {
        return List.of(tool);
    }

    @Override
    public Set<String> requiredPermissions() {
        // TODO(硬化): 上线前改成 Set.of("agent:compare:use") 并注册该权限码
        //  （按 DEVELOPMENT.md B.3 的"4 处都要改"：init_data.sql + SystemPermissionInitializer + 前端 Permission + 这里）。
        //  骨架阶段留空，让所有已登录用户都能用它跑通链路。
        return Set.of();
    }
}
