package org.lbl.intranet.operation.compare.service;

import org.lbl.common.exception.BusinessException;
import org.lbl.intranet.operation.compare.model.ComparisonEnvironment;
import org.lbl.intranet.operation.compare.model.EnvironmentInvocationResult;
import org.lbl.intranet.operation.compare.model.MockResponseComparison;
import org.lbl.intranet.operation.compare.model.ResponseFieldDifference;
import org.lbl.intranet.operation.compare.request.MockResponseComparisonRequest;
import org.lbl.intranet.transaction.service.TransactionAssetService;
import org.lbl.intranet.transaction.vo.TransactionVO;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class MockResponseComparisonService {
    private static final DateTimeFormatter FLOW_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final List<ComparisonEnvironment> ENVIRONMENTS = List.of(
            new ComparisonEnvironment("external-demo-a", "演示环境 A"),
            new ComparisonEnvironment("external-demo-b", "演示环境 B"),
            new ComparisonEnvironment("external-demo-c", "演示环境 C")
    );

    private final TransactionAssetService transactions;

    public MockResponseComparisonService(TransactionAssetService transactions) {
        this.transactions = transactions;
    }

    public List<ComparisonEnvironment> environments() {
        return ENVIRONMENTS;
    }

    public MockResponseComparison compare(MockResponseComparisonRequest request) {
        if (request.environmentAId().equals(request.environmentBId())) throw new BusinessException("环境 A 和环境 B 不能相同");
        ComparisonEnvironment environmentA = requireEnvironment(request.environmentAId());
        ComparisonEnvironment environmentB = requireEnvironment(request.environmentBId());
        TransactionVO transaction = transactions.detail(request.transactionId());
        String namespace = "1.2".equals(request.soapVersion())
                ? "http://www.w3.org/2003/05/soap-envelope"
                : "http://schemas.xmlsoap.org/soap/envelope/";
        String operation = xmlName(transaction.esfServiceOperationName() == null || transaction.esfServiceOperationName().isBlank()
                ? transaction.transactionCode()
                : transaction.esfServiceOperationName());
        String flowBase = "MOCK" + FLOW_TIME.format(LocalDateTime.now()) + String.format("%06d", transaction.id() % 1_000_000);
        long elapsedA = 96 + transaction.id() % 90;
        long elapsedB = 128 + transaction.id() % 120;
        var resultA = new EnvironmentInvocationResult(
                environmentA.id(), environmentA.name(), true, "Y", "MOCK_SUCCESS", flowBase + "A", elapsedA,
                request.requestXmlA(), responseXml(namespace, operation, flowBase + "A", elapsedA, "100.00", true, false)
        );
        var resultB = new EnvironmentInvocationResult(
                environmentB.id(), environmentB.name(), true, "Y", "MOCK_SUCCESS", flowBase + "B", elapsedB,
                request.requestXmlB(), responseXml(namespace, operation, flowBase + "B", elapsedB, "108.50", false, true)
        );
        String root = "/Envelope/Body/" + operation + "Response/";
        List<ResponseFieldDifference> fieldComparisons = List.of(
                new ResponseFieldDifference(root + "responseStatus", "SAME", "Y", "Y"),
                new ResponseFieldDifference(root + "responseCode", "SAME", "MOCK_SUCCESS", "MOCK_SUCCESS"),
                new ResponseFieldDifference(root + "globalFlowId", "VALUE_DIFFERENT", resultA.globalFlowId(), resultB.globalFlowId()),
                new ResponseFieldDifference(root + "elapsedTime", "VALUE_DIFFERENT", String.valueOf(elapsedA), String.valueOf(elapsedB)),
                new ResponseFieldDifference(root + "balance", "VALUE_DIFFERENT", "100.00", "108.50"),
                new ResponseFieldDifference(root + "legacyFlag", "ONLY_A", "Y", null),
                new ResponseFieldDifference(root + "riskLevel", "ONLY_B", null, "LOW")
        );
        return new MockResponseComparison(transaction.id(), transaction.transactionCode(), transaction.transactionName(), resultA, resultB, fieldComparisons);
    }

    private ComparisonEnvironment requireEnvironment(String id) {
        return ENVIRONMENTS.stream().filter(environment -> environment.id().equals(id)).findFirst()
                .orElseThrow(() -> new BusinessException("对比环境不存在或尚未配置"));
    }

    private String responseXml(String namespace, String operation, String flowId, long elapsedTime, String balance,
                               boolean includeLegacyFlag, boolean includeRiskLevel) {
        String legacyFlag = includeLegacyFlag ? "\n      <mock:legacyFlag>Y</mock:legacyFlag>" : "";
        String riskLevel = includeRiskLevel ? "\n      <mock:riskLevel>LOW</mock:riskLevel>" : "";
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <soapenv:Envelope xmlns:soapenv="%s" xmlns:mock="urn:lbl:operation:mock">
                  <soapenv:Header/>
                  <soapenv:Body>
                    <%sResponse>
                      <mock:responseStatus>Y</mock:responseStatus>
                      <mock:responseCode>MOCK_SUCCESS</mock:responseCode>
                      <mock:globalFlowId>%s</mock:globalFlowId>
                      <mock:elapsedTime>%d</mock:elapsedTime>
                      <mock:balance>%s</mock:balance>%s%s
                    </%sResponse>
                  </soapenv:Body>
                </soapenv:Envelope>""".formatted(namespace, operation, flowId, elapsedTime, balance, legacyFlag, riskLevel, operation);
    }

    private String xmlName(String value) {
        String normalized = value == null ? "operation" : value.replaceAll("[^A-Za-z0-9_.:-]", "_");
        return normalized.isBlank() ? "operation" : normalized;
    }
}
