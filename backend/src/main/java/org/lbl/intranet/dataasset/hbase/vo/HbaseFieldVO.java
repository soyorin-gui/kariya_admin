package org.lbl.intranet.dataasset.hbase.vo;

import java.time.LocalDateTime;

public record HbaseFieldVO(Long id, Long hbaseTableId, String columnFamily, String fieldName,
                           String dataType, String description, Integer sortOrder,
                           Long createdBy, LocalDateTime createdTime, Long updatedBy, LocalDateTime updatedTime) { }
