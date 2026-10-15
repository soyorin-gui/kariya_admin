package org.lbl.intranet.operation.log.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public record HistoricalLogDocument(
        String fileId,
        String fileName,
        long fileSize,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime lastModified,
        String content,
        boolean truncated
) { }
