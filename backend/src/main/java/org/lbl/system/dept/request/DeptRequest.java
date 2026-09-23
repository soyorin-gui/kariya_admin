package org.lbl.system.dept.request;

import jakarta.validation.constraints.*;

/**
 * 新增/修改部门。
 * <p>
 * deptCode 的格式约束同样原本只在前端（DeptDialog 的正则）。另外 sortOrder 前端是
 * InputNumber min=0，后端没有下限，直接调接口可以写入负数——排序为负会让这条记录
 * 意外跑到最前面，属于"看起来能用但数据已经错了"的静默问题。
 */
public record DeptRequest(
        // 不用 @Positive：本系统用 0 表示"根部门"（前端清空上级部门时提交的就是 0，
        // apply() 也把 null 归一成 0）。加上 @Positive 会把"建根部门"直接判为非法请求。
        // 是不是根部门、以及有没有权限动它，由 DeptService.apply/requireCreateDept 判断。
        @Min(value = 0, message = "上级部门无效")
        Long parentId,
        @NotBlank(message = "请输入部门名称")
        @Size(max = 80, message = "部门名称最长 80 个字符")
        String deptName,
        @NotBlank(message = "请输入部门编码")
        @Size(max = 80, message = "部门编码最长 80 个字符")
        @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_-]*$", message = "部门编码需以字母开头，只能包含字母、数字、下划线和短横线")
        String deptCode,
        @Positive(message = "负责人无效")
        Long leaderUserId,
        @NotNull(message = "请输入排序值")
        @Min(value = 0, message = "排序值不能为负数")
        @Max(value = 9999, message = "排序值过大")
        Integer sortOrder,
        @NotNull(message = "请选择状态")
        @Min(value = 0, message = "状态无效")
        @Max(value = 1, message = "状态无效")
        Integer status
) {
}
