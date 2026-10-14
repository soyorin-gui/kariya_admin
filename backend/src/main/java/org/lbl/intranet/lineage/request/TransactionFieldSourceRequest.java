package org.lbl.intranet.lineage.request;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
public record TransactionFieldSourceRequest(@NotBlank String sourceType, String sourceDetail, @Valid List<Relation> relations) { public record Relation(@NotBlank String sourceType, Long sourceFieldId, @NotBlank String relationType, String expression, String remark) { } }
