package org.lbl.agent.domain.report;

/** 通用表格列；key 只能读取 rows 中同名字段，不支持表达式或 HTML。 */
public record AgentTableColumn(String key, String title, String format) {
    public AgentTableColumn {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("表格列 key 不能为空");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("表格列标题不能为空");
    }
}
