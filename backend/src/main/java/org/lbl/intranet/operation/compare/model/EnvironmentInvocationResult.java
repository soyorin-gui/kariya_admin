package org.lbl.intranet.operation.compare.model;

public record EnvironmentInvocationResult(
        String environmentId,
        String environmentName,
        boolean success,
        String responseStatus,
        String responseCode,
        String globalFlowId,
        long elapsedTime,
        String requestXml,
        String responseXml
) {
}
