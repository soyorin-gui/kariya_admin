package org.lbl.intranet.operation.compare.service;

import org.junit.jupiter.api.Test;
import org.lbl.intranet.operation.compare.request.MockResponseComparisonRequest;
import org.lbl.intranet.transaction.service.TransactionAssetService;
import org.lbl.intranet.transaction.vo.TransactionVO;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MockResponseComparisonServiceTest {
    private final TransactionAssetService transactions = mock(TransactionAssetService.class);
    private final MockResponseComparisonService service = new MockResponseComparisonService(transactions);

    @Test
    void returnsTwoMockResponsesAndFieldDifferences() {
        when(transactions.detail(9L)).thenReturn(transaction());
        var result = service.compare(new MockResponseComparisonRequest(
                9L, "external-demo-a", "external-demo-b", "1.1",
                "DRAFT", null, null, "VERSION", 2L, 3L, "<request-a/>", "<request-b/>"
        ));

        assertEquals("TX009", result.transactionCode());
        assertTrue(result.environmentA().success());
        assertTrue(result.environmentB().success());
        assertEquals("<request-a/>", result.environmentA().requestXml());
        assertEquals("<request-b/>", result.environmentB().requestXml());
        assertEquals(7, result.fieldComparisons().size());
        assertTrue(result.fieldComparisons().stream().anyMatch(item -> "ONLY_A".equals(item.differenceType())));
        assertTrue(result.fieldComparisons().stream().anyMatch(item -> "ONLY_B".equals(item.differenceType())));
    }

    @Test
    void rejectsSameEnvironment() {
        assertThrows(RuntimeException.class, () -> service.compare(new MockResponseComparisonRequest(
                9L, "external-demo-a", "external-demo-a", "1.1",
                "DRAFT", null, null, "DRAFT", null, null, "<request/>", "<request/>"
        )));
    }

    private TransactionVO transaction() {
        return new TransactionVO(
                9L, "交易查询", "TX009", null, 1,
                "demo", "operation-9", "internal.example", "queryTrade",
                null, null, null, 0, null, null, null, null, null,
                1L, LocalDateTime.now(), 1L, LocalDateTime.now()
        );
    }
}
