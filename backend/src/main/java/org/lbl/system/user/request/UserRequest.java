package org.lbl.system.user.request;

import jakarta.validation.constraints.*;
import java.util.List;

/**
 * 新增/修改用户的请求体。
 * <p>
 * 这里每个 {@code @Size} 都对齐 sys_user 的列宽（username 64、real_name 64、phone 32、email 128）。
 * 以前只有 {@code @NotBlank}，超长输入会一路走到 INSERT，然后被 MySQL 拒掉、冒成
 * 500「系统繁忙，请稍后重试」——用户以为服务坏了，其实只是自己多打了几位。
 * 校验放在这一层，才能变成带字段名的 400。
 *
 * @param username 登录账号。只允许字母/数字/下划线/点/短横线：既保证和 MySQL 默认
 *                 ci 排序下的唯一索引语义一致，也避免出现首尾空格、控制字符这类
 *                 靠肉眼分辨不出来的账号名。
 * @param phone    手机号。只做"字符范围 + 长度"校验，不做严格的 11 位中国大陆号码格式：
 *                 这属于业务规则（可能有座机、分机、境外号码），收紧到 1[3-9]\d{9}
 *                 会误伤真实数据，不该由通用校验层决定。
 */
public record UserRequest(@NotBlank(message = "请输入用户名")
                          @Size(max = 64, message = "用户名最长 64 个字符")
                          @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "用户名只能包含字母、数字、下划线、点和短横线")
                          String username,
                          @NotBlank(message = "请输入姓名")
                          @Size(max = 64, message = "姓名最长 64 个字符")
                          String realName,
                          @NotBlank(message = "请输入手机号")
                          @Size(max = 32, message = "手机号最长 32 个字符")
                          @Pattern(regexp = "^\\+?[0-9 ()-]{5,32}$", message = "手机号格式不正确")
                          String phone,
                          @Size(max = 128, message = "邮箱最长 128 个字符")
                          @Email(message = "邮箱格式不正确")
                          String email,
                          @NotNull(message = "请选择所属部门")
                          @Positive(message = "所属部门无效")
                          Long deptId,
                          @NotEmpty(message = "请至少选择一个角色")
                          @Size(max = 50, message = "单个用户的角色数量过多")
                          List<@NotNull(message = "角色无效") @Positive(message = "角色无效") Long> roleIds,
                          @NotNull(message = "请选择状态")
                          @Min(value = 0, message = "状态无效")
                          @Max(value = 1, message = "状态无效")
                          Integer status) {
}
