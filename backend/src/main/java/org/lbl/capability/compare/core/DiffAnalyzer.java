package org.lbl.capability.compare.core;

import com.fasterxml.jackson.databind.JsonNode;
import org.lbl.capability.compare.model.CompareModels.DiffReport;
import org.lbl.capability.compare.model.CompareModels.DiffType;
import org.lbl.capability.compare.model.CompareModels.FieldDiff;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 差异分析：双口径 diff（严格=含顺序 / 宽松=忽略顺序按 key 对齐），并归类为四种 DiffType。
 * <p>
 * 这是解决"报文不一致到底是数据不一致还是排序不一致"的确定性核心 —— <b>绝不让 LLM 自己判断排序</b>，
 * 那是它最容易幻觉的地方。分类结果以结构化 {@link DiffReport} 交给 LLM 解释。
 */
public final class DiffAnalyzer {
    private DiffAnalyzer() {
    }

    public static DiffReport diff(List<JsonNode> left, List<JsonNode> right, String keyField) {
        // 严格口径：原始 key 序列是否一致（判断"仅排序不一致"）。
        boolean orderMismatch = !Canonicalizer.keys(left, keyField).equals(Canonicalizer.keys(right, keyField));

        // 宽松口径：按 key 对齐，忽略顺序，比较每条记录的内容。
        Map<String, JsonNode> leftMap = indexByKey(left, keyField);
        Map<String, JsonNode> rightMap = indexByKey(right, keyField);

        List<String> leftOnly = new ArrayList<>();
        List<String> rightOnly = new ArrayList<>();
        for (String key : Canonicalizer.keys(left, keyField)) {
            if (!rightMap.containsKey(key)) leftOnly.add(key);
        }
        for (String key : Canonicalizer.keys(right, keyField)) {
            if (!leftMap.containsKey(key)) rightOnly.add(key);
        }

        List<FieldDiff> fieldDiffs = new ArrayList<>();
        int common = 0;
        for (Map.Entry<String, JsonNode> entry : leftMap.entrySet()) {
            JsonNode rightRecord = rightMap.get(entry.getKey());
            if (rightRecord == null) continue;
            common++;
            collectFieldDiffs(entry.getKey(), entry.getValue(), rightRecord, fieldDiffs);
        }

        DiffType type = classify(leftOnly, rightOnly, fieldDiffs, orderMismatch);
        String summary = buildSummary(type, left.size(), right.size(), common, leftOnly, rightOnly, fieldDiffs, orderMismatch);
        return new DiffReport(type, left.size(), right.size(), common, List.copyOf(leftOnly), List.copyOf(rightOnly),
                List.copyOf(fieldDiffs), orderMismatch, summary);
    }

    private static DiffType classify(List<String> leftOnly, List<String> rightOnly,
                                     List<FieldDiff> fieldDiffs, boolean orderMismatch) {
        if (!leftOnly.isEmpty() || !rightOnly.isEmpty()) return DiffType.MISSING_RECORDS;
        if (!fieldDiffs.isEmpty()) return DiffType.DATA_INCONSISTENT;
        if (orderMismatch) return DiffType.SORT_ONLY;
        return DiffType.CONSISTENT;
    }

    private static String buildSummary(DiffType type, int leftCount, int rightCount, int common,
                                       List<String> leftOnly, List<String> rightOnly,
                                       List<FieldDiff> fieldDiffs, boolean orderMismatch) {
        return switch (type) {
            case CONSISTENT -> "对比结果：两侧完全一致（共 " + common + " 条，含顺序）。";
            case SORT_ONLY -> "对比结果：数据一致、仅排序不一致（共 " + common + " 条，顺序" + (orderMismatch ? "不同" : "相同") + "）。无需修复。";
            case DATA_INCONSISTENT -> "对比结果：数据不一致。" + common + " 条共同记录中有 " + fieldDiffs.size()
                    + " 处字段差异（示例：" + topFieldDiffs(fieldDiffs, 3) + "）。";
            case MISSING_RECORDS -> "对比结果：记录缺失。左侧独有 " + leftOnly.size() + " 条、右侧独有 " + rightOnly.size()
                    + " 条（示例 key：" + topKeys(leftOnly, rightOnly, 3) + "）。";
        };
    }

    private static String topFieldDiffs(List<FieldDiff> fieldDiffs, int limit) {
        return fieldDiffs.stream().limit(limit)
                .map(d -> d.key() + "." + d.field() + ": " + d.left() + " → " + d.right())
                .reduce((a, b) -> a + "；" + b).orElse("");
    }

    private static String topKeys(List<String> leftOnly, List<String> rightOnly, int limit) {
        List<String> all = new ArrayList<>(leftOnly.subList(0, Math.min(limit, leftOnly.size())));
        rightOnly.stream().limit(limit).forEach(all::add);
        return all.isEmpty() ? "" : String.join("、", all);
    }

    private static Map<String, JsonNode> indexByKey(List<JsonNode> records, String keyField) {
        Map<String, JsonNode> index = new LinkedHashMap<>();
        for (JsonNode record : records) {
            index.putIfAbsent(Canonicalizer.keyOf(record, keyField), record);
        }
        return index;
    }

    private static void collectFieldDiffs(String key, JsonNode a, JsonNode b, List<FieldDiff> out) {
        Set<String> fields = new LinkedHashSet<>();
        a.fieldNames().forEachRemaining(fields::add);
        b.fieldNames().forEachRemaining(fields::add);
        for (String field : fields) {
            JsonNode av = a.get(field);
            JsonNode bv = b.get(field);
            // JsonNode 深比较：覆盖"字段缺失"（一侧 null）与"值不同"两种情况。
            if (Objects.equals(av, bv)) continue;
            out.add(new FieldDiff(key, field, render(av), render(bv)));
        }
    }

    private static String render(JsonNode node) {
        if (node == null || node.isNull()) return "<缺失>";
        if (node.isTextual()) return node.asText();
        return node.toString();
    }
}
