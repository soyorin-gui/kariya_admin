package org.lbl.intranet.dataasset.es.request;

import jakarta.validation.constraints.*;

public record EsFieldRequest(
        @NotBlank(message = "请输入字段名") @Size(max = 128) String fieldName,
        @NotBlank(message = "请输入数据类型") @Size(max = 64) String dataType,
        @NotNull(message = "请选择是否可搜索") @Min(0) @Max(1) Integer searchableFlag,
        @NotNull(message = "请选择是否可排序") @Min(0) @Max(1) Integer sortableFlag,
        @Size(max = 1000) String description,
        @NotNull(message = "请输入排序") Integer sortOrder) { }
