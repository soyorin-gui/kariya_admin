package org.lbl.intranet.operation.log.model;

import org.lbl.intranet.operation.flow.model.TransactionFlowRecord;

import java.util.List;

public record FlowLogContext(
        TransactionFlowRecord flow,
        String content,
        List<FlowLogFile> files,
        boolean truncated,
        List<String> warnings
) { }
