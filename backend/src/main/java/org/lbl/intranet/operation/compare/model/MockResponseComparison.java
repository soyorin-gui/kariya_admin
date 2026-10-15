package org.lbl.intranet.operation.compare.model;

import java.util.List;

public record MockResponseComparison(
        Long transactionId,
        String transactionCode,
        String transactionName,
        EnvironmentInvocationResult environmentA,
        EnvironmentInvocationResult environmentB,
        List<ResponseFieldDifference> fieldComparisons
) {
}
