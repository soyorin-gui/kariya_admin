package org.lbl.intranet.dataasset.hbase.request;

import jakarta.validation.constraints.*;

public record HbaseTableRequest(
        @NotBlank(message = "请输入集市名") @Size(max = 64) String marketName,
        @NotBlank(message = "请输入 HBase 表名") @Size(max = 128) String tableName,
        @Size(max = 1000) String description,
        @Size(max = 1000) String rowkeyRule,
        @Size(max = 255) String lockKey,
        @Size(max = 64) String dataVolume,
        @Size(max = 64) String dataDepth) { }
