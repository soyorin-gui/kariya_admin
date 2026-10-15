package org.lbl.intranet.operation.invoke.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MockTransactionInvocationRequest(
        @NotNull(message = "请选择交易") Long transactionId,
        @NotBlank(message = "请输入目标容器地址") @Size(max = 255, message = "目标容器地址不能超过 255 个字符") String targetAddress,
        @NotBlank(message = "请选择 SOAP 版本") @Pattern(regexp = "1\\.1|1\\.2", message = "SOAP 版本无效") String soapVersion
) {
}
