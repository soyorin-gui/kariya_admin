package org.lbl.approval.departmenttransfer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.lbl.common.exception.BusinessException;
import org.lbl.approval.departmenttransfer.DepartmentTransferModels.*;
import org.lbl.notification.NotificationService;
import org.lbl.security.context.AccessPolicy;
import org.lbl.system.dept.entity.DeptEntity;
import org.lbl.system.dept.mapper.DeptMapper;
import org.lbl.system.user.entity.UserEntity;
import org.lbl.system.user.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class DepartmentTransferApprovalService {
    private final DepartmentTransferRequestMapper requests;
    private final DepartmentTransferStepMapper steps;
    private final UserMapper users;
    private final DeptMapper depts;
    private final AccessPolicy access;
    private final NotificationService notifications;

    public DepartmentTransferApprovalService(DepartmentTransferRequestMapper requests, DepartmentTransferStepMapper steps,
                                   UserMapper users, DeptMapper depts, AccessPolicy access,
                                   NotificationService notifications) {
        this.requests = requests; this.steps = steps; this.users = users; this.depts = depts;
        this.access = access; this.notifications = notifications;
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
        return new Profile(user.getDeptId(), dept == null ? null : dept.getDeptName(), active == null ? null : detail(active, access.actor()));
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
        request.setRequesterId(requester.getId()); request.setFromDeptId(requester.getDeptId());
        request.setTargetDeptId(target.getId()); request.setReason(form.reason().trim());
        request.setStatus(needsSource ? "PENDING_SOURCE" : "PENDING_TARGET");
        request.setCurrentStep(needsSource ? 1 : 2); request.setVersion(1L);
        requests.insert(request);

        DepartmentTransferStepEntity source = step(request.getId(), 1, "SOURCE", requester.getDeptId(),
                needsSource ? reviewer(requester.getDeptId(), requester.getId()) : null,
                needsSource ? "PENDING" : "SKIPPED");
        DepartmentTransferStepEntity targetStep = step(request.getId(), 2, "TARGET", target.getId(),
                needsSource ? null : targetReviewer(actor, requester, target.getId()), needsSource ? "WAITING" : "PENDING");
        steps.insert(source); steps.insert(targetStep);

        DepartmentTransferStepEntity currentStep = needsSource ? source : targetStep;
        String currentDeptLabel = needsSource ? "原部门" : "目标部门";
        notifications.create(requester.getId(), "DEPARTMENT_REQUEST_SUBMITTED", "部门变更申请已提交",
                "申请已提交，" + reviewerPhrase(currentStep, currentDeptLabel),
                "DEPARTMENT_CHANGE", request.getId());
        notifyCurrentReviewer(request, currentStep, requester);
        notifySuperAdmins(request, "新的部门变更申请", requester.getRealName() + "提交了部门变更申请"
                + (currentStep.getAssignedUserId() == null
                        ? "（" + currentDeptLabel + "步骤需由超级管理员处理）" : ""));
        return detail(request, actor);
    }

    public Detail get(Long id) {
        AccessPolicy.Actor actor = access.actor();
        DepartmentTransferRequestEntity request = require(id);
        requireVisible(request, actor);
        return detail(request, actor);
    }

    @Transactional
    public Detail approve(Long id) { return decide(id, true, null); }

    @Transactional
    public Detail reject(Long id, String reason) {
        if (reason == null || reason.isBlank()) throw new BusinessException("请填写拒绝原因");
        return decide(id, false, reason.trim());
    }

    @Transactional
    public Detail cancel(Long id) {
        AccessPolicy.Actor actor = access.actor();
        DepartmentTransferRequestEntity request = requests.lockById(id);
        if (request == null || !request.getRequesterId().equals(actor.user().getId())) throw new BusinessException("申请不存在");
        requirePending(request);
        request.setStatus("CANCELLED"); request.setFinishedTime(LocalDateTime.now()); request.setVersion(request.getVersion() + 1);
        requests.updateById(request);
        notifyParticipants(request, "部门变更申请已撤销", "申请人已撤销本次部门变更申请");
        return detail(request, actor);
    }

    private Detail decide(Long id, boolean approved, String reason) {
        AccessPolicy.Actor actor = access.actor();
        DepartmentTransferRequestEntity request = requests.lockById(id);
        if (request == null) throw new BusinessException("申请不存在");
        requirePending(request);
        DepartmentTransferStepEntity current = steps.one(id, request.getCurrentStep());
        if (current == null || !"PENDING".equals(current.getStatus())) throw new BusinessException("当前审批步骤状态异常");
        if (!actor.superAdmin() && !actor.user().getId().equals(current.getAssignedUserId())) throw new BusinessException("当前步骤不属于你审批");
        if (request.getRequesterId().equals(actor.user().getId())) throw new BusinessException("不能审批自己的部门变更申请");

        UserEntity requester = users.selectByIdForUpdate(request.getRequesterId());
        DeptEntity target = depts.selectById(request.getTargetDeptId());
        if (requester == null || requester.getStatus() != 1 || target == null || target.getStatus() != 1
                || !Objects.equals(requester == null ? null : requester.getDeptId(), request.getFromDeptId())) {
            request.setStatus("CANCELLED"); request.setFinishedTime(LocalDateTime.now()); request.setVersion(request.getVersion() + 1);
            requests.updateById(request);
            notifyParticipants(request, "部门变更申请已失效", "用户当前部门或目标部门已经发生变化，请重新提交申请");
            return detail(request, actor);
        }

        current.setStatus(approved ? "APPROVED" : "REJECTED"); current.setDecidedBy(actor.user().getId());
        current.setDecisionReason(reason); current.setDecidedTime(LocalDateTime.now()); steps.updateById(current);
        if (!approved) {
            request.setStatus("REJECTED"); request.setFinishedTime(LocalDateTime.now()); request.setVersion(request.getVersion() + 1);
            requests.updateById(request);
            notifyParticipants(request, "部门变更申请已拒绝", "审批未通过：" + reason);
            return detail(request, actor);
        }

        if (request.getCurrentStep() == 1) {
            DepartmentTransferStepEntity targetStep = steps.one(id, 2);
            targetStep.setAssignedUserId(targetReviewer(actor, requester, request.getTargetDeptId()));
            targetStep.setStatus("PENDING"); steps.updateById(targetStep);
            request.setCurrentStep(2); request.setStatus("PENDING_TARGET"); request.setVersion(request.getVersion() + 1);
            requests.updateById(request);
            notifications.create(request.getRequesterId(), "DEPARTMENT_REQUEST_PROGRESS", "原部门审批已通过",
                    "申请已进入目标部门审批阶段，" + reviewerPhrase(targetStep, "目标部门"),
                    "DEPARTMENT_CHANGE", request.getId());
            notifyCurrentReviewer(request, targetStep, requester);
            notifications.approvalUpdated(participants(request), request.getId());
            return detail(request, actor);
        }

        if (!actor.superAdmin() && access.requiresPlatformTransferReview(actor, requester))
            throw new BusinessException("该用户的部门角色范围会随调动改变，请由超级管理员审批");
        requester.setDeptId(request.getTargetDeptId()); users.updateById(requester);
        request.setStatus("APPROVED"); request.setFinishedTime(LocalDateTime.now()); request.setVersion(request.getVersion() + 1);
        requests.updateById(request);
        notifyParticipants(request, "部门变更申请已通过", "全部审批完成，用户部门已更新为“" + target.getDeptName() + "”");
        return detail(request, actor);
    }

    private DepartmentTransferStepEntity step(Long requestId, int order, String type, Long deptId, Long assigned, String status) {
        DepartmentTransferStepEntity value = new DepartmentTransferStepEntity();
        value.setRequestId(requestId); value.setStepOrder(order); value.setStepType(type); value.setDeptId(deptId);
        value.setAssignedUserId(assigned); value.setStatus(status); return value;
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

    /**
     * 当前步骤「在等谁审批」的统一文案。
     * <p>
     * 不能无条件写死「等待原部门负责人审批」：{@link #reviewer} 在三种情况下会返回 {@code null} ——
     * 部门尚未配置负责人、配置的负责人账号已停用、负责人就是申请人本人（不允许自审）。
     * 此时唯一能批的是超级管理员（见 {@link #decide} 的权限判定：{@code superAdmin || id == assignedUserId}），
     * 所以文案必须跟着这个事实走。写死"部门负责人"会让申请人一直等一个永远不会发生的结果，
     * 而且界面上看不出任何异常 —— 这是排查成本最高的一类问题。
     */
    private String reviewerPhrase(DepartmentTransferStepEntity step, String deptLabel) {
        return step.getAssignedUserId() == null
                ? deptLabel + "步骤需由超级管理员审批（无有效负责人或调动涉及角色数据范围变更）"
                : "等待" + deptLabel + "负责人审批";
    }

    private void notifyCurrentReviewer(DepartmentTransferRequestEntity request, DepartmentTransferStepEntity step, UserEntity requester) {
        if (step.getAssignedUserId() != null) notifications.create(step.getAssignedUserId(), "DEPARTMENT_APPROVAL_REQUIRED",
                "待审批的部门变更申请", requester.getRealName() + "提交了部门变更申请，请处理当前步骤",
                "DEPARTMENT_CHANGE", request.getId());
    }

    private void notifySuperAdmins(DepartmentTransferRequestEntity request, String title, String content) {
        for (Long id : users.selectActiveSuperAdminIds()) if (!id.equals(request.getRequesterId()))
            notifications.create(id, "DEPARTMENT_APPROVAL_WATCH", title, content, "DEPARTMENT_CHANGE", request.getId());
    }

    private void notifyParticipants(DepartmentTransferRequestEntity request, String title, String content) {
        for (Long id : participants(request)) notifications.create(id, "DEPARTMENT_REQUEST_RESULT", title, content,
                "DEPARTMENT_CHANGE", request.getId());
        notifications.approvalUpdated(participants(request), request.getId());
    }

    private Set<Long> participants(DepartmentTransferRequestEntity request) {
        Set<Long> ids = new LinkedHashSet<>(); ids.add(request.getRequesterId()); ids.addAll(users.selectActiveSuperAdminIds());
        for (DepartmentTransferStepEntity step : steps.byRequest(request.getId())) {
            if (step.getAssignedUserId() != null) ids.add(step.getAssignedUserId());
            if (step.getDecidedBy() != null) ids.add(step.getDecidedBy());
        }
        return ids;
    }

    private void requireVisible(DepartmentTransferRequestEntity request, AccessPolicy.Actor actor) {
        if (actor.superAdmin() || participants(request).contains(actor.user().getId())) return;
        throw new BusinessException("无权查看该申请");
    }
    private void requirePending(DepartmentTransferRequestEntity request) {
        if (!Set.of("PENDING_SOURCE", "PENDING_TARGET").contains(request.getStatus())) throw new BusinessException("该申请已经处理完成");
    }
    private DepartmentTransferRequestEntity require(Long id) {
        DepartmentTransferRequestEntity value = requests.selectById(id);
        if (value == null) throw new BusinessException("申请不存在"); return value;
    }

    private Detail detail(DepartmentTransferRequestEntity request, AccessPolicy.Actor actor) {
        UserEntity requester = users.selectById(request.getRequesterId());
        DeptEntity from = request.getFromDeptId() == null ? null : depts.selectById(request.getFromDeptId());
        DeptEntity target = depts.selectById(request.getTargetDeptId());
        List<Step> stepViews = steps.byRequest(request.getId()).stream().map(value -> {
            DeptEntity dept = value.getDeptId() == null ? null : depts.selectById(value.getDeptId());
            UserEntity assigned = value.getAssignedUserId() == null ? null : users.selectById(value.getAssignedUserId());
            UserEntity decided = value.getDecidedBy() == null ? null : users.selectById(value.getDecidedBy());
            return new Step(value.getId(), value.getStepOrder(), value.getStepType(), value.getDeptId(),
                    dept == null ? null : dept.getDeptName(), value.getAssignedUserId(), assigned == null ? null : assigned.getRealName(),
                    value.getStatus(), value.getDecidedBy(), decided == null ? null : decided.getRealName(),
                    value.getDecisionReason(), value.getDecidedTime());
        }).toList();
        DepartmentTransferStepEntity current = Set.of("PENDING_SOURCE", "PENDING_TARGET").contains(request.getStatus())
                ? steps.one(request.getId(), request.getCurrentStep()) : null;
        boolean requiresPlatform = current != null && current.getStepOrder() == 2 && requester != null
                && access.requiresPlatformTransferReview(actor, requester);
        boolean canApprove = current != null && !request.getRequesterId().equals(actor.user().getId())
                && (actor.superAdmin() || !requiresPlatform && actor.user().getId().equals(current.getAssignedUserId()));
        return new Detail(request.getId(), request.getRequesterId(), requester == null ? "未知用户" : requester.getRealName(),
                request.getFromDeptId(), from == null ? "未分配部门" : from.getDeptName(), request.getTargetDeptId(),
                target == null ? "已删除部门" : target.getDeptName(), request.getReason(), request.getStatus(),
                request.getCurrentStep(), request.getCreatedTime(), request.getFinishedTime(), canApprove,
                request.getRequesterId().equals(actor.user().getId()) && current != null, stepViews);
    }
}
