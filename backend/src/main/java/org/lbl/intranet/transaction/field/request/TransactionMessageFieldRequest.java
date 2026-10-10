package org.lbl.intranet.transaction.field.request;

import jakarta.validation.constraints.*;

public record TransactionMessageFieldRequest(
        @NotNull @PositiveOrZero Long parentId,
        @NotBlank @Pattern(regexp = "FIELD|OBJECT|ARRAY") String nodeType,
        @NotBlank @Size(max = 64) String fieldNameCn,
        @NotBlank @Size(max = 64) String fieldNameEn,
        @Size(max = 64) String dataType,
        @Size(max = 64) String dataLength,
        @NotNull @Min(0) @Max(1) Integer requiredFlag,
        @Size(max = 1000) String description,
        @NotNull @Min(0) Integer sortOrder) { }
