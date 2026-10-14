package org.lbl.system.log.risk;

import org.lbl.system.log.risk.model.LoginRiskEvent;
import org.lbl.system.log.risk.model.OperationRiskEvent;

import java.time.LocalDateTime;
import java.util.List;

/** 一次扫描的只读数据快照；四条规则共享，避免重复查询同一批日志。 */
public record AuditRiskContext(
        LocalDateTime beginTime,
        LocalDateTime endTime,
        List<LoginRiskEvent> loginEvents,
        List<OperationRiskEvent> operationEvents) {

    public AuditRiskContext {
        loginEvents = loginEvents == null ? List.of() : List.copyOf(loginEvents);
        operationEvents = operationEvents == null ? List.of() : List.copyOf(operationEvents);
    }
}
