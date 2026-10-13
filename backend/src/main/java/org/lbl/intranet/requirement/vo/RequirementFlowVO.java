package org.lbl.intranet.requirement.vo;

import java.time.LocalDateTime;
public record RequirementFlowVO(String eventType, String eventName, String remark, String operatorName, LocalDateTime createdTime) { }
