package org.lbl.intranet.operation.flow.source;

import org.lbl.intranet.operation.flow.model.FlowEnvironment;

import java.time.LocalDate;
import java.util.List;

/**
 * 流水原始记录来源。外网页面和查询逻辑只依赖这个边界；内网接入时新增 Redis Cluster 实现。
 */
public interface TransactionFlowSource {
    List<FlowEnvironment> environments();

    List<String> responseCodes(String environmentId);

    List<String> read(String environmentId, LocalDate queryDate);
}
