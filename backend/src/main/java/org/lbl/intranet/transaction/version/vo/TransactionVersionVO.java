package org.lbl.intranet.transaction.version.vo;

import java.time.LocalDateTime;

public record TransactionVersionVO(Long id, Long transactionId, Long requirementId, Integer versionNo,
                                   Integer activeFlag, String transactionName, String transactionCode,
                                   Long activatedBy, LocalDateTime activatedTime) { }
