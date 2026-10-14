package org.lbl.system.log.controller;

import org.lbl.common.result.PageResult;
import org.lbl.common.result.Result;
import org.lbl.system.log.analysis.AuditAnalysisService;
import org.lbl.system.log.analysis.model.OperationAuditOverview;
import org.lbl.system.log.aspect.OperationLog;
import org.lbl.system.log.entity.OperationLogEntity;
import org.lbl.system.log.service.OperationLogService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/system/operation-logs")
public class OperationLogController {
    private final OperationLogService service;
    private final AuditAnalysisService analysis;

    public OperationLogController(OperationLogService service, AuditAnalysisService analysis) {
        this.service = service;
        this.analysis = analysis;
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

    /**
     * 返回指定时间窗口的操作统计。这里只返回客观数量，不判断是否异常。
     */
    @GetMapping("/statistics")
    @PreAuthorize("hasAuthority('system:operatelog:list')")
    Result<OperationAuditOverview> statistics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime beginTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false) Integer topN) {
        return Result.ok(analysis.operationOverview(beginTime, endTime, topN));
    }

    @DeleteMapping
    @PreAuthorize("hasAuthority('system:operatelog:delete')")
    @OperationLog(module = "操作日志", action = "删除操作日志")
    Result<Void> remove(@RequestParam List<Long> ids) {
        return Result.ok(null, "已删除 " + service.remove(ids) + " 条操作日志");
    }
}
