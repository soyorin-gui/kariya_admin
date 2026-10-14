package org.lbl.intranet.requirement.request;
import jakarta.validation.constraints.NotBlank;
public record RequirementDecisionRequest(@NotBlank(message = "请填写拒绝原因") String reason) { }
