package org.lbl.intranet.dataasset.hbase.vo;

import java.time.LocalDateTime;

public record HbaseTableVO(Long id, String marketName, String tableName, String description,
                           String rowkeyRule, String lockKey, String dataVolume, String dataDepth,
                           Long createdBy, LocalDateTime createdTime, Long updatedBy, LocalDateTime updatedTime) { }
