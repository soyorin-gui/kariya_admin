package org.lbl.intranet.operation.log.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.lbl.common.result.Result;
import org.lbl.intranet.operation.log.model.HistoricalLogDocument;
import org.lbl.intranet.operation.log.model.LogDownload;
import org.lbl.intranet.operation.log.model.LogEnvironment;
import org.lbl.intranet.operation.log.model.RealtimeLogResult;
import org.lbl.intranet.operation.log.request.HistoricalLogQueryRequest;
import org.lbl.intranet.operation.log.request.RealtimeLogQueryRequest;
import org.lbl.intranet.operation.log.service.OperationLogTraceService;
import org.lbl.system.log.aspect.OperationLog;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/intranet/operation/logs")
public class OperationLogTraceController {
    private final OperationLogTraceService service;

    public OperationLogTraceController(OperationLogTraceService service) {
        this.service = service;
    }

    @GetMapping("/environments")
    @PreAuthorize("hasAuthority('intranet:operation:log:list')")
    Result<List<LogEnvironment>> environments() {
        return Result.ok(service.environments());
    }

    @PostMapping("/history/query")
    @PreAuthorize("hasAuthority('intranet:operation:log:list')")
    Result<List<HistoricalLogDocument>> history(@Valid @RequestBody HistoricalLogQueryRequest request) {
        return Result.ok(service.history(request));
    }

    @GetMapping("/history/files/{fileId}/download")
    @PreAuthorize("hasAuthority('intranet:operation:log:download')")
    @OperationLog(module = "交易运行中心", action = "下载历史日志", targetType = "OPERATION_LOG_FILE", targetId = "#fileId")
    void download(@PathVariable String fileId, @RequestParam String environmentId, HttpServletResponse response) throws IOException {
        LogDownload file = service.download(environmentId, fileId);
        response.setContentType(file.contentType());
        response.setContentLengthLong(file.contentLength());
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8).replace("+", "%20"));
        try (var input = file.content()) {
            input.transferTo(response.getOutputStream());
        }
    }

    @PostMapping("/realtime/query")
    @PreAuthorize("hasAuthority('intranet:operation:log:list')")
    Result<RealtimeLogResult> realtime(@Valid @RequestBody RealtimeLogQueryRequest request) {
        return Result.ok(service.realtime(request));
    }
}
