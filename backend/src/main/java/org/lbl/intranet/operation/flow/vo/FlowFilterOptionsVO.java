package org.lbl.intranet.operation.flow.vo;

import java.util.List;

public record FlowFilterOptionsVO(List<String> responseStatuses, List<String> responseCodes) { }
