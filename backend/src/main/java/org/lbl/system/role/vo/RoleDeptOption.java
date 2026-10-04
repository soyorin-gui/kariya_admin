package org.lbl.system.role.vo;

/** 祖先节点仅用于展示层级，selectable 决定是否可以授权。 */
public record RoleDeptOption(Long id, Long parentId, String deptName, Integer status, boolean selectable) {
}
