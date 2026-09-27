package org.lbl.capability.compare.core;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Comparator;
import java.util.List;

/**
 * 排序归一化：把两份返回按业务主键排序，消除"顺序不同但内容相同"造成的假差异。
 * <p>
 * 纯函数、无状态，便于单测。与 {@code DeptPaths} 一样属于"算法性工具类"。
 */
public final class Canonicalizer {
    private Canonicalizer() {
    }

    /** 按 keyField 升序排序（key 缺失按空串处理）。keyOf 恒不为 null，故无需 nullsLast。 */
    public static List<JsonNode> sortByKey(List<JsonNode> records, String keyField) {
        return records.stream()
                .sorted(Comparator.comparing(record -> keyOf(record, keyField)))
                .toList();
    }

    /** 提取原始 key 序列（保持原顺序），用于判断"严格口径下顺序是否一致"。 */
    public static List<String> keys(List<JsonNode> records, String keyField) {
        return records.stream().map(record -> keyOf(record, keyField)).toList();
    }

    public static String keyOf(JsonNode record, String keyField) {
        JsonNode key = record.get(keyField);
        return key == null || key.isNull() ? "" : key.asText();
    }
}
