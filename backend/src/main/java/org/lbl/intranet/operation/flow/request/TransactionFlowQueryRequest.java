package org.lbl.intranet.operation.flow.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record TransactionFlowQueryRequest(
        @NotBlank(message = "运行环境不能为空") String environmentId,
        @NotNull(message = "查询日期不能为空") @JsonFormat(pattern = "yyyy-MM-dd") LocalDate queryDate,
        @Size(max = 128, message = "调用方 ID 最多 128 个字符") String callerId,
        @Size(max = 256, message = "IP-服务地址最多 256 个字符") String serviceAddress,
        @Size(max = 64, message = "交易码最多 64 个字符") String transactionCode,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
        @Pattern(regexp = "Y|N|E", message = "响应状态只能是 Y、N 或 E") String responseStatus,
        @Size(max = 64, message = "响应码最多 64 个字符") String responseCode,
        @Size(max = 128, message = "全局流水号最多 128 个字符") String globalFlowId,
        @Min(value = 1, message = "页码必须大于 0") long pageNum,
        @Min(value = 1, message = "每页条数必须大于 0") @Max(value = 100, message = "每页最多 100 条") long pageSize
) { }
