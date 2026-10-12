package org.lbl.intranet.requirement.request;
import jakarta.validation.constraints.NotNull;
public record AssignTransactionRequest(@NotNull(message = "请选择交易") Long transactionId) { }
