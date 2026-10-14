package org.lbl.common.util;

/** 文本字段的存储规范化。 */
public final class TextValues {
    private TextValues() {
    }

    /** 空白值转为 null，其他值去除首尾空格；保留既有 isBlank/trim 语义。 */
    public static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
