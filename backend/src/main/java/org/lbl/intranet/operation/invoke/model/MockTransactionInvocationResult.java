package org.lbl.intranet.operation.invoke.model;

public record MockTransactionInvocationResult(
        Long transactionId,
        String transactionCode,
        String transactionName,
        String targetAddress,
        boolean success,
        String responseStatus,
        String responseCode,
        String responseMessage,
        String globalFlowId,
        long elapsedTime,
        String requestXml,
        String responseXml
) {
}
