package org.lbl.intranet.operation.log.service;

import org.lbl.common.exception.BusinessException;
import org.lbl.intranet.operation.log.model.CurrentLogDocument;
import org.lbl.intranet.operation.log.model.FlowLogContext;
import org.lbl.intranet.operation.log.model.FlowLogFile;
import org.lbl.intranet.operation.log.model.LogDownload;
import org.lbl.intranet.operation.log.model.LogFileType;
import org.lbl.intranet.operation.log.request.FlowLogContextRequest;
import org.lbl.intranet.operation.log.source.HistoricalLogSource;
import org.lbl.intranet.operation.log.source.RealtimeLogSource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class OperationLogTraceService {
    private final List<HistoricalLogSource> historicalSources;
    private final List<RealtimeLogSource> realtimeSources;

    public OperationLogTraceService(List<HistoricalLogSource> historicalSources, List<RealtimeLogSource> realtimeSources) {
        this.historicalSources = List.copyOf(historicalSources);
        this.realtimeSources = List.copyOf(realtimeSources);
    }

    public FlowLogContext context(FlowLogContextRequest request) {
        var flow = request.flow();
        if (flow.queryDate() == null || flow.globalFlowId() == null || flow.globalFlowId().isBlank()) {
            throw new BusinessException("流水信息不完整");
        }
        var historicalDocuments = historical(request.environmentId()).query(request.environmentId(), flow.queryDate(), flow.globalFlowId().trim());
        CurrentLogDocument currentDocument = realtime(request.environmentId()).query(request.environmentId(), flow.globalFlowId().trim());
        List<FlowLogFile> files = new ArrayList<>();
        StringBuilder content = new StringBuilder();
        boolean truncated = false;
        for (var document : historicalDocuments) {
            files.add(new FlowLogFile(document.fileId(), document.fileName(), LogFileType.ROLLED, document.fileSize(), document.lastModified(), document.truncated()));
            appendContent(content, document.fileName(), document.content());
            truncated |= document.truncated();
        }
        files.add(new FlowLogFile(currentDocument.fileId(), currentDocument.fileName(), LogFileType.CURRENT, currentDocument.fileSize(), currentDocument.lastModified(), currentDocument.truncated()));
        appendContent(content, currentDocument.fileName(), currentDocument.content());
        truncated |= currentDocument.truncated();
        List<String> warnings = truncated ? List.of("日志内容已按数据源限制截断") : List.of();
        return new FlowLogContext(flow, content.toString(), List.copyOf(files), truncated, warnings);
    }

    public LogDownload download(String environmentId, LogFileType fileType, String fileId) {
        if (environmentId == null || environmentId.isBlank() || fileId == null || fileId.isBlank()) throw new BusinessException("日志下载参数不完整");
        return switch (fileType) {
            case ROLLED -> historical(environmentId).downloadHistorical(environmentId, fileId);
            case CURRENT -> realtime(environmentId).downloadRealTime(environmentId, fileId);
        };
    }

    private void appendContent(StringBuilder content, String fileName, String fileContent) {
        if (!content.isEmpty()) content.append("\n\n");
        content.append("===== ").append(fileName).append(" =====\n").append(fileContent);
    }

    private HistoricalLogSource historical(String environmentId) {
        return historicalSources.stream()
                .filter(source -> source.environments().stream().anyMatch(environment -> environment.id().equals(environmentId)))
                .findFirst().orElseThrow(() -> new BusinessException("运行环境不存在或尚未配置历史日志数据源"));
    }

    private RealtimeLogSource realtime(String environmentId) {
        return realtimeSources.stream()
                .filter(source -> source.environments().stream().anyMatch(environment -> environment.id().equals(environmentId)))
                .findFirst().orElseThrow(() -> new BusinessException("运行环境不存在或尚未配置实时日志数据源"));
    }
}
