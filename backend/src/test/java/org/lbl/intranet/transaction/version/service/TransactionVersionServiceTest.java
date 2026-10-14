package org.lbl.intranet.transaction.version.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.lbl.approval.core.entity.ApprovalInstanceEntity;
import org.lbl.approval.core.service.ApprovalWorkflowService;
import org.lbl.intranet.dataasset.relation.entity.TransactionEsResourceEntity;
import org.lbl.intranet.dataasset.relation.entity.TransactionHbaseResourceEntity;
import org.lbl.intranet.dataasset.relation.mapper.TransactionEsResourceMapper;
import org.lbl.intranet.dataasset.relation.mapper.TransactionHbaseResourceMapper;
import org.lbl.intranet.requirement.entity.IntranetRequirementEntity;
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
import org.lbl.security.context.AccessPolicy;
import org.lbl.system.user.entity.UserEntity;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionVersionServiceTest {
    @Mock TransactionVersionMapper versions;
    @Mock TransactionVersionMessageFieldMapper versionFields;
    @Mock TransactionVersionHbaseResourceMapper versionHbaseResources;
    @Mock TransactionVersionEsResourceMapper versionEsResources;
    @Mock IntranetRequirementMapper requirements;
    @Mock TransactionAssetMapper transactions;
    @Mock TransactionMessageFieldMapper fields;
    @Mock TransactionHbaseResourceMapper hbaseResources;
    @Mock TransactionEsResourceMapper esResources;
    @Mock ApprovalWorkflowService approvals;
    @Mock AccessPolicy access;
    @Mock RequirementFlowMapper flows;

    @BeforeEach
    void initializeLambdaMetadata() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, "test-version"), TransactionVersionEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, "test-field"), TransactionMessageFieldEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, "test-hbase"), TransactionHbaseResourceEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, "test-es"), TransactionEsResourceEntity.class);
    }

    @Test
    void activateCreatesImmutableSnapshotAndPublishesTransaction() {
        IntranetRequirementEntity requirement = new IntranetRequirementEntity();
        requirement.setId(10L); requirement.setRequirementType(2); requirement.setStatus(2);
        requirement.setTransactionId(20L); requirement.setDeveloperUserId(30L);
        when(requirements.selectById(10L)).thenReturn(requirement);
        when(versions.countByRequirementId(10L)).thenReturn(0L);
        when(versions.maxVersionNo(20L)).thenReturn(2);

        ApprovalInstanceEntity approval = new ApprovalInstanceEntity();
        approval.setId(40L); approval.setStatus(ApprovalWorkflowService.APPROVED);
        when(approvals.get("INTRANET_REQUIREMENT", 10L)).thenReturn(approval);

        TransactionAssetEntity transaction = new TransactionAssetEntity();
        transaction.setId(20L); transaction.setStatus(1); transaction.setTransactionName("查询交易");
        transaction.setTransactionCode("TX001"); transaction.setEsfServiceName("QueryService");
        transaction.setEsfServiceOperationId("query"); transaction.setEsfServiceAddress("esf://query");
        transaction.setEsfServiceOperationName("查询"); transaction.setPrintFileMode(0);
        when(transactions.selectById(20L)).thenReturn(transaction);

        TransactionMessageFieldEntity field = new TransactionMessageFieldEntity();
        field.setId(50L); field.setMessageSide("REQUEST"); field.setParentId(0L); field.setNodeType("FIELD");
        field.setFieldNameCn("账号"); field.setFieldNameEn("accountNo"); field.setRequiredFlag(1); field.setSortOrder(1);
        when(fields.selectList(any())).thenReturn(List.of(field));
        TransactionHbaseResourceEntity hbase = new TransactionHbaseResourceEntity(); hbase.setHbaseTableId(60L);
        when(hbaseResources.selectList(any())).thenReturn(List.of(hbase));
        TransactionEsResourceEntity es = new TransactionEsResourceEntity(); es.setEsIndexId(70L);
        when(esResources.selectList(any())).thenReturn(List.of(es));

        UserEntity user = new UserEntity(); user.setId(80L);
        AccessPolicy.Actor actor = mock(AccessPolicy.Actor.class);
        when(actor.user()).thenReturn(user); when(access.actor()).thenReturn(actor);
        when(versions.insert(any(TransactionVersionEntity.class))).thenAnswer(invocation -> { ((TransactionVersionEntity) invocation.getArgument(0)).setId(90L); return 1; });

        TransactionVersionService service = new TransactionVersionService(versions, versionFields, versionHbaseResources,
                versionEsResources, requirements, transactions, fields, hbaseResources, esResources, approvals, access, flows);

        var result = service.activate(10L);

        assertEquals(3, result.versionNo());
        assertEquals(1, result.activeFlag());
        ArgumentCaptor<TransactionVersionEntity> version = ArgumentCaptor.forClass(TransactionVersionEntity.class);
        verify(versions).insert(version.capture());
        assertEquals(10L, version.getValue().getRequirementId());
        assertEquals(40L, version.getValue().getApprovalInstanceId());
        assertEquals(80L, version.getValue().getActivatedBy());
        verify(versionFields).insert(any(TransactionVersionMessageFieldEntity.class));
        verify(versionHbaseResources).insert(any(TransactionVersionHbaseResourceEntity.class));
        verify(versionEsResources).insert(any(TransactionVersionEsResourceEntity.class));
        verify(transactions).updateById(transaction);
        assertEquals(2, transaction.getStatus());
    }
}
