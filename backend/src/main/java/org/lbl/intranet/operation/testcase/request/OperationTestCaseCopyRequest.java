package org.lbl.intranet.operation.testcase.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OperationTestCaseCopyRequest(
        @NotBlank(message = "请选择目标字段定义")
        @Pattern(regexp = "DRAFT|VERSION", message = "目标字段定义无效") String bindingType,
        Long transactionVersionId,
        @Size(max = 128, message = "案例名称不能超过128个字符") String caseName
) { }
