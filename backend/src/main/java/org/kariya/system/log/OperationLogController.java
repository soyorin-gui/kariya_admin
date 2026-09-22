package org.kariya.system.log;

import org.kariya.common.result.PageResult;
import org.kariya.common.result.Result;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/system/operation-logs")
public class OperationLogController {
    private final OperationLogService service;

    public OperationLogController(OperationLogService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('system:operatelog:list')")
    Result<PageResult<OperationLogEntity>> page(@RequestParam(defaultValue = "1") long pageNum,
                                               @RequestParam(defaultValue = "10") long pageSize,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) String module,
                                               @RequestParam(required = false) String result,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime beginTime,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return Result.ok(service.page(pageNum, pageSize, keyword, module, result, beginTime, endTime));
    }

    @DeleteMapping
    @PreAuthorize("hasAuthority('system:operatelog:delete')")
    @OperationLog(module = "操作日志", action = "删除操作日志")
    Result<Void> remove(@RequestParam List<Long> ids) {
        return Result.ok(null, "已删除 " + service.remove(ids) + " 条操作日志");
    }
}
