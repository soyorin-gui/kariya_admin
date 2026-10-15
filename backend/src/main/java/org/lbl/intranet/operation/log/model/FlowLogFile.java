package org.lbl.intranet.operation.log.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public record FlowLogFile(
        String fileId,
        String fileName,
        LogFileType fileType,
        long fileSize,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime lastModified,
        boolean truncated
) { }
