package org.lbl.intranet.operation.flow.service;

import org.junit.jupiter.api.Test;
import org.lbl.intranet.operation.flow.model.FlowEnvironment;
import org.lbl.intranet.operation.flow.parser.TransactionFlowParser;
import org.lbl.intranet.operation.flow.request.TransactionFlowQueryRequest;
import org.lbl.intranet.operation.flow.source.TransactionFlowSource;
import org.lbl.intranet.operation.flow.source.TransactionFlowSourceRegistry;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransactionFlowQueryServiceTest {
    private final LocalDate date = LocalDate.of(2026, 9, 23);
    private final TransactionFlowSource source = new TransactionFlowSource() {
        @Override public List<FlowEnvironment> environments() { return List.of(new FlowEnvironment("test", "测试环境")); }
        @Override public List<String> responseCodes(String environmentId) { return List.of("OK", "ERROR"); }
        @Override public List<String> read(String environmentId, LocalDate queryDate) {
            return List.of(
                    "CALLER-ABC,address-1,T001,2026-09-23 10:00:00,2026-09-23 10:00:01,1000,Y,OK,2026-09-23,FLOW-1",
                    "CALLER-XYZ,address-2,T002,2026-09-23 11:00:00,2026-09-23 11:00:01,1000,N,ERROR,2026-09-23,FLOW-2",
                    "非法记录");
        }
    };
    private final TransactionFlowQueryService service = new TransactionFlowQueryService(
            new TransactionFlowSourceRegistry(List.of(source)), new TransactionFlowParser());

    @Test
    void filtersCallerFuzzilyAndFlowIdExactlyWhileIgnoringMalformedRecord() {
        var request = new TransactionFlowQueryRequest("test", date, "ABC", null, "T001", null, null, "Y", "OK", "FLOW-1", 1, 10);

        var result = service.query(request);

        assertEquals(1, result.total());
        assertEquals("FLOW-1", result.records().get(0).globalFlowId());
    }

    @Test
    void filtersByStartTimeAndReturnsExactPagedTotal() {
        var request = new TransactionFlowQueryRequest("test", date, null, null, null,
                LocalDateTime.of(2026, 9, 23, 10, 30), null, null, null, null, 1, 1);

        var result = service.query(request);

        assertEquals(1, result.total());
        assertEquals(1, result.records().size());
        assertEquals("FLOW-2", result.records().get(0).globalFlowId());
    }

    @Test
    void rejectsTimeOutsideSelectedDate() {
        var request = new TransactionFlowQueryRequest("test", date, null, null, null,
                LocalDateTime.of(2026, 9, 24, 0, 0), null, null, null, null, 1, 10);

        assertThrows(org.lbl.common.exception.BusinessException.class, () -> service.query(request));
    }
}
