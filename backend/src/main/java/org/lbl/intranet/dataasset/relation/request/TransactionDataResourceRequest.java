package org.lbl.intranet.dataasset.relation.request;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record TransactionDataResourceRequest(
        @NotNull(message = "HBase 资源列表不能为空") List<Long> hbaseTableIds,
        @NotNull(message = "ES 资源列表不能为空") List<Long> esIndexIds) { }
