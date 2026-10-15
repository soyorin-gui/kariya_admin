package org.lbl.intranet.operation.log.service;

import org.junit.jupiter.api.Test;
import org.lbl.intranet.operation.flow.model.TransactionFlowRecord;
import org.lbl.intranet.operation.log.model.LogFileType;
import org.lbl.intranet.operation.log.request.FlowLogContextRequest;
import org.lbl.intranet.operation.log.source.MockOperationLogSource;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OperationLogTraceServiceTest {
    private final MockOperationLogSource source = new MockOperationLogSource();
    private final OperationLogTraceService service = new OperationLogTraceService(List.of(source), List.of(source));

    @Test
    void queriesContextAndDownloadsBothLogTypes() throws IOException {
        var flow = new TransactionFlowRecord("caller", "127.0.0.1", "TX-001", LocalDateTime.of(2026, 10, 14, 9, 0), LocalDateTime.of(2026, 10, 14, 9, 0, 1), BigDecimal.ONE, "Y", "0000", LocalDate.of(2026, 10, 14), "FLOW-001");
        var context = service.context(new FlowLogContextRequest("external-demo", flow));

        assertEquals(2, context.files().size());
        assertTrue(context.content().contains("FLOW-001"));
        var historical = context.files().stream().filter(file -> file.fileType() == LogFileType.ROLLED).findFirst().orElseThrow();
        var download = service.download("external-demo", historical.fileType(), historical.fileId());
        try (var input = download.content()) {
            assertTrue(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).contains("FLOW-001"));
        }
        var current = context.files().stream().filter(file -> file.fileType() == LogFileType.CURRENT).findFirst().orElseThrow();
        var currentDownload = service.download("external-demo", current.fileType(), current.fileId());
        try (var input = currentDownload.content()) {
            assertTrue(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).contains("FLOW-001"));
        }
    }
}
