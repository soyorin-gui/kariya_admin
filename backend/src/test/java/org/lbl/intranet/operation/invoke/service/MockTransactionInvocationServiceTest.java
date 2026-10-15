package org.lbl.intranet.operation.invoke.service;

import org.junit.jupiter.api.Test;
import org.lbl.intranet.operation.invoke.request.MockTransactionInvocationRequest;
import org.lbl.intranet.transaction.service.TransactionAssetService;
import org.lbl.intranet.transaction.vo.TransactionVO;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MockTransactionInvocationServiceTest {
    @Test
    void returnsStaticSoapResultWithoutCallingTargetAddress() {
        TransactionAssetService transactions = mock(TransactionAssetService.class);
        when(transactions.detail(7L)).thenReturn(new TransactionVO(
                7L, "交易明细查询", "TX007", null, 1,
                "demo", "operation-7", "internal.example", "queryDetail",
                null, null, null, 0, null, null, null, null, null,
                1L, LocalDateTime.now(), 1L, LocalDateTime.now()
        ));
        var service = new MockTransactionInvocationService(transactions);

        var result = service.invoke(new MockTransactionInvocationRequest(7L, "10.0.0.7:8080", "1.2"));

        assertTrue(result.success());
        assertEquals("Y", result.responseStatus());
        assertEquals("MOCK_SUCCESS", result.responseCode());
        assertTrue(result.requestXml().contains("TX007"));
        assertTrue(result.responseXml().contains(result.globalFlowId()));
    }
}
