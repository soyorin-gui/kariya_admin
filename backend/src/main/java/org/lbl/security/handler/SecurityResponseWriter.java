package org.lbl.security.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.lbl.common.result.Result;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 在 Spring Security 过滤器链内部直接写响应体。
 * <p>
 * 过滤器链中的拒绝发生在进入 DispatcherServlet 之前，{@code @RestControllerAdvice} 此时还没有机会介入，
 * 因此必须在这里手动输出。响应体刻意保持与业务接口完全一致的 {@link Result} 结构
 * （code / message / data / requestId），前端一套解析逻辑即可通用；
 * requestId 由 RequestLoggingFilter 写入 MDC，这里能取到同一个值，方便和服务端日志对账。
 */
public final class SecurityResponseWriter {
    private SecurityResponseWriter() {
    }

    public static void write(HttpServletResponse response, ObjectMapper json, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        json.writeValue(response.getOutputStream(), Result.fail(status, message));
    }
}
