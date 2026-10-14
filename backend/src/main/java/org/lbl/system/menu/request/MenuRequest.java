package org.lbl.system.menu.request;

import jakarta.validation.constraints.*;

/**
 * 新增/修改菜单。
 * <p>
 * 长度上限全部对齐 sys_menu 的列宽；以前完全没有长度约束，超长值会一路冒成 500。
 * permissionCode 只做"字符范围"约束（小写字母/数字/冒号/点/下划线/短横线），
 * 不限定成 system:xxx:yyy 的具体形状：菜单是可在后台配置的，硬编码前缀会让
 * 将来新增模块时必须改后端代码才能配得上。
 */
public record MenuRequest(
        // 0 表示顶级菜单（apply() 把 null 归一成 0），因此不能用 @Positive。
        @Min(value = 0, message = "上级菜单无效")
        Long parentId,
        @NotBlank(message = "请输入菜单名称")
        @Size(max = 80, message = "菜单名称最长 80 个字符")
        String menuName,
        @NotBlank(message = "请选择菜单类型")
        @Pattern(regexp = "(?i)^(DIR|MENU|BUTTON)$", message = "菜单类型必须是目录、菜单或按钮")
        String menuType,
        @Size(max = 80, message = "路由名称最长 80 个字符")
        String routeName,
        @Size(max = 160, message = "路由地址最长 160 个字符")
        String routePath,
        @Size(max = 160, message = "前端组件最长 160 个字符")
        String component,
        @Size(max = 120, message = "权限标识最长 120 个字符")
        @Pattern(regexp = "^[a-z0-9_:.-]*$", message = "权限标识只能包含小写字母、数字、冒号、下划线、点和短横线")
        String permissionCode,
        @Size(max = 80, message = "图标名称最长 80 个字符")
        String icon,
        @NotNull(message = "请输入排序值")
        @Min(value = 0, message = "排序值不能为负数")
        @Max(value = 9999, message = "排序值过大")
        Integer sortOrder,
        @NotNull(message = "请选择是否显示")
        @Min(value = 0, message = "可见性无效")
        @Max(value = 1, message = "可见性无效")
        Integer visible,
        @NotNull(message = "请选择状态")
        @Min(value = 0, message = "状态无效")
        @Max(value = 1, message = "状态无效")
        Integer status,
        @NotNull(message = "请选择是否缓存")
        @Min(value = 0, message = "页面缓存取值无效")
        @Max(value = 1, message = "页面缓存取值无效")
        Integer keepAlive
) {
}
