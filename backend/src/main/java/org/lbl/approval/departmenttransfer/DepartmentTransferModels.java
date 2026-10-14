package org.lbl.approval.departmenttransfer;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

public final class DepartmentTransferModels {
    private DepartmentTransferModels() {}
    public record Submit(@NotNull @Positive Long targetDeptId,
                         @NotBlank @Size(max = 500, message = "申请说明最长 500 个字符") String reason) {}
    public record Reject(@NotBlank(message = "请填写拒绝原因")
                         @Size(max = 500, message = "拒绝原因最长 500 个字符") String reason) {}
    public record DeptOption(Long id, String name) {}
    public record Step(Long id, int order, String type, Long deptId, String deptName, Long assignedUserId,
                       String assignedUserName, String status, Long decidedBy, String decidedByName,
                       String decisionReason, LocalDateTime decidedTime) {}
    public record Detail(Long id, Long requesterId, String requesterName, Long fromDeptId, String fromDeptName,
                         Long targetDeptId, String targetDeptName, String reason, String status, int currentStep,
                         LocalDateTime createdTime, LocalDateTime finishedTime, boolean canApprove,
                         boolean canCancel, List<Step> steps) {}
    public record Profile(Long currentDeptId, String currentDeptName, Detail activeRequest) {}
}
