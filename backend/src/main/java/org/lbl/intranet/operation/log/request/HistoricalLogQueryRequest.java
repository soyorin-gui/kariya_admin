package org.lbl.intranet.operation.log.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record HistoricalLogQueryRequest(
        @NotBlank(message = "运行环境不能为空") String environmentId,
        @NotNull(message = "查询日期不能为空") @JsonFormat(pattern = "yyyy-MM-dd") LocalDate queryDate,
        @NotBlank(message = "全局流水号不能为空") @Size(max = 128, message = "全局流水号最多 128 个字符") String globalFlowId
) { }
