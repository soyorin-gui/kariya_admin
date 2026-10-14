package org.lbl.common.result;

import java.util.UUID;
import org.slf4j.MDC;

public record Result<T>(int code, String message, T data, String requestId) {
    public static <T> Result<T> ok(T data) {
        return new Result<>(0, "success", data, resolveRequestId());
    }

    public static <T> Result<T> ok(T data, String message) {
        return new Result<>(0, message, data, resolveRequestId());
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null, resolveRequestId());
    }

    private static String resolveRequestId() {
        String requestId = MDC.get("requestId");
        return requestId == null ? UUID.randomUUID().toString() : requestId;
    }
}
