package org.lbl.intranet.operation.log.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public record RealtimeLogResult(
        String applicationName,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime collectedTime,
        String content
) { }
