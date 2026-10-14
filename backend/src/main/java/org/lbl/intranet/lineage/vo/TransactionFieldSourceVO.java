package org.lbl.intranet.lineage.vo;
import java.util.List;
public record TransactionFieldSourceVO(Long targetFieldId, String sourceType, String sourceDetail, List<Relation> relations) { public record Relation(Long id, String sourceType, Long sourceFieldId, String sourceLabel, String relationType, String expression, String remark) { } }
