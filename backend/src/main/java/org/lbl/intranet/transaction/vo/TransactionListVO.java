package org.lbl.intranet.transaction.vo;
import java.time.LocalDateTime;
public record TransactionListVO(Long id, String transactionName, String transactionCode, Integer status, String esfServiceName, String esfServiceOperationName, String label, String businessContact, LocalDateTime updatedTime) { }
