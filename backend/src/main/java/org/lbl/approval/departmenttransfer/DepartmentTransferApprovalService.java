package org.lbl.approval.departmenttransfer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.lbl.approval.core.entity.ApprovalInstanceEntity;
import org.lbl.approval.core.entity.ApprovalTaskEntity;
import org.lbl.approval.core.service.ApprovalWorkflowService;
import org.lbl.approval.departmenttransfer.DepartmentTransferModels.*;
import org.lbl.common.exception.BusinessException;
import org.lbl.notification.NotificationService;
import org.lbl.security.context.AccessPolicy;
import org.lbl.system.dept.entity.DeptEntity;
import org.lbl.system.dept.mapper.DeptMapper;
import org.lbl.system.user.entity.UserEntity;
import org.lbl.system.user.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class DepartmentTransferApprovalService {
    private static final String BUSINESS_TYPE = "DEPARTMENT_CHANGE";

    private final DepartmentTransferRequestMapper requests;
    private final UserMapper users;
    private final DeptMapper depts;
    private final AccessPolicy access;
    private final NotificationService notifications;
    private final ApprovalWorkflowService approvals;

    public DepartmentTransferApprovalService(DepartmentTransferRequestMapper requests, UserMapper users,
                                             DeptMapper depts, AccessPolicy access,
                                             NotificationService notifications, ApprovalWorkflowService approvals) {
        this.requests = requests;
        this.users = users;
        this.depts = depts;
        this.access = access;
        this.notifications = notifications;
        this.approvals = approvals;
    }

    public List<DeptOption> options() {
        return depts.selectList(new LambdaQueryWrapper<DeptEntity>().eq(DeptEntity::getStatus, 1)
                        .orderByAsc(DeptEntity::getSortOrder).orderByAsc(DeptEntity::getId)).stream()
                .map(value -> new DeptOption(value.getId(), value.getDeptName())).toList();
    }

    public Profile profile() {
        UserEntity user = access.actor().user();
        DeptEntity dept = user.getDeptId() == null ? null : depts.selectById(user.getDeptId());
        DepartmentTransferRequestEntity active = requests.activeByRequester(user.getId());
        return new Profile(user.getDeptId(), dept == null ? null : dept.getDeptName(),
                active == null ? null : detail(active, access.actor()));
    }

    @Transactional
    public Detail submit(Submit form) {
        AccessPolicy.Actor actor = access.actor();
        UserEntity requester = users.selectByIdForUpdate(actor.user().getId());
        if (requester == null || requester.getStatus() != 1) throw new BusinessException("账号不存在或已停用");
        if (requests.activeByRequester(requester.getId()) != null) throw new BusinessException("已有部门变更申请正在审批中");
        DeptEntity target = depts.selectById(form.targetDeptId());
        if (target == null || target.getStatus() != 1) throw new BusinessException("目标部门不存在或已停用");
        if (Objects.equals(requester.getDeptId(), target.getId())) throw new BusinessException("你已经属于该部门");

        boolean needsSource = requester.getDeptId() != null;
        DepartmentTransferRequestEntity request = new DepartmentTransferRequestEntity();
        request.setRequesterId(requester.getId());
        request.setFromDeptId(requester.getDeptId());
        request.setTargetDeptId(target.getId());
        request.setReason(form.reason().trim());
        requests.insert(request);

        Long sourceAssignee = needsSource ? reviewer(requester.getDeptId(), requester.getId()) : null;
        Long targetAssignee = needsSource ? null : targetReviewer(actor, requester, target.getId());
        ApprovalTaskEntity source = task(1, "SOURCE", "原部门审批", sourceAssignee,
                needsSource ? "PENDING" : "SKIPPED");
        ApprovalTaskEntity targetTask = task(2, "TARGET", "目标部门审批", targetAssignee,
                needsSource ? "WAITING" : "PENDING");

        DeptEntity from = requester.getDeptId() == null ? null : depts.selectById(requester.getDeptId());
        ApprovalInstanceEntity instance = new ApprovalInstanceEntity();
        instance.setBusinessType(BUSINESS_TYPE);
        instance.setBusinessId(request.getId());
        instance.setBusinessTitle(requester.getRealName() + "："
                + (from == null ? "未分配部门" : from.getDeptName()) + " → " + target.getDeptName());
        instance.setBusinessSummary(request.getReason());
        instance.setRequesterId(requester.getId());
        instance.setRequesterName(requester.getRealName());
        instance.setCurrentStep(needsSource ? 1 : 2);
        approvals.create(instance, List.of(source, targetTask));

        ApprovalTaskEntity current = needsSource ? source : targetTask;
        String currentDeptLabel = needsSource ? "原部门" : "目标部门";
        notifications.create(requester.getId(), "DEPARTMENT_REQUEST_SUBMITTED", "部门变更申请已提交",
                "申请已提交，" + reviewerPhrase(current, currentDeptLabel), BUSINESS_TYPE, request.getId());
        notifyCurrentReviewer(request, current, requester);
        notifySuperAdmins(request, "新的部门变更申请", requester.getRealName() + "提交了部门变更申请"
                + (current.getAssignedUserId() == null
                ? "（" + currentDeptLabel + "步骤需由超级管理员处理）" : ""));
        return detail(request, actor);
    }

    public Detail get(Long id) {
        AccessPolicy.Actor actor = access.actor();
        DepartmentTransferRequestEntity request = require(id);
        ApprovalInstanceEntity instance = approvals.get(BUSINESS_TYPE, id);
        requireVisible(instance, actor);
        return detail(request, actor, instance);
    }

    @Transactional
    public Detail approve(Long id) {
        return decide(id, true, null);
    }

    @Transactional
    public Detail reject(Long id, String reason) {
        if (reason == null || reason.isBlank()) throw new BusinessException("请填写拒绝原因");
        return decide(id, false, reason.trim());
    }

    @Transactional
    public Detail cancel(Long id) {
        AccessPolicy.Actor actor = access.actor();
        ApprovalInstanceEntity instance = approvals.lock(BUSINESS_TYPE, id);
        DepartmentTransferRequestEntity request = require(id);
        if (!instance.getRequesterId().equals(actor.user().getId())) throw new BusinessException("申请不存在");
        approvals.cancel(instance);
        notifyParticipants(instance, "部门变更申请已撤销", "申请人已撤销本次部门变更申请");
        return detail(request, actor, instance);
    }

    private Detail decide(Long id, boolean approved, String reason) {
        AccessPolicy.Actor actor = access.actor();
        ApprovalInstanceEntity instance = approvals.lock(BUSINESS_TYPE, id);
        DepartmentTransferRequestEntity request = require(id);
        ApprovalTaskEntity current = approvals.currentTask(instance);
        if (current == null || !"PENDING".equals(current.getStatus())) throw new BusinessException("当前审批步骤状态异常");
        if (!actor.superAdmin() && !actor.user().getId().equals(current.getAssignedUserId())) {
            throw new BusinessException("当前步骤不属于你审批");
        }
        if (instance.getRequesterId().equals(actor.user().getId())) throw new BusinessException("不能审批自己的部门变更申请");

        UserEntity requester = users.selectByIdForUpdate(request.getRequesterId());
        DeptEntity target = depts.selectById(request.getTargetDeptId());
        if (requester == null || requester.getStatus() != 1 || target == null || target.getStatus() != 1
                || !Objects.equals(requester == null ? null : requester.getDeptId(), request.getFromDeptId())) {
            approvals.cancel(instance);
            notifyParticipants(instance, "部门变更申请已失效", "用户当前部门或目标部门已经发生变化，请重新提交申请");
            return detail(request, actor, instance);
        }

        if (!approved) {
            approvals.rejectCurrent(instance, current, actor.user().getId(), actor.user().getRealName(), reason);
            notifyParticipants(instance, "部门变更申请已拒绝", "审批未通过：" + reason);
            return detail(request, actor, instance);
        }

        if (instance.getCurrentStep() == 1) {
            Long targetReviewerId = targetReviewer(actor, requester, request.getTargetDeptId());
            approvals.approveCurrent(instance, current, actor.user().getId(), actor.user().getRealName(),
                    targetReviewerId, userName(targetReviewerId));
            ApprovalTaskEntity targetTask = approvals.currentTask(instance);
            notifications.create(request.getRequesterId(), "DEPARTMENT_REQUEST_PROGRESS", "原部门审批已通过",
                    "申请已进入目标部门审批阶段，" + reviewerPhrase(targetTask, "目标部门"),
                    BUSINESS_TYPE, request.getId());
            notifyCurrentReviewer(request, targetTask, requester);
            notifications.approvalUpdated(participants(instance), request.getId());
            return detail(request, actor, instance);
        }

        if (!actor.superAdmin() && access.requiresPlatformTransferReview(actor, requester)) {
            throw new BusinessException("该用户的部门角色范围会随调动改变，请由超级管理员审批");
        }
        requester.setDeptId(request.getTargetDeptId());
        users.updateById(requester);
        approvals.approveCurrent(instance, current, actor.user().getId(), actor.user().getRealName(), null, null);
        notifyParticipants(instance, "部门变更申请已通过", "全部审批完成，用户部门已更新为“" + target.getDeptName() + "”");
        return detail(request, actor, instance);
    }

    private ApprovalTaskEntity task(int order, String type, String name, Long assigned, String status) {
        ApprovalTaskEntity value = new ApprovalTaskEntity();
        value.setStepOrder(order);
        value.setStepType(type);
        value.setStepName(name);
        value.setAssignedUserId(assigned);
        value.setAssignedUserName(userName(assigned));
        value.setStatus(status);
        return value;
    }

    private Long targetReviewer(AccessPolicy.Actor actor, UserEntity requester, Long deptId) {
        return access.requiresPlatformTransferReview(actor, requester) ? null : reviewer(deptId, requester.getId());
    }

    private Long reviewer(Long deptId, Long requesterId) {
        DeptEntity dept = deptId == null ? null : depts.selectById(deptId);
        if (dept == null || dept.getLeaderUserId() == null || dept.getLeaderUserId().equals(requesterId)) return null;
        UserEntity leader = users.selectById(dept.getLeaderUserId());
        return leader != null && leader.getStatus() == 1 ? leader.getId() : null;
    }

    private String userName(Long userId) {
        if (userId == null) return null;
        UserEntity user = users.selectById(userId);
        return user == null ? null : user.getRealName();
    }

    private String reviewerPhrase(ApprovalTaskEntity task, String deptLabel) {
        return task.getAssignedUserId() == null
                ? deptLabel + "步骤需由超级管理员审批（无有效负责人或调动涉及角色数据范围变更）"
                : "等待" + deptLabel + "负责人审批";
    }

    private void notifyCurrentReviewer(DepartmentTransferRequestEntity request, ApprovalTaskEntity task, UserEntity requester) {
        if (task.getAssignedUserId() != null) {
            notifications.create(task.getAssignedUserId(), "DEPARTMENT_APPROVAL_REQUIRED", "待审批的部门变更申请",
                    requester.getRealName() + "提交了部门变更申请，请处理当前步骤", BUSINESS_TYPE, request.getId());
        }
    }

    private void notifySuperAdmins(DepartmentTransferRequestEntity request, String title, String content) {
        for (Long id : users.selectActiveSuperAdminIds()) {
            if (!id.equals(request.getRequesterId())) {
                notifications.create(id, "DEPARTMENT_APPROVAL_WATCH", title, content, BUSINESS_TYPE, request.getId());
            }
        }
    }

    private void notifyParticipants(ApprovalInstanceEntity instance, String title, String content) {
        Set<Long> participantIds = participants(instance);
        for (Long id : participantIds) {
            notifications.create(id, "DEPARTMENT_REQUEST_RESULT", title, content,
                    BUSINESS_TYPE, instance.getBusinessId());
        }
        notifications.approvalUpdated(participantIds, instance.getBusinessId());
    }

    private Set<Long> participants(ApprovalInstanceEntity instance) {
        Set<Long> ids = new LinkedHashSet<>();
        ids.add(instance.getRequesterId());
        ids.addAll(users.selectActiveSuperAdminIds());
        for (ApprovalTaskEntity task : approvals.tasks(instance)) {
            if (task.getAssignedUserId() != null) ids.add(task.getAssignedUserId());
            if (task.getDecidedBy() != null) ids.add(task.getDecidedBy());
        }
        return ids;
    }

    private void requireVisible(ApprovalInstanceEntity instance, AccessPolicy.Actor actor) {
        if (actor.superAdmin() || participants(instance).contains(actor.user().getId())) return;
        throw new BusinessException("无权查看该申请");
    }

    private DepartmentTransferRequestEntity require(Long id) {
        DepartmentTransferRequestEntity value = requests.selectById(id);
        if (value == null) throw new BusinessException("申请不存在");
        return value;
    }

    private Detail detail(DepartmentTransferRequestEntity request, AccessPolicy.Actor actor) {
        return detail(request, actor, approvals.get(BUSINESS_TYPE, request.getId()));
    }

    private Detail detail(DepartmentTransferRequestEntity request, AccessPolicy.Actor actor,
                          ApprovalInstanceEntity instance) {
        UserEntity requester = users.selectById(request.getRequesterId());
        DeptEntity from = request.getFromDeptId() == null ? null : depts.selectById(request.getFromDeptId());
        DeptEntity target = depts.selectById(request.getTargetDeptId());
        List<Step> stepViews = approvals.tasks(instance).stream().map(value -> {
            Long deptId = "SOURCE".equals(value.getStepType()) ? request.getFromDeptId() : request.getTargetDeptId();
            DeptEntity dept = deptId == null ? null : depts.selectById(deptId);
            return new Step(value.getId(), value.getStepOrder(), value.getStepType(), deptId,
                    dept == null ? null : dept.getDeptName(), value.getAssignedUserId(), value.getAssignedUserName(),
                    value.getStatus(), value.getDecidedBy(), value.getDecidedByName(),
                    value.getDecisionReason(), value.getDecidedTime());
        }).toList();
        ApprovalTaskEntity current = ApprovalWorkflowService.PENDING.equals(instance.getStatus())
                ? approvals.currentTask(instance) : null;
        boolean requiresPlatform = current != null && current.getStepOrder() == 2 && requester != null
                && access.requiresPlatformTransferReview(actor, requester);
        boolean canApprove = current != null && !instance.getRequesterId().equals(actor.user().getId())
                && (actor.superAdmin() || !requiresPlatform && actor.user().getId().equals(current.getAssignedUserId()));
        String status = ApprovalWorkflowService.PENDING.equals(instance.getStatus())
                ? (instance.getCurrentStep() == 1 ? "PENDING_SOURCE" : "PENDING_TARGET")
                : instance.getStatus();
        return new Detail(request.getId(), request.getRequesterId(),
                requester == null ? instance.getRequesterName() : requester.getRealName(),
                request.getFromDeptId(), from == null ? "未分配部门" : from.getDeptName(), request.getTargetDeptId(),
                target == null ? "已删除部门" : target.getDeptName(), request.getReason(), status,
                instance.getCurrentStep(), instance.getCreatedTime(), instance.getFinishedTime(), canApprove,
                instance.getRequesterId().equals(actor.user().getId()) && current != null, stepViews);
    }
}
