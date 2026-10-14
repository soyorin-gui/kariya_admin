package org.lbl.intranet.lineage.vo;
public record ImpactItemVO(Long transactionId, String transactionCode, String transactionName, Long targetFieldId, String messageSide, String fieldNameCn, String fieldNameEn, String relationType, String expression, String remark) { }
