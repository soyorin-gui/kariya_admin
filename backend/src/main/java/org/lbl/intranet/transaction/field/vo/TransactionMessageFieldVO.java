package org.lbl.intranet.transaction.field.vo;

public record TransactionMessageFieldVO(Long id, Long transactionId, String messageSide, Long parentId, String nodeType,
                                        String fieldNameCn, String fieldNameEn, String dataType, String dataLength,
                                        Integer requiredFlag, String description, Integer sortOrder) { }
