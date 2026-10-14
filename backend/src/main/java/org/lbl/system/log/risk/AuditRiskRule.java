package org.lbl.system.log.risk;

import org.lbl.system.log.risk.model.AuditRiskFinding;

import java.util.List;

/** 单条确定性风险规则；实现类只负责业务判定，不负责查询、权限或结果上限。 */
public interface AuditRiskRule {
    String code();

    List<AuditRiskFinding> evaluate(AuditRiskContext context);
}
