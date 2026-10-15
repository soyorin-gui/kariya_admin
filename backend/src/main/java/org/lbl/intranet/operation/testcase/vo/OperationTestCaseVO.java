package org.lbl.intranet.operation.testcase.vo;

import java.time.LocalDateTime;

public record OperationTestCaseVO(
        Long id,
        Long transactionId,
        String bindingType,
        Long transactionVersionId,
        String caseName,
        String description,
        String requestPayload,
        Long createdBy,
        LocalDateTime createdTime,
        Long updatedBy,
        LocalDateTime updatedTime
) { }
