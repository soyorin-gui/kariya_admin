package org.lbl.intranet.operation.invoke.service;

import org.lbl.intranet.operation.invoke.model.MockTransactionInvocationResult;
import org.lbl.intranet.operation.invoke.request.MockTransactionInvocationRequest;
import org.lbl.intranet.transaction.service.TransactionAssetService;
import org.lbl.intranet.transaction.vo.TransactionVO;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class MockTransactionInvocationService {
    private static final DateTimeFormatter FLOW_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private final TransactionAssetService transactions;

    public MockTransactionInvocationService(TransactionAssetService transactions) {
        this.transactions = transactions;
    }

    public MockTransactionInvocationResult invoke(MockTransactionInvocationRequest request) {
        TransactionVO transaction = transactions.detail(request.transactionId());
        String soapNamespace = "1.2".equals(request.soapVersion())
                ? "http://www.w3.org/2003/05/soap-envelope"
                : "http://schemas.xmlsoap.org/soap/envelope/";
        String operation = xmlName(transaction.esfServiceOperationName() == null || transaction.esfServiceOperationName().isBlank()
                ? transaction.transactionCode()
                : transaction.esfServiceOperationName());
        String globalFlowId = "MOCK" + FLOW_TIME.format(LocalDateTime.now()) + String.format("%06d", transaction.id() % 1_000_000);
        long elapsedTime = 60 + transaction.id() % 240;
        String requestXml = requestXml(soapNamespace, operation, transaction.transactionCode());
        String responseXml = responseXml(soapNamespace, operation, globalFlowId, elapsedTime);
        return new MockTransactionInvocationResult(
                transaction.id(), transaction.transactionCode(), transaction.transactionName(), request.targetAddress().trim(),
                true, "Y", "MOCK_SUCCESS", "静态模拟调用成功，未向交易容器发送请求",
                globalFlowId, elapsedTime, requestXml, responseXml
        );
    }

    private String requestXml(String soapNamespace, String operation, String transactionCode) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <soapenv:Envelope xmlns:soapenv="%s" xmlns:mock="urn:lbl:operation:mock">
                  <soapenv:Header/>
                  <soapenv:Body>
                    <%s>
                      <mock:transactionCode>%s</mock:transactionCode>
                    </%s>
                  </soapenv:Body>
                </soapenv:Envelope>""".formatted(soapNamespace, operation, xmlEscape(transactionCode), operation);
    }

    private String responseXml(String soapNamespace, String operation, String globalFlowId, long elapsedTime) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <soapenv:Envelope xmlns:soapenv="%s" xmlns:mock="urn:lbl:operation:mock">
                  <soapenv:Header/>
                  <soapenv:Body>
                    <%sResponse>
                      <mock:responseStatus>Y</mock:responseStatus>
                      <mock:responseCode>MOCK_SUCCESS</mock:responseCode>
                      <mock:responseMessage>静态模拟调用成功，未向交易容器发送请求</mock:responseMessage>
                      <mock:globalFlowId>%s</mock:globalFlowId>
                      <mock:elapsedTime>%d</mock:elapsedTime>
                    </%sResponse>
                  </soapenv:Body>
                </soapenv:Envelope>""".formatted(soapNamespace, operation, globalFlowId, elapsedTime, operation);
    }

    private String xmlName(String value) {
        String normalized = value == null ? "operation" : value.replaceAll("[^A-Za-z0-9_.:-]", "_");
        return normalized.isBlank() ? "operation" : normalized;
    }

    private String xmlEscape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
