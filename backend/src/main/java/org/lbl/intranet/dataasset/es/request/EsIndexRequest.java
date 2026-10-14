package org.lbl.intranet.dataasset.es.request;

import jakarta.validation.constraints.*;

public record EsIndexRequest(
        @NotBlank(message = "请输入逻辑索引名") @Size(max = 128) String indexName,
        @NotBlank(message = "请输入索引命名模式") @Size(max = 255) String indexPattern,
        @Size(max = 64) String partitionRule,
        @NotNull(message = "请选择 HBase 表") Long hbaseTableId,
        @Size(max = 1000) String description) { }
