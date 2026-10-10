package org.lbl.intranet.transaction.request;

import jakarta.validation.constraints.*;

public record TransactionRequest(
        @NotBlank(message = "请输入交易名称") @Size(max = 128, message = "交易名称最长 128 个字符") String transactionName,
        @NotBlank(message = "请输入交易编码") @Size(max = 64, message = "交易编码最长 64 个字符") String transactionCode,
        @Size(max = 1000, message = "交易说明最长 1000 个字符") String description,
        @NotNull(message = "请选择交易状态") @Min(0) @Max(3) Integer status,
        @NotBlank(message = "请输入 ESF 服务名") @Size(max = 64) String esfServiceName,
        @NotBlank(message = "请输入 ESF 服务操作 ID") @Size(max = 64) String esfServiceOperationId,
        @NotBlank(message = "请输入 ESF 服务地址") @Size(max = 64) String esfServiceAddress,
        @NotBlank(message = "请输入 ESF 服务操作名称") @Size(max = 64) String esfServiceOperationName,
        @Size(max = 10) String label, @Size(max = 32) String businessContact, @Size(max = 50) String dataTimeliness,
        @NotNull(message = "请选择打印文件方式") @Min(0) @Max(2) Integer printFileMode,
        @Size(max = 500) String sortRule, @Size(max = 500) String dataValidationScope, @Size(max = 100) String queryScope,
        @Size(max = 500) String fileGenerationScope, @Size(max = 255) String fileNameRule) { }
