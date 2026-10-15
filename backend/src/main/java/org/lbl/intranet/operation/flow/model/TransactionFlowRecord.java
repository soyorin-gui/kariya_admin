package org.lbl.intranet.operation.flow.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record TransactionFlowRecord(
        String callerId,
        String serviceAddress,
        String transactionCode,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
        BigDecimal elapsedTime,
        String responseStatus,
        String responseCode,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate queryDate,
        String globalFlowId
) { }
