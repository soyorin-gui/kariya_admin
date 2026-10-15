package org.lbl.intranet.operation.testcase.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.lbl.intranet.operation.testcase.entity.OperationTestCaseEntity;
import org.lbl.intranet.operation.testcase.mapper.OperationTestCaseMapper;
import org.lbl.intranet.operation.testcase.request.OperationTestCaseRequest;
import org.lbl.intranet.transaction.entity.TransactionAssetEntity;
import org.lbl.intranet.transaction.mapper.TransactionAssetMapper;
import org.lbl.intranet.transaction.version.entity.TransactionVersionEntity;
import org.lbl.intranet.transaction.version.mapper.TransactionVersionMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OperationTestCaseServiceTest {
    private final OperationTestCaseMapper cases = mock(OperationTestCaseMapper.class);
    private final TransactionAssetMapper transactions = mock(TransactionAssetMapper.class);
    private final TransactionVersionMapper versions = mock(TransactionVersionMapper.class);
    private final OperationTestCaseService service = new OperationTestCaseService(cases, transactions, versions, new ObjectMapper());

    @Test
    void createsDraftCaseWithNormalizedJson() {
        when(transactions.selectById(7L)).thenReturn(new TransactionAssetEntity());
        when(cases.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> { ((OperationTestCaseEntity) invocation.getArgument(0)).setId(12L); return 1; })
                .when(cases).insert(any(OperationTestCaseEntity.class));

        var result = service.create(new OperationTestCaseRequest(7L, "DRAFT", null, " 正常查询 ", null, "{\"page\": 1}"));

        assertEquals(12L, result.id());
        assertEquals("正常查询", result.caseName());
        assertEquals("{\"page\":1}", result.requestPayload());
    }

    @Test
    void rejectsVersionFromAnotherTransaction() {
        when(transactions.selectById(7L)).thenReturn(new TransactionAssetEntity());
        TransactionVersionEntity version = new TransactionVersionEntity();
        version.setTransactionId(8L);
        when(versions.selectById(3L)).thenReturn(version);

        assertThrows(RuntimeException.class, () -> service.create(
                new OperationTestCaseRequest(7L, "VERSION", 3L, "案例", null, "{}")));
    }
}
