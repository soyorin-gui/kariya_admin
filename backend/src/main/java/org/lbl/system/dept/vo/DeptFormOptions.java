package org.lbl.system.dept.vo;

import java.util.List;

/**
 * 新增/编辑部门表单的候选数据。
 *
 * @param leaders       可选负责人（按当前账号的数据范围收窄）
 * @param canCreateRoot 能否新增顶级部门 —— 决定表单里"上级部门"能不能被清空。
 *                      必须由后端给出：这条规则的唯一依据是 {@code AccessPolicy.canCreateRootDept}，
 *                      前端无法从其它字段推导（见 {@code DeptService.formOptions} 的说明）。
 */
public record DeptFormOptions(List<Option> leaders, boolean canCreateRoot) {
    public record Option(Long value, String label) {
    }
}
