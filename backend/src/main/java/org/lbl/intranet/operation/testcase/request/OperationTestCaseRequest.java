package org.lbl.intranet.operation.testcase.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OperationTestCaseRequest(
        @NotNull(message = "请选择交易") Long transactionId,
        @NotBlank(message = "请选择字段定义来源")
        @Pattern(regexp = "DRAFT|VERSION", message = "字段定义来源无效") String bindingType,
        Long transactionVersionId,
        @NotBlank(message = "请输入案例名称") @Size(max = 128, message = "案例名称不能超过128个字符") String caseName,
        @Size(max = 500, message = "案例说明不能超过500个字符") String description,
        @NotBlank(message = "请求参数不能为空") String requestPayload
) { }
