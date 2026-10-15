package org.lbl.intranet.operation.compare.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MockResponseComparisonRequest(
        @NotNull(message = "请选择交易") Long transactionId,
        @NotBlank(message = "请选择环境 A") @Size(max = 64) String environmentAId,
        @NotBlank(message = "请选择环境 B") @Size(max = 64) String environmentBId,
        @NotBlank(message = "请选择 SOAP 版本") @Pattern(regexp = "1\\.1|1\\.2", message = "SOAP 版本无效") String soapVersion,
        @NotBlank(message = "请选择环境 A 字段定义") @Pattern(regexp = "DRAFT|VERSION") String bindingTypeA,
        Long transactionVersionIdA,
        Long testCaseIdA,
        @NotBlank(message = "请选择环境 B 字段定义") @Pattern(regexp = "DRAFT|VERSION") String bindingTypeB,
        Long transactionVersionIdB,
        Long testCaseIdB,
        @NotBlank(message = "环境 A 请求报文不能为空") @Size(max = 1_000_000, message = "环境 A 请求报文过大") String requestXmlA,
        @NotBlank(message = "环境 B 请求报文不能为空") @Size(max = 1_000_000, message = "环境 B 请求报文过大") String requestXmlB
) {
}
