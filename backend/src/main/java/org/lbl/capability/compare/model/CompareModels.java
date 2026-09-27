package org.lbl.capability.compare.model;

import java.util.List;

/**
 * 双系统对比能力的模型。刻意与 LLM 无关 —— 这是"确定性层"的输出，LLM 只消费它。
 */
public final class CompareModels {
    private CompareModels() {
    }

    /** 差异分类。这是整个对比能力最重要的输出，直接决定 LLM 给的结论是否可信。 */
    public enum DiffType {
        /** 完全一致（含顺序）。 */
        CONSISTENT,
        /** 数据不一致：宽松口径（忽略顺序）下仍有字段值差异 —— 真问题，需修复。 */
        DATA_INCONSISTENT,
        /** 仅排序不一致：宽松口径一致、但顺序不同 —— 数据本身 ok，可接受。 */
        SORT_ONLY,
        /** 记录缺失：一边有、一边没有。 */
        MISSING_RECORDS
    }

    /** 一处字段级差异。left/right 为两边的字符串化值（缺失侧为 "<缺失>"）。 */
    public record FieldDiff(String key, String field, String left, String right) {
    }

    /**
     * 结构化对比报告。summaryText 是给 LLM 看的摘要，前端则用其它字段渲染完整 diff 视图。
     *
     * @param type          差异分类
     * @param leftCount     左侧（solr+hbase）记录数
     * @param rightCount    右侧（es+hbase）记录数
     * @param commonCount   两侧都存在的 key 数
     * @param leftOnly      仅左侧有的 key
     * @param rightOnly     仅右侧有的 key
     * @param fieldDiffs    字段级差异明细
     * @param orderMismatch 原始顺序是否不同（严格口径）
     */
    public record DiffReport(DiffType type, int leftCount, int rightCount, int commonCount,
                             List<String> leftOnly, List<String> rightOnly,
                             List<FieldDiff> fieldDiffs, boolean orderMismatch, String summaryText) {
    }
}
