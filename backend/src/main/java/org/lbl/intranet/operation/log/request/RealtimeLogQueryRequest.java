package org.lbl.intranet.operation.log.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RealtimeLogQueryRequest(
        @NotBlank(message = "运行环境不能为空") String environmentId,
        @NotBlank(message = "全局流水号不能为空") @Size(max = 128, message = "全局流水号最多 128 个字符") String globalFlowId
) { }
