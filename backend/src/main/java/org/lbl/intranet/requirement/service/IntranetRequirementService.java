package org.lbl.intranet.requirement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.lbl.common.exception.BusinessException;
import org.lbl.common.result.PageResult;
import org.lbl.approval.core.entity.ApprovalInstanceEntity;
import org.lbl.approval.core.entity.ApprovalTaskEntity;
import org.lbl.approval.core.service.ApprovalWorkflowService;
import org.lbl.intranet.requirement.entity.IntranetRequirementEntity;
import org.lbl.intranet.requirement.entity.NewTransactionRequirementDetailEntity;
import org.lbl.intranet.requirement.entity.TransactionCallRequirementDetailEntity;
import org.lbl.intranet.requirement.entity.TransactionChangeRequirementDetailEntity;
import org.lbl.intranet.requirement.entity.RequirementFlowEntity;
import org.lbl.intranet.requirement.mapper.IntranetRequirementMapper;
import org.lbl.intranet.requirement.mapper.NewTransactionRequirementDetailMapper;
import org.lbl.intranet.requirement.mapper.TransactionCallRequirementDetailMapper;
import org.lbl.intranet.requirement.mapper.TransactionChangeRequirementDetailMapper;
import org.lbl.intranet.requirement.mapper.RequirementFlowMapper;
import org.lbl.intranet.requirement.request.RequirementRequest;
import org.lbl.intranet.requirement.request.AssignDeveloperRequest;
import org.lbl.intranet.requirement.request.AssignTransactionRequest;
import org.lbl.intranet.requirement.request.ConfigureImplementationRequest;
import org.lbl.intranet.transaction.entity.TransactionAssetEntity;
import org.lbl.intranet.transaction.mapper.TransactionAssetMapper;
import org.lbl.intranet.transaction.version.entity.TransactionVersionEntity;
import org.lbl.intranet.transaction.version.mapper.TransactionVersionMapper;
import org.lbl.notification.NotificationService;
import org.lbl.security.context.AccessPolicy;
import org.lbl.system.dept.entity.DeptEntity;
import org.lbl.system.dept.mapper.DeptMapper;
import org.lbl.system.user.entity.UserEntity;
import org.lbl.system.user.mapper.UserMapper;
import org.lbl.intranet.requirement.vo.RequirementListVO;
import org.lbl.intranet.requirement.vo.RequirementFlowVO;
import org.lbl.intranet.requirement.vo.RequirementImplementationOptionsVO;
import org.lbl.intranet.requirement.vo.SelectionOptionVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.function.Function;
import java.time.LocalDateTime;
import java.util.stream.Collectors;
import static org.lbl.common.util.TextValues.trimToNull;

@Service
public class IntranetRequirementService {
    private static final String BUSINESS_TYPE = "INTRANET_REQUIREMENT";
    private static final String APPROVER_ROLE = "department_approver";
    private static final String APPROVER_DEPT_CODE = "INTRANET_REQUIREMENT_APPROVAL";
    private static final String DEVELOPER_ROLE = "intranet_developer";
    private static final String DEVELOPER_DEPT_CODE = "INTRANET_DEVELOPMENT";
    private final IntranetRequirementMapper requirements;
    private final AccessPolicy access; private final ApprovalWorkflowService approvals; private final DeptMapper depts; private final UserMapper users; private final TransactionAssetMapper transactions; private final NewTransactionRequirementDetailMapper newDetails; private final TransactionCallRequirementDetailMapper callDetails; private final TransactionChangeRequirementDetailMapper changeDetails; private final TransactionVersionMapper versions; private final NotificationService notifications; private final RequirementFlowMapper flows;
    public IntranetRequirementService(IntranetRequirementMapper requirements, AccessPolicy access, ApprovalWorkflowService approvals, DeptMapper depts, UserMapper users, TransactionAssetMapper transactions, NewTransactionRequirementDetailMapper newDetails, TransactionCallRequirementDetailMapper callDetails, TransactionChangeRequirementDetailMapper changeDetails, TransactionVersionMapper versions, NotificationService notifications, RequirementFlowMapper flows) { this.requirements = requirements; this.access = access; this.approvals = approvals; this.depts = depts; this.users = users; this.transactions = transactions; this.newDetails = newDetails; this.callDetails = callDetails; this.changeDetails = changeDetails; this.versions = versions; this.notifications = notifications; this.flows = flows; }

    public PageResult<RequirementListVO> page(long pageNum, long pageSize, String keyword, Integer status, Integer type, String sortField, String sortOrder) {
        if (pageNum < 1 || pageSize < 1 || pageSize > 100) throw new BusinessException("分页参数无效，每页最多 100 条");
        LambdaQueryWrapper<IntranetRequirementEntity> q = new LambdaQueryWrapper<>();
        String value = trimToNull(keyword);
        if (value != null) q.and(x -> x.like(IntranetRequirementEntity::getOaNo, value).or().like(IntranetRequirementEntity::getTitle, value));
        if (status != null) q.eq(IntranetRequirementEntity::getStatus, status);
        if (type != null) q.eq(IntranetRequirementEntity::getRequirementType, type);
        if (sortField != null && !sortField.isBlank() && !"updatedTime".equals(sortField) && !"expectedOnlineDate".equals(sortField)) throw new BusinessException("不支持的排序字段");
        boolean ascending = "asc".equalsIgnoreCase(sortOrder);
        if ("expectedOnlineDate".equals(sortField)) q.orderBy(true, ascending, IntranetRequirementEntity::getExpectedOnlineDate);
        else q.orderBy(true, ascending, IntranetRequirementEntity::getUpdatedTime);
        q.orderByDesc(IntranetRequirementEntity::getId);
        Page<IntranetRequirementEntity> result = requirements.selectPage(Page.of(pageNum, pageSize), q);
        Map<Long, String> codes = result.getRecords().stream().map(IntranetRequirementEntity::getTransactionId).filter(java.util.Objects::nonNull).collect(Collectors.toMap(Function.identity(), id -> { TransactionAssetEntity t = transactions.selectById(id); return t == null ? "" : t.getTransactionCode(); }, (a,b) -> a));
        return new PageResult<>(result.getRecords().stream().map(v -> view(v, codes.get(v.getTransactionId()))).toList(), result.getTotal(), pageNum, pageSize);
    }

    public RequirementListVO get(Long id) { IntranetRequirementEntity v = require(id); return view(v, transactionCode(v.getTransactionId())); }
    public List<RequirementFlowVO> flow(Long id) { require(id); return flows.selectList(new LambdaQueryWrapper<RequirementFlowEntity>().eq(RequirementFlowEntity::getRequirementId, id).orderByAsc(RequirementFlowEntity::getCreatedTime).orderByAsc(RequirementFlowEntity::getId)).stream().map(v -> new RequirementFlowVO(v.getEventType(), v.getEventName(), v.getRemark(), v.getOperatorName(), v.getCreatedTime())).toList(); }
    public RequirementListVO getApprovalDetail(Long id) {
        IntranetRequirementEntity requirement = require(id);
        ApprovalInstanceEntity instance = approvals.get(BUSINESS_TYPE, id);
        if (instance == null) throw new BusinessException("该需求尚未进入审批流程");
        requireApprovalVisible(instance);
        return view(requirement);
    }
    public NewTransactionRequirementDetailEntity getNewTransactionDetail(Long id) { IntranetRequirementEntity requirement = require(id); if (requirement.getRequirementType() != 1) throw new BusinessException("该需求不是新增交易类型"); return newDetails.selectById(id); }
    @Transactional public NewTransactionRequirementDetailEntity saveNewTransactionDetail(Long id, NewTransactionRequirementDetailEntity detail) { IntranetRequirementEntity requirement = require(id); requireOwner(requirement); if (requirement.getRequirementType() != 1) throw new BusinessException("该需求不是新增交易类型"); if (requirement.getStatus() != 0 && requirement.getStatus() != 3) throw new BusinessException("仅草稿或已拒绝需求可以编辑明细"); detail.setRequirementId(id); if (newDetails.selectById(id) == null) newDetails.insert(detail); else newDetails.updateById(detail); return detail; }
    public TransactionCallRequirementDetailEntity getTransactionCallDetail(Long id) { IntranetRequirementEntity requirement = require(id); if (requirement.getRequirementType() != 3) throw new BusinessException("该需求不是交易调用类型"); return callDetails.selectById(id); }
    @Transactional public TransactionCallRequirementDetailEntity saveTransactionCallDetail(Long id, TransactionCallRequirementDetailEntity detail) { IntranetRequirementEntity requirement = require(id); requireOwner(requirement); if (requirement.getRequirementType() != 3) throw new BusinessException("该需求不是交易调用类型"); requireEditableDetail(requirement); detail.setRequirementId(id); if (callDetails.selectById(id) == null) callDetails.insert(detail); else callDetails.updateById(detail); return detail; }
    public TransactionChangeRequirementDetailEntity getTransactionChangeDetail(Long id) { IntranetRequirementEntity requirement = require(id); if (requirement.getRequirementType() != 2) throw new BusinessException("该需求不是交易变更类型"); return changeDetails.selectById(id); }
    @Transactional public TransactionChangeRequirementDetailEntity saveTransactionChangeDetail(Long id, TransactionChangeRequirementDetailEntity detail) { IntranetRequirementEntity requirement = require(id); requireOwner(requirement); if (requirement.getRequirementType() != 2) throw new BusinessException("该需求不是交易变更类型"); requireEditableDetail(requirement); detail.setRequirementId(id); if (changeDetails.selectById(id) == null) changeDetails.insert(detail); else changeDetails.updateById(detail); return detail; }
    @Transactional public RequirementListVO create(RequirementRequest request) {
        IntranetRequirementEntity value = new IntranetRequirementEntity(); apply(value, request); value.setStatus(0); requirements.insert(value); record(value.getId(), "DRAFT", "创建草稿", null); return view(value, null);
    }
    @Transactional public RequirementListVO update(Long id, RequirementRequest request) {
        IntranetRequirementEntity value = require(id);
        requireOwner(value);
        if (value.getStatus() != 0 && value.getStatus() != 3) throw new BusinessException("仅草稿或已拒绝需求可以编辑");
        if (!value.getRequirementType().equals(request.requirementType())) throw new BusinessException("需求类型创建后不可修改，如需调整请删除后重新新建");
        apply(value, request); requirements.updateById(value); return view(value);
    }
    @Transactional public RequirementListVO submit(Long id) {
        IntranetRequirementEntity value = require(id); requireOwner(value);
        if (value.getStatus() != 0 && value.getStatus() != 3) throw new BusinessException("仅草稿或已拒绝需求可以提交审批");
        DeptEntity dept = deptByCode(APPROVER_DEPT_CODE, "未初始化需求审批部门，请先执行部署初始化");
        ApprovalTaskEntity task = new ApprovalTaskEntity(); task.setStepOrder(1); task.setStepType("REQUIREMENT"); task.setStepName("需求审批"); task.setStatus("PENDING"); task.setCandidateDeptId(dept.getId()); task.setCandidateDeptName(dept.getDeptName()); task.setCandidateRoleCode(APPROVER_ROLE); task.setCandidateRoleName("部门审批人");
        String summary = "OA 联系单：" + value.getOaNo();
        ApprovalInstanceEntity existing;
        try { existing = approvals.get(BUSINESS_TYPE, id); } catch (BusinessException ignored) { existing = null; }
        boolean resubmitted = existing != null;
        ApprovalInstanceEntity instance;
        if (resubmitted) {
            instance = approvals.lock(BUSINESS_TYPE, id);
            approvals.resubmit(instance, value.getTitle(), summary, java.util.List.of(task));
        } else {
            var actor = access.actor(); instance = new ApprovalInstanceEntity(); instance.setBusinessType(BUSINESS_TYPE); instance.setBusinessId(value.getId()); instance.setBusinessTitle(value.getTitle()); instance.setBusinessSummary(summary); instance.setRequesterId(actor.user().getId()); instance.setRequesterName(actor.user().getRealName()); instance.setCurrentStep(1);
            approvals.create(instance, java.util.List.of(task));
        }
        value.setStatus(1); requirements.updateById(value); record(id, resubmitted ? "RESUBMITTED" : "SUBMITTED", resubmitted ? "重新提交审批" : "提交审批", null);
        notifySubmitted(instance, task, resubmitted);
        return view(value);
    }
    @Transactional public RequirementListVO approve(Long id) { return decide(id, true, null); }
    @Transactional public RequirementListVO reject(Long id, String reason) { if (reason == null || reason.isBlank()) throw new BusinessException("请填写拒绝原因"); return decide(id, false, reason.trim()); }
    @Transactional public RequirementListVO cancel(Long id) {
        IntranetRequirementEntity value = require(id); requireOwner(value);
        if (value.getStatus() != 1) throw new BusinessException("仅审批中的需求可以撤销");
        ApprovalInstanceEntity instance = approvals.lock(BUSINESS_TYPE, id);
        ApprovalTaskEntity task = approvals.currentTask(instance);
        approvals.cancel(instance); value.setStatus(0); requirements.updateById(value); record(id, "WITHDRAWN", "撤销审批，返回草稿", null);
        notifications.create(instance.getRequesterId(), "INTRANET_REQUIREMENT_CANCELLED", "内网需求已撤销", "你已撤销需求“" + value.getTitle() + "”，已返回草稿", BUSINESS_TYPE, id);
        notifications.approvalUpdated(participants(instance, task), id);
        return view(value);
    }
    @Transactional public RequirementListVO abandon(Long id) { IntranetRequirementEntity value = require(id); requireOwner(value); if (value.getStatus() != 0 && value.getStatus() != 3) throw new BusinessException("仅草稿或已拒绝需求可以废弃"); value.setStatus(4); requirements.updateById(value); record(id, "ABANDONED", "废弃需求", null); return view(value, transactionCode(value.getTransactionId())); }
    @Transactional public RequirementListVO publishCall(Long id) { IntranetRequirementEntity value = require(id); if (value.getStatus() != 2 || value.getRequirementType() != 3) throw new BusinessException("仅开发中的交易调用需求可以投产"); if (value.getTransactionId() == null) throw new BusinessException("请先在实施中关联交易码"); TransactionAssetEntity transaction = transactions.selectById(value.getTransactionId()); if (transaction == null || transaction.getStatus() == 3) throw new BusinessException("关联交易不可用"); value.setStatus(5); requirements.updateById(value); record(id, "DEPLOYED", "交易调用需求已投产", transaction.getTransactionCode()); return view(value, transaction.getTransactionCode()); }
    @Transactional public RequirementListVO assignDeveloper(Long id, AssignDeveloperRequest request) {
        IntranetRequirementEntity value = require(id);
        if (value.getStatus() != 2) throw new BusinessException("仅已通过需求可以分配开发负责人");
        if (value.getRequirementType() == 3) throw new BusinessException("交易调用需求不分配开发负责人");
        requireVersionNotActivated(id);
        DeptEntity dept = deptByCode(DEVELOPER_DEPT_CODE, "未初始化内网研发部门，请先执行 V13 脚本");
        if (!users.hasActiveRoleInDepartment(request.developerUserId(), dept.getId(), DEVELOPER_ROLE)) throw new BusinessException("所选用户不是内网研发部的开发人员");
        UserEntity user = users.selectById(request.developerUserId()); if (user == null || user.getStatus() != 1) throw new BusinessException("开发人员不存在或已停用");
        value.setDeveloperUserId(user.getId()); value.setDeveloperName(user.getRealName()); requirements.updateById(value); return view(value);
    }
    public RequirementImplementationOptionsVO implementationOptions(Long id) {
        IntranetRequirementEntity value = require(id);
        if (value.getStatus() != 2) throw new BusinessException("仅开发中的需求可以配置实施信息");
        LambdaQueryWrapper<TransactionAssetEntity> query = new LambdaQueryWrapper<TransactionAssetEntity>()
                .ne(TransactionAssetEntity::getStatus, 3);
        if (value.getRequirementType() == 1) {
            if (value.getTransactionId() == null) query.eq(TransactionAssetEntity::getStatus, 0);
            else query.and(condition -> condition.eq(TransactionAssetEntity::getStatus, 0)
                    .or().eq(TransactionAssetEntity::getId, value.getTransactionId()));
        }
        query.orderByAsc(TransactionAssetEntity::getTransactionCode);
        List<SelectionOptionVO> transactionOptions = transactions.selectList(query).stream()
                .map(item -> new SelectionOptionVO(item.getId(), item.getTransactionCode() + " · " + item.getTransactionName())).toList();
        List<SelectionOptionVO> developerOptions = value.getRequirementType() == 3 ? List.of() : users.selectList(new LambdaQueryWrapper<UserEntity>()
                        .in(UserEntity::getId, users.selectActiveIdsByRoleInDepartment(deptByCode(DEVELOPER_DEPT_CODE, "未初始化内网研发部门，请先执行部署初始化").getId(), DEVELOPER_ROLE)).orderByAsc(UserEntity::getRealName)).stream()
                .filter(user -> user.getStatus() == 1)
                .map(user -> new SelectionOptionVO(user.getId(), user.getRealName())).toList();
        TransactionVersionEntity activated = versions.selectByRequirementId(id);
        return new RequirementImplementationOptionsVO(transactionOptions, developerOptions, activated == null ? null : activated.getVersionNo());
    }
    @Transactional public RequirementListVO configureImplementation(Long id, ConfigureImplementationRequest request) {
        IntranetRequirementEntity value = require(id);
        if (value.getStatus() != 2) throw new BusinessException("仅开发中的需求可以配置实施信息");
        requireVersionNotActivated(id);
        if (value.getTransactionId() != null && !value.getTransactionId().equals(request.transactionId())) throw new BusinessException("已分配的交易不能更换");
        TransactionAssetEntity transaction = transactions.selectById(request.transactionId());
        if (transaction == null || transaction.getStatus() == 3) throw new BusinessException("只能选择未下线的交易");
        if (value.getRequirementType() == 1 && transaction.getStatus() != 0) throw new BusinessException("新增交易需求只能选择评估中的交易");
        if (value.getRequirementType() != 3 && requirements.selectCount(new LambdaQueryWrapper<IntranetRequirementEntity>()
                .eq(IntranetRequirementEntity::getTransactionId, transaction.getId())
                .eq(IntranetRequirementEntity::getStatus, 2)
                .ne(IntranetRequirementEntity::getId, id)) > 0) {
            throw new BusinessException("该交易已有开发中的交易需求，不能重复实施");
        }
        if (value.getRequirementType() != 3) {
            if (request.developerUserId() == null) throw new BusinessException("请选择开发负责人");
            DeptEntity dept = deptByCode(DEVELOPER_DEPT_CODE, "未初始化内网研发部门，请先执行部署初始化");
            if (!users.hasActiveRoleInDepartment(request.developerUserId(), dept.getId(), DEVELOPER_ROLE)) throw new BusinessException("所选用户不是内网研发部的开发人员");
            UserEntity developer = users.selectById(request.developerUserId()); if (developer == null || developer.getStatus() != 1) throw new BusinessException("开发人员不存在或已停用");
            value.setDeveloperUserId(developer.getId()); value.setDeveloperName(developer.getRealName());
            transaction.setStatus(1); transactions.updateById(transaction);
        }
        value.setTransactionId(transaction.getId()); requirements.updateById(value); record(id, "IMPLEMENTED", "完成实施配置", transaction.getTransactionCode());
        return view(value);
    }
    @Transactional public RequirementListVO assignTransaction(Long id, AssignTransactionRequest request) {
        IntranetRequirementEntity value = require(id);
        if (value.getStatus() != 2) throw new BusinessException("仅已通过需求可以分配交易码");
        if (value.getRequirementType() == 3) throw new BusinessException("交易调用需求不在此处分配交易");
        requireVersionNotActivated(id);
        if (value.getTransactionId() != null) throw new BusinessException("该需求已分配交易码，不能重复分配");
        TransactionAssetEntity transaction = transactions.selectById(request.transactionId());
        if (transaction == null || transaction.getStatus() == 3) throw new BusinessException("只能选择未下线的交易");
        if (value.getRequirementType() == 1 && transaction.getStatus() != 0) throw new BusinessException("新增交易需求只能选择评估中的交易");
        value.setTransactionId(transaction.getId()); requirements.updateById(value); return view(value);
    }
    private RequirementListVO decide(Long id, boolean approved, String reason) {
        IntranetRequirementEntity value = require(id); ApprovalInstanceEntity instance = approvals.lock(BUSINESS_TYPE, id); ApprovalTaskEntity task = approvals.currentTask(instance); var actor = access.actor();
        if (task == null || !"PENDING".equals(task.getStatus()) || (!actor.superAdmin() && !users.hasActiveRoleInDepartment(actor.user().getId(), task.getCandidateDeptId(), APPROVER_ROLE))) throw new BusinessException("当前步骤不属于你审批");
        if (instance.getRequesterId().equals(actor.user().getId())) throw new BusinessException("不能审批自己提交的需求");
        if (approved) { approvals.approveCurrent(instance, task, actor.user().getId(), actor.user().getRealName(), null, null); value.setStatus(2); record(id, "APPROVED", "审批通过，进入开发中", null); } else { approvals.rejectCurrent(instance, task, actor.user().getId(), actor.user().getRealName(), reason); value.setStatus(3); record(id, "REJECTED", "审批拒绝", reason); }
        requirements.updateById(value);
        notifications.create(instance.getRequesterId(), approved ? "INTRANET_REQUIREMENT_APPROVED" : "INTRANET_REQUIREMENT_REJECTED", approved ? "内网需求已通过" : "内网需求已拒绝", approved ? "需求“" + value.getTitle() + "”审批已通过" : "需求“" + value.getTitle() + "”审批未通过：" + reason, BUSINESS_TYPE, id);
        notifications.approvalUpdated(participants(instance, task), id);
        return view(value);
    }
    private void notifySubmitted(ApprovalInstanceEntity instance, ApprovalTaskEntity task, boolean resubmitted) {
        notifications.create(instance.getRequesterId(), resubmitted ? "INTRANET_REQUIREMENT_RESUBMITTED" : "INTRANET_REQUIREMENT_SUBMITTED", resubmitted ? "内网需求已重新提交" : "内网需求已提交", "需求“" + instance.getBusinessTitle() + "”已进入审批流程", BUSINESS_TYPE, instance.getBusinessId());
        for (Long id : approvers(task)) {
            if (!id.equals(instance.getRequesterId())) notifications.create(id, "INTRANET_REQUIREMENT_APPROVAL_REQUIRED", "待审批的内网需求", instance.getRequesterName() + "提交了需求“" + instance.getBusinessTitle() + "”", BUSINESS_TYPE, instance.getBusinessId());
        }
        notifications.approvalUpdated(participants(instance, task), instance.getBusinessId());
    }
    private Set<Long> participants(ApprovalInstanceEntity instance, ApprovalTaskEntity task) { Set<Long> ids = approvers(task); ids.add(instance.getRequesterId()); if (task != null && task.getDecidedBy() != null) ids.add(task.getDecidedBy()); return ids; }
    private Set<Long> approvers(ApprovalTaskEntity task) { Set<Long> ids = new LinkedHashSet<>(users.selectActiveSuperAdminIds()); if (task != null && task.getCandidateDeptId() != null && task.getCandidateRoleCode() != null) ids.addAll(users.selectActiveIdsByRoleInDepartment(task.getCandidateDeptId(), task.getCandidateRoleCode())); return ids; }
    private void requireApprovalVisible(ApprovalInstanceEntity instance) {
        var actor = access.actor();
        if (actor.superAdmin() || instance.getRequesterId().equals(actor.user().getId())) return;
        boolean visible = approvals.tasks(instance).stream().anyMatch(task ->
                actor.user().getId().equals(task.getAssignedUserId())
                        || actor.user().getId().equals(task.getDecidedBy())
                        || task.getCandidateDeptId() != null && task.getCandidateRoleCode() != null
                        && users.hasActiveRoleInDepartment(actor.user().getId(), task.getCandidateDeptId(), task.getCandidateRoleCode()));
        if (!visible) throw new BusinessException("无权查看该需求审批");
    }
    private DeptEntity deptByCode(String code, String message) { DeptEntity value = depts.selectOne(new LambdaQueryWrapper<DeptEntity>().eq(DeptEntity::getDeptCode, code).eq(DeptEntity::getStatus, 1)); if (value == null) throw new BusinessException(message); return value; }
    private void requireOwner(IntranetRequirementEntity value) { if (!access.actor().superAdmin() && !access.actor().user().getId().equals(value.getCreatedBy())) throw new BusinessException("无权修改该需求"); }
    private void requireEditableDetail(IntranetRequirementEntity value) { if (value.getStatus() != 0 && value.getStatus() != 3) throw new BusinessException("仅草稿或已拒绝需求可以编辑明细"); }
    private void requireVersionNotActivated(Long requirementId) { if (versions.countByRequirementId(requirementId) > 0) throw new BusinessException("交易版本已生效，不能修改实施配置"); }
    private IntranetRequirementEntity require(Long id) { IntranetRequirementEntity value = requirements.selectById(id); if (value == null) throw new BusinessException("需求不存在"); return value; }
    private void apply(IntranetRequirementEntity value, RequirementRequest request) { value.setOaNo(request.oaNo().trim()); value.setRequirementType(request.requirementType()); value.setTitle(request.title().trim()); value.setDescription(trimToNull(request.description())); value.setExpectedOnlineDate(request.expectedOnlineDate()); }
    private String transactionCode(Long id) { if (id == null) return null; TransactionAssetEntity t = transactions.selectById(id); return t == null ? null : t.getTransactionCode(); }
    private void record(Long id, String type, String name, String remark) { var actor = access.actor(); RequirementFlowEntity event = new RequirementFlowEntity(); event.setRequirementId(id); event.setEventType(type); event.setEventName(name); event.setRemark(remark); event.setOperatorId(actor.user().getId()); event.setOperatorName(actor.user().getRealName()); event.setCreatedTime(LocalDateTime.now()); flows.insert(event); }
    private RequirementListVO view(IntranetRequirementEntity v) { return view(v, transactionCode(v.getTransactionId())); }
    private RequirementListVO view(IntranetRequirementEntity v, String code) { return new RequirementListVO(v.getId(), v.getOaNo(), v.getRequirementType(), v.getTitle(), v.getDescription(), v.getExpectedOnlineDate(), v.getTransactionId(), code, v.getStatus(), v.getDeveloperUserId(), v.getDeveloperName(), v.getCreatedBy(), v.getCreatedTime(), v.getUpdatedTime()); }
}
