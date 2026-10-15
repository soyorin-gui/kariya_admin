package org.lbl.intranet.operation.log.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.lbl.intranet.operation.flow.model.TransactionFlowRecord;

public record FlowLogContextRequest(
        @NotBlank(message = "运行环境不能为空") String environmentId,
        @NotNull(message = "流水信息不能为空") @Valid TransactionFlowRecord flow
) { }
