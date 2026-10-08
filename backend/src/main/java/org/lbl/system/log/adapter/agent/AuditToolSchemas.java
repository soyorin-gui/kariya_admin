package org.lbl.system.log.adapter.agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 审计工具共用的 JSON Schema，避免复制后约束逐渐不一致。 */
final class AuditToolSchemas {
    private AuditToolSchemas() {
    }

    static Map<String, Object> analyze() {
        Map<String, Object> properties = rangeProperties();
        properties.put("topN", integer("排行榜返回数量，默认 10，范围 1-50", 1, 50));
        return objectSchema(properties, List.of("range"));
    }

    /** Risk scans only need a range, not ranking parameters. */
    static Map<String, Object> riskScan() {
        return objectSchema(rangeProperties(), List.of("range"));
    }

    static Map<String, Object> timeline() {
        Map<String, Object> properties = rangeProperties();
        properties.put("userId", integer("用户 ID；已知时优先使用", 1, null));
        properties.put("username", Map.of("type", "string", "maxLength", 64,
                "description", "完整用户名；不知道 userId 时使用精确匹配"));
        properties.put("limit", integer("最多返回事件数，默认 50，范围 1-100", 1, 100));
        Map<String, Object> schema = new LinkedHashMap<>(objectSchema(properties, List.of("range")));
        schema.put("anyOf", List.of(
                Map.of("required", List.of("userId")),
                Map.of("required", List.of("username"))));
        return Map.copyOf(schema);
    }

    private static Map<String, Object> rangeProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("range", Map.of(
                "type", "string",
                "enum", List.of("TODAY", "YESTERDAY", "RECENT_HOURS", "CUSTOM"),
                "description", "时间范围预设；今天用 TODAY，自定义时间用 CUSTOM"));
        properties.put("recentHours", integer("range=RECENT_HOURS 时必填，范围 1-24", 1, 24));
        properties.put("beginTime", Map.of("type", "string", "format", "date-time",
                "description", "range=CUSTOM 时必填，ISO-8601 本地时间"));
        properties.put("endTime", Map.of("type", "string", "format", "date-time",
                "description", "range=CUSTOM 时必填，ISO-8601 本地时间；区间为左闭右开"));
        return properties;
    }

    private static Map<String, Object> integer(String description, Integer minimum, Integer maximum) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "integer");
        schema.put("description", description);
        if (minimum != null) schema.put("minimum", minimum);
        if (maximum != null) schema.put("maximum", maximum);
        return Map.copyOf(schema);
    }

    private static Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.copyOf(properties));
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return Map.copyOf(schema);
    }
}
