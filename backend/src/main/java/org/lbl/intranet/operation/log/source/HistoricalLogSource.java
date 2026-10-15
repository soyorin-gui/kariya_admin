package org.lbl.intranet.operation.log.source;

import org.lbl.intranet.operation.log.model.HistoricalLogDocument;
import org.lbl.intranet.operation.log.model.LogDownload;
import org.lbl.intranet.operation.log.model.LogEnvironment;

import java.time.LocalDate;
import java.util.List;

/** OBS 历史日志的外部系统边界；外网阶段仅提供 local 模拟实现。 */
public interface HistoricalLogSource {
    List<LogEnvironment> environments();

    List<HistoricalLogDocument> query(String environmentId, LocalDate queryDate, String globalFlowId);

    LogDownload download(String environmentId, String fileId);
}
