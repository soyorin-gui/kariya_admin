package org.lbl.intranet.requirement.request;
import jakarta.validation.constraints.NotNull;
public record AssignDeveloperRequest(@NotNull(message = "请选择开发负责人") Long developerUserId) { }
