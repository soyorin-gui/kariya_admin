package org.lbl.system.log.risk.model;

import lombok.Data;

import java.time.LocalDateTime;

/** 登录风险规则所需的最小数据库投影。 */
@Data
public class LoginRiskEvent {
    private Long id;
    private Long userId;
    private String username;
    private String loginIp;
    private String userAgent;
    private String result;
    private LocalDateTime loginTime;
}
