package org.lbl.intranet.dataasset.es.vo;

import java.time.LocalDateTime;

public record EsIndexVO(Long id, String indexName, String indexPattern, String partitionRule,
                        Long hbaseTableId, String hbaseMarketName, String hbaseTableName, String description,
                        Long createdBy, LocalDateTime createdTime, Long updatedBy, LocalDateTime updatedTime) { }
