package org.lbl.intranet.dataasset.es.vo;

import java.time.LocalDateTime;

public record EsFieldVO(Long id, Long esIndexId, String fieldName, String dataType,
                        Integer searchableFlag, Integer sortableFlag, String description, Integer sortOrder,
                        Long createdBy, LocalDateTime createdTime, Long updatedBy, LocalDateTime updatedTime) { }
