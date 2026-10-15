package org.lbl.intranet.operation.log.service;

import org.lbl.common.exception.BusinessException;
import org.lbl.intranet.operation.log.model.HistoricalLogDocument;
import org.lbl.intranet.operation.log.model.LogDownload;
import org.lbl.intranet.operation.log.model.LogEnvironment;
import org.lbl.intranet.operation.log.model.RealtimeLogResult;
import org.lbl.intranet.operation.log.request.HistoricalLogQueryRequest;
import org.lbl.intranet.operation.log.request.RealtimeLogQueryRequest;
import org.lbl.intranet.operation.log.source.HistoricalLogSource;
import org.lbl.intranet.operation.log.source.RealtimeLogSource;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OperationLogTraceService {
    private final List<HistoricalLogSource> historicalSources;
    private final List<RealtimeLogSource> realtimeSources;

    public OperationLogTraceService(List<HistoricalLogSource> historicalSources, List<RealtimeLogSource> realtimeSources) {
        this.historicalSources = List.copyOf(historicalSources);
        this.realtimeSources = List.copyOf(realtimeSources);
    }

    public List<LogEnvironment> environments() {
        Map<String, LogEnvironment> unique = new LinkedHashMap<>();
        historicalSources.stream().flatMap(source -> source.environments().stream()).forEach(item -> unique.putIfAbsent(item.id(), item));
        realtimeSources.stream().flatMap(source -> source.environments().stream()).forEach(item -> unique.putIfAbsent(item.id(), item));
        return unique.values().stream().sorted(Comparator.comparing(LogEnvironment::name)).toList();
    }

    public List<HistoricalLogDocument> history(HistoricalLogQueryRequest request) {
        return historical(request.environmentId()).query(request.environmentId(), request.queryDate(), request.globalFlowId().trim());
    }

    public LogDownload download(String environmentId, String fileId) {
        if (environmentId == null || environmentId.isBlank() || fileId == null || fileId.isBlank()) throw new BusinessException("日志下载参数不完整");
        return historical(environmentId).download(environmentId, fileId);
    }

    public RealtimeLogResult realtime(RealtimeLogQueryRequest request) {
        return realtime(request.environmentId()).query(request.environmentId(), request.globalFlowId().trim());
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
