package org.lbl.intranet.lineage.vo;

import java.util.List;

/** 仅返回已登记关系形成的确定性最小子图，不推断字段映射。 */
public record LineageGraphVO(List<Node> nodes, List<Edge> edges) {
    public record Node(String id, String type, String label, String detail) { }
    public record Edge(String source, String target, String label) { }
}
