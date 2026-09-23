package org.lbl.system.role.request;

import jakarta.validation.constraints.*;

/**
 * 新增/修改角色。
 * <p>
 * roleCode 的格式约束之前只存在于前端表单（RoleDialog 里的正则），后端只校验了非空。
 * 绕过 UI 直接调接口就能写进任意标识——包括看起来像内置角色的值。虽然
 * super_admin 这类判定还结合了 builtin 字段、不会因此提权，但"角色标识"在本系统里
 * 是被当作稳定配置键使用的（代码里按 role_code 查角色），必须保证后端口径与前端一致。
 */
public record RoleRequest(
        @NotBlank(message = "请输入角色名称")
        @Size(max = 80, message = "角色名称最长 80 个字符")
        String roleName,
        @NotBlank(message = "请输入角色标识")
        @Size(max = 80, message = "角色标识最长 80 个字符")
        @Pattern(regexp = "^[a-z][a-z0-9_:.-]*$", message = "角色标识需以小写字母开头，只能包含小写字母、数字、冒号、下划线、点和短横线")
        String roleCode,
        @NotBlank(message = "请选择数据范围")
        @Pattern(regexp = "^(ALL|DEPT_AND_CHILDREN|DEPT|SELF)$", message = "数据范围必须是 ALL、DEPT_AND_CHILDREN、DEPT 或 SELF")
        String dataScope,
        @NotNull(message = "请选择状态")
        @Min(value = 0, message = "状态无效")
        @Max(value = 1, message = "状态无效")
        Integer status
) {
}
