package org.lbl.intranet.operation.log.source;

import org.lbl.common.exception.BusinessException;
import org.lbl.intranet.operation.log.model.HistoricalLogDocument;
import org.lbl.intranet.operation.log.model.LogDownload;
import org.lbl.intranet.operation.log.model.LogEnvironment;
import org.lbl.intranet.operation.log.model.CurrentLogDocument;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 外网开发日志源，不包含任何真实地址、路径、协议或日志。 */
@Component
@Profile({"default", "local"})
public class MockOperationLogSource implements HistoricalLogSource, RealtimeLogSource {
    private static final LogEnvironment ENVIRONMENT = new LogEnvironment("external-demo", "外网模拟环境");
    private final Map<String, StoredFile> historicalDownloads = new ConcurrentHashMap<>();
    private final Map<String, StoredFile> currentDownloads = new ConcurrentHashMap<>();

    @Override
    public List<LogEnvironment> environments() {
        return List.of(ENVIRONMENT);
    }

    @Override
    public List<HistoricalLogDocument> query(String environmentId, LocalDate queryDate, String globalFlowId) {
        requireEnvironment(environmentId);
        String content = """
                这是外网开发使用的虚构历史日志，不代表任何真实 OBS 路径或报文。
                [INFO ] environment=%s globalFlowId=%s 接收交易请求
                [INFO ] globalFlowId=%s 执行业务处理
                [INFO ] globalFlowId=%s 交易处理结束 responseStatus=Y
                """.formatted(environmentId, globalFlowId, globalFlowId, globalFlowId);
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        String fileId = UUID.nameUUIDFromBytes((environmentId + "|" + queryDate + "|" + globalFlowId).getBytes(StandardCharsets.UTF_8)).toString();
        String fileName = "demo-transaction-" + queryDate + ".log";
        historicalDownloads.put(fileId, new StoredFile(environmentId, fileName, bytes));
        return List.of(new HistoricalLogDocument(fileId, fileName, bytes.length,
                LocalDateTime.of(queryDate, LocalTime.of(23, 59, 59)), content, false));
    }

    @Override
    public LogDownload downloadHistorical(String environmentId, String fileId) {
        requireEnvironment(environmentId);
        StoredFile file = historicalDownloads.get(fileId);
        if (file == null || !file.environmentId().equals(environmentId)) throw new BusinessException("日志文件不存在或查询上下文已失效");
        return new LogDownload(file.fileName(), "text/plain;charset=UTF-8", file.content().length, new ByteArrayInputStream(file.content()));
    }

    @Override
    public CurrentLogDocument query(String environmentId, String globalFlowId) {
        requireEnvironment(environmentId);
        String content = """
                这是外网开发使用的虚构实时日志，不代表任何真实交易应用接口。
                [INFO ] application=demo-transaction-app environment=%s
                [INFO ] globalFlowId=%s 当前交易线程运行正常
                [INFO ] globalFlowId=%s 当前未发现异常堆栈
                """.formatted(environmentId, globalFlowId, globalFlowId);
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        String fileId = UUID.nameUUIDFromBytes((environmentId + "|current|" + globalFlowId).getBytes(StandardCharsets.UTF_8)).toString();
        String fileName = "demo-transaction.log.crn";
        currentDownloads.put(fileId, new StoredFile(environmentId, fileName, bytes));
        return new CurrentLogDocument(fileId, fileName, bytes.length, LocalDateTime.now(), "demo-transaction-app", content, false);
    }

    @Override
    public LogDownload downloadRealTime(String environmentId, String fileId) {
        requireEnvironment(environmentId);
        StoredFile file = currentDownloads.get(fileId);
        if (file == null || !file.environmentId().equals(environmentId)) throw new BusinessException("当前日志文件不存在或查询上下文已失效");
        return new LogDownload(file.fileName(), "text/plain;charset=UTF-8", file.content().length, new ByteArrayInputStream(file.content()));
    }

    private void requireEnvironment(String environmentId) {
        if (!ENVIRONMENT.id().equals(environmentId)) throw new BusinessException("运行环境不存在或尚未配置日志数据源");
    }

    private record StoredFile(String environmentId, String fileName, byte[] content) { }
}
