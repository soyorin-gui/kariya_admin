package org.lbl.intranet.operation.log.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.lbl.common.result.Result;
import org.lbl.intranet.operation.log.model.FlowLogContext;
import org.lbl.intranet.operation.log.model.LogDownload;
import org.lbl.intranet.operation.log.model.LogFileType;
import org.lbl.intranet.operation.log.request.FlowLogContextRequest;
import org.lbl.intranet.operation.log.service.OperationLogTraceService;
import org.lbl.system.log.aspect.OperationLog;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
@RestController
@RequestMapping("/api/intranet/operation/logs")
public class OperationLogTraceController {
    private final OperationLogTraceService service;

    public OperationLogTraceController(OperationLogTraceService service) {
        this.service = service;
    }

    @PostMapping("/context")
    @PreAuthorize("hasAuthority('intranet:operation:log:list')")
    Result<FlowLogContext> context(@Valid @RequestBody FlowLogContextRequest request) {
        return Result.ok(service.context(request));
    }

    @GetMapping("/files/{fileType}/{fileId}/download")
    @PreAuthorize("hasAuthority('intranet:operation:log:download')")
    @OperationLog(module = "交易运行中心", action = "下载交易日志", targetType = "OPERATION_LOG_FILE", targetId = "#fileId")
    void download(@PathVariable LogFileType fileType, @PathVariable String fileId, @RequestParam String environmentId, HttpServletResponse response) throws IOException {
        LogDownload file = service.download(environmentId, fileType, fileId);
        response.setContentType(file.contentType());
        response.setContentLengthLong(file.contentLength());
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8).replace("+", "%20"));
        try (var input = file.content()) {
            input.transferTo(response.getOutputStream());
        }
    }

}
