package org.kariya.system.log;

import org.kariya.common.result.PageResult;
import org.kariya.common.result.Result;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/system/login-logs")
public class LoginLogController {
    private final LoginLogService service;

    public LoginLogController(LoginLogService service) {
        this.service = service;
    }

    /**
     * 时间参数用 ISO-8601（{@code 2026-09-22T00:00:00}）。查询参数不像请求体那样走 Jackson，
     * 必须显式声明 @DateTimeFormat，否则 LocalDateTime 绑定会直接失败。
     */
    @GetMapping
    @PreAuthorize("hasAuthority('system:loginlog:list')")
    Result<PageResult<LoginLogEntity>> page(@RequestParam(defaultValue = "1") long pageNum,
                                           @RequestParam(defaultValue = "10") long pageSize,
                                           @RequestParam(required = false) String username,
                                           @RequestParam(required = false) String result,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime beginTime,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return Result.ok(service.page(pageNum, pageSize, username, result, beginTime, endTime));
    }

    @DeleteMapping
    @PreAuthorize("hasAuthority('system:loginlog:delete')")
    @OperationLog(module = "登录日志", action = "删除登录日志")
    Result<Void> remove(@RequestParam List<Long> ids) {
        return Result.ok(null, "已删除 " + service.remove(ids) + " 条登录日志");
    }
}
