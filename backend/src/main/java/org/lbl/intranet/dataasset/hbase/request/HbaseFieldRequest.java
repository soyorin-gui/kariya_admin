package org.lbl.intranet.dataasset.hbase.request;

import jakarta.validation.constraints.*;

public record HbaseFieldRequest(
        @NotBlank(message = "请输入列族") @Size(max = 64) String columnFamily,
        @NotBlank(message = "请输入字段名") @Size(max = 128) String fieldName,
        @Size(max = 128) String dataType,
        @Size(max = 1000) String description,
        @NotNull(message = "请输入排序") Integer sortOrder) { }
