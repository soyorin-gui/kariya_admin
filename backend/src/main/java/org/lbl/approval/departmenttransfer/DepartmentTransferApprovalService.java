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
import org.lbl.system.role.mapper.RoleMapper;
import org.lbl.system.user.entity.UserEntity;
import org.lbl.system.user.mapper.UserMapper;
import org.lbl.system.user.mapper.UserRoleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class DepartmentTransferApprovalService {
    private static final String BUSINESS_TYPE = "DEPARTMENT_CHANGE";
    private static final String DEPARTMENT_APPROVER_ROLE = "department_approver";

    private final DepartmentTransferRequestMapper requests;
    private final UserMapper users;
    private final DeptMapper depts;
    private final AccessPolicy access;
    private final NotificationService notifications;
    private final ApprovalWorkflowService approvals;
    private final RoleMapper roles;
    private final UserRoleMapper userRoles;

    public DepartmentTransferApprovalService(DepartmentTransferRequestMapper requests, UserMapper users,
                                             DeptMapper depts, AccessPolicy access,
                                             NotificationService notifications, ApprovalWorkflowService approvals,
                                             RoleMapper roles, UserRoleMapper userRoles) {
        this.requests = requests;
        this.users = users;
        this.depts = depts;
        this.access = access;
        this.notifications = notifications;
        this.approvals = approvals;
        this.roles = roles;
        this.userRoles = userRoles;
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

        ApprovalTaskEntity source = task(1, "SOURCE", "原部门审批", requester.getDeptId() == null ? null : depts.selectById(requester.getDeptId()),
                needsSource ? "PENDING" : "SKIPPED");
        ApprovalTaskEntity targetTask = task(2, "TARGET", "目标部门审批", target,
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
                + "（" + currentDeptLabel + "审批组或超级管理员可处理）");
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
        if (!candidate(actor, current)) {
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
            approvals.approveCurrent(instance, current, actor.user().getId(), actor.user().getRealName(),
                    null, null);
            ApprovalTaskEntity targetTask = approvals.currentTask(instance);
            notifications.create(request.getRequesterId(), "DEPARTMENT_REQUEST_PROGRESS", "原部门审批已通过",
                    "申请已进入目标部门审批阶段，" + reviewerPhrase(targetTask, "目标部门"),
                    BUSINESS_TYPE, request.getId());
            notifyCurrentReviewer(request, targetTask, requester);
            notifications.approvalUpdated(participants(instance), request.getId());
            return detail(request, actor, instance);
        }

        var boundRoles = roles.selectDepartmentBoundByUserId(requester.getId());
        if (!boundRoles.isEmpty()) userRoles.deleteByUserIdAndRoleIds(requester.getId(), boundRoles.stream().map(value -> value.getId()).toList());
        requester.setDeptId(request.getTargetDeptId());
        users.updateById(requester);
        approvals.approveCurrent(instance, current, actor.user().getId(), actor.user().getRealName(), null, null);
        notifyParticipants(instance, "部门变更申请已通过", "全部审批完成，用户部门已更新为“" + target.getDeptName() + "”");
        return detail(request, actor, instance);
    }

    private ApprovalTaskEntity task(int order, String type, String name, DeptEntity dept, String status) {
        ApprovalTaskEntity value = new ApprovalTaskEntity();
        value.setStepOrder(order);
        value.setStepType(type);
        value.setStepName(name);
        if (dept != null) {
            value.setCandidateDeptId(dept.getId());
            value.setCandidateDeptName(dept.getDeptName());
            value.setCandidateRoleCode(DEPARTMENT_APPROVER_ROLE);
            value.setCandidateRoleName("部门审批人");
        }
        value.setStatus(status);
        return value;
    }

    private String reviewerPhrase(ApprovalTaskEntity task, String deptLabel) {
        return "等待" + deptLabel + "部门审批人或超级管理员审批";
    }

    private void notifyCurrentReviewer(DepartmentTransferRequestEntity request, ApprovalTaskEntity task, UserEntity requester) {
        // 候选组成员通过审批中心待办获取任务；超级管理员另有观察通知。
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
        if (actor.superAdmin() || participants(instance).contains(actor.user().getId())
                || approvals.tasks(instance).stream().anyMatch(task -> candidate(actor, task))) return;
        throw new BusinessException("无权查看该申请");
    }

    private boolean candidate(AccessPolicy.Actor actor, ApprovalTaskEntity task) {
        return actor.superAdmin() || task.getCandidateDeptId() != null
                && users.hasActiveRoleInDepartment(actor.user().getId(), task.getCandidateDeptId(), DEPARTMENT_APPROVER_ROLE);
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
        boolean canApprove = current != null && !instance.getRequesterId().equals(actor.user().getId())
                && (actor.superAdmin() || users.hasActiveRoleInDepartment(actor.user().getId(), current.getCandidateDeptId(), DEPARTMENT_APPROVER_ROLE));
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
