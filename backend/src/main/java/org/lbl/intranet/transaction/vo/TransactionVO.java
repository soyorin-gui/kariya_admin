package org.lbl.intranet.transaction.vo;
import java.time.LocalDateTime;
public record TransactionVO(Long id, String transactionName, String transactionCode, String description, Integer status, String esfServiceName, String esfServiceOperationId, String esfServiceAddress, String esfServiceOperationName, String label, String businessContact, String dataTimeliness, Integer printFileMode, String sortRule, String dataValidationScope, String queryScope, String fileGenerationScope, String fileNameRule, Long createdBy, LocalDateTime createdTime, Long updatedBy, LocalDateTime updatedTime) { }
