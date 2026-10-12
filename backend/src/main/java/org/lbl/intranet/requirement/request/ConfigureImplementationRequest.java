package org.lbl.intranet.requirement.request;

import jakarta.validation.constraints.NotNull;

public record ConfigureImplementationRequest(
        @NotNull(message = "请选择交易") Long transactionId,
        @NotNull(message = "请选择开发负责人") Long developerUserId
) { }
