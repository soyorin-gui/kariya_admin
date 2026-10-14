package org.lbl.approval.core.service;

import org.lbl.approval.core.entity.ApprovalInstanceEntity;
import org.lbl.approval.core.entity.ApprovalTaskEntity;
import org.lbl.approval.core.mapper.ApprovalInstanceMapper;
import org.lbl.approval.core.mapper.ApprovalTaskRecordMapper;
import org.lbl.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** 公共审批状态机；业务校验与最终生效动作仍由各业务服务负责。 */
@Service
public class ApprovalWorkflowService {
    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String CANCELLED = "CANCELLED";

    private final ApprovalInstanceMapper instances;
    private final ApprovalTaskRecordMapper tasks;

    public ApprovalWorkflowService(ApprovalInstanceMapper instances, ApprovalTaskRecordMapper tasks) {
        this.instances = instances;
        this.tasks = tasks;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ApprovalInstanceEntity create(ApprovalInstanceEntity instance, List<ApprovalTaskEntity> taskList) {
        if (instances.selectByBusiness(instance.getBusinessType(), instance.getBusinessId()) != null) {
            throw new BusinessException("该业务已经存在审批实例");
        }
        LocalDateTime now = LocalDateTime.now();
        instance.setStatus(PENDING);
        instance.setApprovalRound(1);
        instance.setSubmittedTime(now);
        instances.insert(instance);
        for (ApprovalTaskEntity task : taskList) {
            task.setApprovalInstanceId(instance.getId());
            task.setApprovalRound(instance.getApprovalRound());
            tasks.insert(task);
        }
        return instance;
    }

    /** 驳回后重新提交同一业务；旧轮次任务保留，只新增下一轮。调用前必须先锁定实例。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void resubmit(ApprovalInstanceEntity instance, String businessTitle, String businessSummary,
                         List<ApprovalTaskEntity> taskList) {
        if (!REJECTED.equals(instance.getStatus()) && !CANCELLED.equals(instance.getStatus())) throw new BusinessException("只有已驳回或已撤销的申请可以重新提交");
        ApprovalTaskEntity firstPending = taskList.stream()
                .filter(task -> "PENDING".equals(task.getStatus()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("重新提交必须包含当前审批步骤"));
        int nextRound = instance.getApprovalRound() + 1;
        instance.setBusinessTitle(businessTitle);
        instance.setBusinessSummary(businessSummary);
        instance.setStatus(PENDING);
        instance.setCurrentStep(firstPending.getStepOrder());
        instance.setApprovalRound(nextRound);
        instance.setSubmittedTime(LocalDateTime.now());
        instance.setFinishedTime(null);
        instances.updateById(instance);
        for (ApprovalTaskEntity task : taskList) {
            task.setApprovalInstanceId(instance.getId());
            task.setApprovalRound(nextRound);
            tasks.insert(task);
        }
    }

    public ApprovalInstanceEntity get(String businessType, Long businessId) {
        ApprovalInstanceEntity instance = instances.selectByBusiness(businessType, businessId);
        if (instance == null) throw new BusinessException("审批实例不存在");
        return instance;
    }

    /** 必须在调用方的 @Transactional 方法内使用，锁覆盖所有连接同一 MySQL 主库的应用实例。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public ApprovalInstanceEntity lock(String businessType, Long businessId) {
        ApprovalInstanceEntity instance = instances.lockByBusiness(businessType, businessId);
        if (instance == null) throw new BusinessException("审批实例不存在");
        return instance;
    }

    public List<ApprovalTaskEntity> tasks(ApprovalInstanceEntity instance) {
        return tasks.byInstance(instance.getId(), instance.getApprovalRound());
    }

    public ApprovalTaskEntity currentTask(ApprovalInstanceEntity instance) {
        return tasks.one(instance.getId(), instance.getApprovalRound(), instance.getCurrentStep());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void approveCurrent(ApprovalInstanceEntity instance, ApprovalTaskEntity current,
                               Long actorId, String actorName, Long nextAssigneeId, String nextAssigneeName) {
        requirePending(instance, current);
        decide(current, "APPROVED", actorId, actorName, null);
        ApprovalTaskEntity next = tasks.nextWaiting(instance.getId(), instance.getApprovalRound(), current.getStepOrder());
        if (next == null) {
            instance.setStatus(APPROVED);
            instance.setFinishedTime(LocalDateTime.now());
        } else {
            next.setAssignedUserId(nextAssigneeId);
            next.setAssignedUserName(nextAssigneeName);
            next.setStatus("PENDING");
            tasks.updateById(next);
            instance.setCurrentStep(next.getStepOrder());
        }
        instances.updateById(instance);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void rejectCurrent(ApprovalInstanceEntity instance, ApprovalTaskEntity current,
                              Long actorId, String actorName, String reason) {
        requirePending(instance, current);
        decide(current, "REJECTED", actorId, actorName, reason);
        cancelWaitingTasks(instance);
        instance.setStatus(REJECTED);
        instance.setFinishedTime(LocalDateTime.now());
        instances.updateById(instance);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void cancel(ApprovalInstanceEntity instance) {
        if (!PENDING.equals(instance.getStatus())) throw new BusinessException("该申请已经处理完成");
        ApprovalTaskEntity current = currentTask(instance);
        if (current != null && "PENDING".equals(current.getStatus())) {
            current.setStatus("CANCELLED");
            tasks.updateById(current);
        }
        cancelWaitingTasks(instance);
        instance.setStatus(CANCELLED);
        instance.setFinishedTime(LocalDateTime.now());
        instances.updateById(instance);
    }

    private void decide(ApprovalTaskEntity task, String status, Long actorId, String actorName, String reason) {
        task.setStatus(status);
        task.setDecidedBy(actorId);
        task.setDecidedByName(actorName);
        task.setDecisionReason(reason);
        task.setDecidedTime(LocalDateTime.now());
        tasks.updateById(task);
    }

    private void cancelWaitingTasks(ApprovalInstanceEntity instance) {
        for (ApprovalTaskEntity task : tasks(instance)) {
            if ("WAITING".equals(task.getStatus())) {
                task.setStatus("CANCELLED");
                tasks.updateById(task);
            }
        }
    }

    private void requirePending(ApprovalInstanceEntity instance, ApprovalTaskEntity current) {
        if (!PENDING.equals(instance.getStatus())) throw new BusinessException("该申请已经处理完成");
        if (current == null || !"PENDING".equals(current.getStatus())) {
            throw new BusinessException("当前审批步骤状态异常");
        }
    }
}
