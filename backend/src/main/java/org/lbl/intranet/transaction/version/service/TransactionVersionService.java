package org.lbl.intranet.transaction.version.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.lbl.approval.core.entity.ApprovalInstanceEntity;
import org.lbl.approval.core.service.ApprovalWorkflowService;
import org.lbl.common.exception.BusinessException;
import org.lbl.intranet.dataasset.relation.entity.TransactionEsResourceEntity;
import org.lbl.intranet.dataasset.relation.entity.TransactionHbaseResourceEntity;
import org.lbl.intranet.dataasset.relation.mapper.TransactionEsResourceMapper;
import org.lbl.intranet.dataasset.relation.mapper.TransactionHbaseResourceMapper;
import org.lbl.intranet.requirement.entity.IntranetRequirementEntity;
import org.lbl.intranet.requirement.entity.RequirementFlowEntity;
import org.lbl.intranet.requirement.mapper.IntranetRequirementMapper;
import org.lbl.intranet.requirement.mapper.RequirementFlowMapper;
import org.lbl.intranet.transaction.entity.TransactionAssetEntity;
import org.lbl.intranet.transaction.field.entity.TransactionMessageFieldEntity;
import org.lbl.intranet.transaction.field.mapper.TransactionMessageFieldMapper;
import org.lbl.intranet.transaction.mapper.TransactionAssetMapper;
import org.lbl.intranet.transaction.version.entity.TransactionVersionEntity;
import org.lbl.intranet.transaction.version.entity.TransactionVersionEsResourceEntity;
import org.lbl.intranet.transaction.version.entity.TransactionVersionHbaseResourceEntity;
import org.lbl.intranet.transaction.version.entity.TransactionVersionMessageFieldEntity;
import org.lbl.intranet.transaction.version.mapper.TransactionVersionEsResourceMapper;
import org.lbl.intranet.transaction.version.mapper.TransactionVersionHbaseResourceMapper;
import org.lbl.intranet.transaction.version.mapper.TransactionVersionMapper;
import org.lbl.intranet.transaction.version.mapper.TransactionVersionMessageFieldMapper;
import org.lbl.intranet.transaction.version.vo.TransactionVersionVO;
import org.lbl.intranet.transaction.version.vo.TransactionVersionDetailVO;
import org.lbl.security.context.AccessPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TransactionVersionService {
    private static final String BUSINESS_TYPE = "INTRANET_REQUIREMENT";
    private final TransactionVersionMapper versions;
    private final TransactionVersionMessageFieldMapper versionFields;
    private final TransactionVersionHbaseResourceMapper versionHbaseResources;
    private final TransactionVersionEsResourceMapper versionEsResources;
    private final IntranetRequirementMapper requirements;
    private final TransactionAssetMapper transactions;
    private final TransactionMessageFieldMapper fields;
    private final TransactionHbaseResourceMapper hbaseResources;
    private final TransactionEsResourceMapper esResources;
    private final ApprovalWorkflowService approvals;
    private final AccessPolicy access;
    private final RequirementFlowMapper flows;

    public TransactionVersionService(TransactionVersionMapper versions, TransactionVersionMessageFieldMapper versionFields,
                                     TransactionVersionHbaseResourceMapper versionHbaseResources,
                                     TransactionVersionEsResourceMapper versionEsResources,
                                     IntranetRequirementMapper requirements, TransactionAssetMapper transactions,
                                     TransactionMessageFieldMapper fields, TransactionHbaseResourceMapper hbaseResources,
                                     TransactionEsResourceMapper esResources, ApprovalWorkflowService approvals, AccessPolicy access,
                                     RequirementFlowMapper flows) {
        this.versions = versions; this.versionFields = versionFields; this.versionHbaseResources = versionHbaseResources;
        this.versionEsResources = versionEsResources; this.requirements = requirements; this.transactions = transactions;
        this.fields = fields; this.hbaseResources = hbaseResources; this.esResources = esResources;
        this.approvals = approvals; this.access = access; this.flows = flows;
    }

    public List<TransactionVersionVO> list(Long transactionId) {
        if (transactions.selectById(transactionId) == null) throw new BusinessException("交易不存在或已删除");
        return versions.selectList(new LambdaQueryWrapper<TransactionVersionEntity>()
                        .eq(TransactionVersionEntity::getTransactionId, transactionId)
                        .orderByDesc(TransactionVersionEntity::getVersionNo))
                .stream().map(this::view).toList();
    }

    public TransactionVersionDetailVO detail(Long versionId) {
        TransactionVersionEntity version = versions.selectById(versionId);
        if (version == null) throw new BusinessException("交易版本不存在");
        List<TransactionVersionMessageFieldEntity> messageFields = versionFields.selectList(
                new LambdaQueryWrapper<TransactionVersionMessageFieldEntity>()
                        .eq(TransactionVersionMessageFieldEntity::getVersionId, versionId)
                        .orderByAsc(TransactionVersionMessageFieldEntity::getMessageSide)
                        .orderByAsc(TransactionVersionMessageFieldEntity::getSortOrder)
                        .orderByAsc(TransactionVersionMessageFieldEntity::getId));
        List<Long> hbaseIds = versionHbaseResources.selectList(new LambdaQueryWrapper<TransactionVersionHbaseResourceEntity>()
                        .eq(TransactionVersionHbaseResourceEntity::getVersionId, versionId))
                .stream().map(TransactionVersionHbaseResourceEntity::getHbaseTableId).toList();
        List<Long> esIds = versionEsResources.selectList(new LambdaQueryWrapper<TransactionVersionEsResourceEntity>()
                        .eq(TransactionVersionEsResourceEntity::getVersionId, versionId))
                .stream().map(TransactionVersionEsResourceEntity::getEsIndexId).toList();
        return new TransactionVersionDetailVO(version, messageFields, hbaseIds, esIds);
    }

    @Transactional
    public TransactionVersionVO activate(Long requirementId) {
        IntranetRequirementEntity requirement = requirements.selectById(requirementId);
        if (requirement == null) throw new BusinessException("需求不存在");
        if (!Integer.valueOf(2).equals(requirement.getStatus())) throw new BusinessException("仅开发中需求可以生成生效版本");
        if (Integer.valueOf(3).equals(requirement.getRequirementType())) throw new BusinessException("交易调用需求不生成交易版本");
        if (requirement.getTransactionId() == null) throw new BusinessException("请先分配交易");
        if (requirement.getDeveloperUserId() == null) throw new BusinessException("请先分配开发负责人");
        if (versions.countByRequirementId(requirementId) > 0) throw new BusinessException("该需求已经生成过交易版本");

        ApprovalInstanceEntity approval = approvals.get(BUSINESS_TYPE, requirementId);
        if (!ApprovalWorkflowService.APPROVED.equals(approval.getStatus())) throw new BusinessException("需求审批尚未通过");
        TransactionAssetEntity transaction = transactions.selectById(requirement.getTransactionId());
        if (transaction == null) throw new BusinessException("关联交易不存在或已删除");
        if (!Integer.valueOf(1).equals(transaction.getStatus())) throw new BusinessException("交易必须处于开发中才能投产");

        versions.update(null, new LambdaUpdateWrapper<TransactionVersionEntity>()
                .eq(TransactionVersionEntity::getTransactionId, transaction.getId())
                .eq(TransactionVersionEntity::getActiveFlag, 1)
                .set(TransactionVersionEntity::getActiveFlag, 0));

        var actor = access.actor();
        TransactionVersionEntity version = snapshot(transaction, requirement, approval, versions.maxVersionNo(transaction.getId()) + 1,
                actor.user().getId());
        versions.insert(version);
        snapshotFields(version.getId(), transaction.getId());
        snapshotResources(version.getId(), transaction.getId());

        transaction.setStatus(2);
        transactions.updateById(transaction);
        requirement.setStatus(5);
        requirements.updateById(requirement);
        RequirementFlowEntity flow = new RequirementFlowEntity();
        flow.setRequirementId(requirementId); flow.setEventType("DEPLOYED"); flow.setEventName("投产交易版本 V" + version.getVersionNo());
        flow.setOperatorId(actor.user().getId()); flow.setOperatorName(actor.user().getRealName()); flow.setCreatedTime(LocalDateTime.now());
        flows.insert(flow);
        return view(version);
    }

    private void snapshotFields(Long versionId, Long transactionId) {
        fields.selectList(new LambdaQueryWrapper<TransactionMessageFieldEntity>()
                        .eq(TransactionMessageFieldEntity::getTransactionId, transactionId)
                        .orderByAsc(TransactionMessageFieldEntity::getId))
                .forEach(source -> {
                    TransactionVersionMessageFieldEntity target = new TransactionVersionMessageFieldEntity();
                    target.setVersionId(versionId); target.setSourceFieldId(source.getId()); target.setMessageSide(source.getMessageSide());
                    target.setParentSourceFieldId(source.getParentId()); target.setNodeType(source.getNodeType());
                    target.setFieldNameCn(source.getFieldNameCn()); target.setFieldNameEn(source.getFieldNameEn());
                    target.setDataType(source.getDataType()); target.setDataLength(source.getDataLength());
                    target.setRequiredFlag(source.getRequiredFlag()); target.setDescription(source.getDescription()); target.setSortOrder(source.getSortOrder());
                    versionFields.insert(target);
                });
    }

    private void snapshotResources(Long versionId, Long transactionId) {
        hbaseResources.selectList(new LambdaQueryWrapper<TransactionHbaseResourceEntity>()
                        .eq(TransactionHbaseResourceEntity::getTransactionId, transactionId))
                .forEach(source -> { TransactionVersionHbaseResourceEntity target = new TransactionVersionHbaseResourceEntity(); target.setVersionId(versionId); target.setHbaseTableId(source.getHbaseTableId()); versionHbaseResources.insert(target); });
        esResources.selectList(new LambdaQueryWrapper<TransactionEsResourceEntity>()
                        .eq(TransactionEsResourceEntity::getTransactionId, transactionId))
                .forEach(source -> { TransactionVersionEsResourceEntity target = new TransactionVersionEsResourceEntity(); target.setVersionId(versionId); target.setEsIndexId(source.getEsIndexId()); versionEsResources.insert(target); });
    }

    private TransactionVersionEntity snapshot(TransactionAssetEntity source, IntranetRequirementEntity requirement,
                                              ApprovalInstanceEntity approval, int versionNo, Long actorId) {
        TransactionVersionEntity target = new TransactionVersionEntity();
        target.setTransactionId(source.getId()); target.setRequirementId(requirement.getId()); target.setApprovalInstanceId(approval.getId());
        target.setVersionNo(versionNo); target.setActiveFlag(1); target.setTransactionName(source.getTransactionName());
        target.setTransactionCode(source.getTransactionCode()); target.setDescription(source.getDescription()); target.setTransactionStatus(2);
        target.setEsfServiceName(source.getEsfServiceName()); target.setEsfServiceOperationId(source.getEsfServiceOperationId());
        target.setEsfServiceAddress(source.getEsfServiceAddress()); target.setEsfServiceOperationName(source.getEsfServiceOperationName());
        target.setLabel(source.getLabel()); target.setBusinessContact(source.getBusinessContact()); target.setDataTimeliness(source.getDataTimeliness());
        target.setPrintFileMode(source.getPrintFileMode()); target.setSortRule(source.getSortRule()); target.setDataValidationScope(source.getDataValidationScope());
        target.setQueryScope(source.getQueryScope()); target.setFileGenerationScope(source.getFileGenerationScope()); target.setFileNameRule(source.getFileNameRule());
        target.setActivatedBy(actorId); target.setActivatedTime(LocalDateTime.now()); target.setCreatedTime(LocalDateTime.now());
        return target;
    }

    private TransactionVersionVO view(TransactionVersionEntity value) {
        return new TransactionVersionVO(value.getId(), value.getTransactionId(), value.getRequirementId(), value.getVersionNo(),
                value.getActiveFlag(), value.getTransactionName(), value.getTransactionCode(), value.getActivatedBy(), value.getActivatedTime());
    }
}
