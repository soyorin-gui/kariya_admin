package org.lbl.intranet.operation.log.source;

import org.lbl.intranet.operation.log.model.LogEnvironment;
import org.lbl.intranet.operation.log.model.CurrentLogDocument;
import org.lbl.intranet.operation.log.model.LogDownload;

import java.util.List;

/** 交易应用内部 HTTP 实时日志接口边界；外网阶段仅提供 local 模拟实现。 */
public interface RealtimeLogSource {
    List<LogEnvironment> environments();

    CurrentLogDocument query(String environmentId, String globalFlowId);

    LogDownload downloadRealTime(String environmentId, String fileId);
}
