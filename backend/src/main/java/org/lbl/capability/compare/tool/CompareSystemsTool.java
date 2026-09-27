package org.lbl.capability.compare.tool;

import org.lbl.agent.core.Tool;
import org.lbl.agent.core.model.AgentModels.AgentContext;
import org.lbl.agent.core.model.AgentModels.ToolResult;
import org.lbl.capability.compare.core.ComparisonService;
import org.lbl.capability.compare.model.CompareModels.DiffReport;
import org.lbl.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 工具：把同一个查询请求发到两套系统并对比。
 * <p>
 * 注意 description 的写法 —— 它是 LLM 决定"要不要用这个工具、参数怎么填"的唯一依据，
 * 必须把触发场景和参数语义说清楚。
 */
@Component
public class CompareSystemsTool implements Tool {
    private final ComparisonService comparison;

    public CompareSystemsTool(ComparisonService comparison) {
        this.comparison = comparison;
    }

    @Override
    public String name() {
        return "compare_systems";
    }

    @Override
    public String description() {
        return "把同一个查询请求报文分别发送到 solr+hbase 与 es+hbase 两套查询系统，对比返回是否一致。"
                + "当用户提到\"对比/核对/验证两套系统、切换验证、报文是否一致、排序是否一致\"时使用。"
                + "返回结果会区分\"数据不一致\"与\"仅排序不一致\"（后者数据本身一致，无需修复）。";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "requestBody", Map.of(
                                "type", "string",
                                "description", "要发送到两套系统的查询请求报文（JSON 字符串）。"
                                        + "若用户只给了业务条件，请先拼成请求报文再传入。")),
                "required", List.of("requestBody"));
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments, AgentContext context) {
        Object raw = arguments == null ? null : arguments.get("requestBody");
        String requestBody = raw == null ? null : String.valueOf(raw);
        if (requestBody == null || requestBody.isBlank()) {
            throw new BusinessException("缺少查询请求报文（requestBody）");
        }
        DiffReport report = comparison.compare(requestBody);
        return ToolResult.diff(report.summaryText(), report);
    }
}
