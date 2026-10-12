package org.lbl.intranet.requirement.request;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

/** 通用主表字段。各类型明细由后续分步骤表单保存，避免将表单内容塞入通用 content 字段。 */
public record RequirementRequest(
        @NotBlank(message = "请输入 OA 联系单编号") @Size(max = 50) String oaNo,
        @NotNull(message = "请选择需求类型") @Min(1) @Max(3) Integer requirementType,
        @NotBlank(message = "请输入需求名称") @Size(max = 255) String title,
        @Size(max = 1000) String description,
        LocalDate expectedOnlineDate) { }
