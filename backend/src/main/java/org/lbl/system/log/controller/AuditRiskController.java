package org.lbl.system.log.controller;

import org.lbl.common.result.Result;
import org.lbl.system.log.risk.AuditRiskAnalysisService;
import org.lbl.system.log.risk.model.AuditRiskReport;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/** 登录与操作日志的统一只读风险扫描入口。 */
@RestController
@RequestMapping("/api/system/audit-risks")
public class AuditRiskController {
    private final AuditRiskAnalysisService analysis;

    public AuditRiskController(AuditRiskAnalysisService analysis) {
        this.analysis = analysis;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('system:loginlog:list') and hasAuthority('system:operatelog:list')")
    Result<AuditRiskReport> scan(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime beginTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return Result.ok(analysis.scan(beginTime, endTime));
    }
}
