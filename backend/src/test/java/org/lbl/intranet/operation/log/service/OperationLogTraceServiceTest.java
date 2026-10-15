package org.lbl.intranet.operation.log.service;

import org.junit.jupiter.api.Test;
import org.lbl.intranet.operation.log.request.HistoricalLogQueryRequest;
import org.lbl.intranet.operation.log.request.RealtimeLogQueryRequest;
import org.lbl.intranet.operation.log.source.MockOperationLogSource;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OperationLogTraceServiceTest {
    private final MockOperationLogSource source = new MockOperationLogSource();
    private final OperationLogTraceService service = new OperationLogTraceService(List.of(source), List.of(source));

    @Test
    void queriesAndDownloadsFictionalHistoricalLog() throws IOException {
        var documents = service.history(new HistoricalLogQueryRequest("external-demo", LocalDate.of(2026, 10, 14), "FLOW-001"));

        assertEquals(1, documents.size());
        assertTrue(documents.get(0).content().contains("FLOW-001"));
        var download = service.download("external-demo", documents.get(0).fileId());
        try (var input = download.content()) {
            assertTrue(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).contains("FLOW-001"));
        }
    }

    @Test
    void queriesFictionalRealtimeLog() {
        var result = service.realtime(new RealtimeLogQueryRequest("external-demo", "FLOW-002"));

        assertEquals("demo-transaction-app", result.applicationName());
        assertTrue(result.content().contains("FLOW-002"));
    }
}
