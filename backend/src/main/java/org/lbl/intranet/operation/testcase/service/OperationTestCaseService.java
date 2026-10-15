package org.lbl.intranet.operation.testcase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.lbl.common.exception.BusinessException;
import org.lbl.intranet.operation.testcase.entity.OperationTestCaseEntity;
import org.lbl.intranet.operation.testcase.mapper.OperationTestCaseMapper;
import org.lbl.intranet.operation.testcase.request.OperationTestCaseCopyRequest;
import org.lbl.intranet.operation.testcase.request.OperationTestCaseRequest;
import org.lbl.intranet.operation.testcase.vo.OperationTestCaseVO;
import org.lbl.intranet.transaction.mapper.TransactionAssetMapper;
import org.lbl.intranet.transaction.version.entity.TransactionVersionEntity;
import org.lbl.intranet.transaction.version.mapper.TransactionVersionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OperationTestCaseService {
    public static final String DRAFT = "DRAFT";
    public static final String VERSION = "VERSION";

    private final OperationTestCaseMapper cases;
    private final TransactionAssetMapper transactions;
    private final TransactionVersionMapper versions;
    private final ObjectMapper json;

    public OperationTestCaseService(OperationTestCaseMapper cases, TransactionAssetMapper transactions,
                                    TransactionVersionMapper versions, ObjectMapper json) {
        this.cases = cases;
        this.transactions = transactions;
        this.versions = versions;
        this.json = json;
    }

    public List<OperationTestCaseVO> list(Long transactionId, String bindingType, Long transactionVersionId) {
        requireBinding(transactionId, bindingType, transactionVersionId);
        LambdaQueryWrapper<OperationTestCaseEntity> query = new LambdaQueryWrapper<OperationTestCaseEntity>()
                .eq(OperationTestCaseEntity::getTransactionId, transactionId)
                .eq(OperationTestCaseEntity::getBindingType, bindingType)
                .orderByAsc(OperationTestCaseEntity::getCaseName)
                .orderByAsc(OperationTestCaseEntity::getId);
        if (DRAFT.equals(bindingType)) query.isNull(OperationTestCaseEntity::getTransactionVersionId);
        else query.eq(OperationTestCaseEntity::getTransactionVersionId, transactionVersionId);
        return cases.selectList(query).stream().map(this::view).toList();
    }

    public OperationTestCaseVO detail(Long id) {
        return view(requireCase(id));
    }

    @Transactional
    public OperationTestCaseVO create(OperationTestCaseRequest request) {
        requireBinding(request.transactionId(), request.bindingType(), request.transactionVersionId());
        String name = request.caseName().trim();
        requireUniqueName(null, request.transactionId(), request.bindingType(), request.transactionVersionId(), name);
        OperationTestCaseEntity value = new OperationTestCaseEntity();
        apply(value, request, name);
        cases.insert(value);
        return view(value);
    }

    @Transactional
    public OperationTestCaseVO update(Long id, OperationTestCaseRequest request) {
        OperationTestCaseEntity value = requireCase(id);
        requireBinding(request.transactionId(), request.bindingType(), request.transactionVersionId());
        String name = request.caseName().trim();
        requireUniqueName(id, request.transactionId(), request.bindingType(), request.transactionVersionId(), name);
        apply(value, request, name);
        cases.updateById(value);
        return view(value);
    }

    @Transactional
    public OperationTestCaseVO copy(Long id, OperationTestCaseCopyRequest request) {
        OperationTestCaseEntity source = requireCase(id);
        requireBinding(source.getTransactionId(), request.bindingType(), request.transactionVersionId());
        String name = request.caseName() == null || request.caseName().isBlank()
                ? source.getCaseName() + " - 副本" : request.caseName().trim();
        requireUniqueName(null, source.getTransactionId(), request.bindingType(), request.transactionVersionId(), name);
        OperationTestCaseEntity target = new OperationTestCaseEntity();
        target.setTransactionId(source.getTransactionId());
        target.setBindingType(request.bindingType());
        target.setTransactionVersionId(VERSION.equals(request.bindingType()) ? request.transactionVersionId() : null);
        target.setCaseName(name);
        target.setDescription(source.getDescription());
        target.setRequestPayload(source.getRequestPayload());
        cases.insert(target);
        return view(target);
    }

    @Transactional
    public void remove(Long id) {
        requireCase(id);
        cases.deleteById(id);
    }

    private void apply(OperationTestCaseEntity target, OperationTestCaseRequest request, String name) {
        target.setTransactionId(request.transactionId());
        target.setBindingType(request.bindingType());
        target.setTransactionVersionId(VERSION.equals(request.bindingType()) ? request.transactionVersionId() : null);
        target.setCaseName(name);
        target.setDescription(blankToNull(request.description()));
        target.setRequestPayload(normalizePayload(request.requestPayload()));
    }

    private void requireBinding(Long transactionId, String bindingType, Long transactionVersionId) {
        if (transactions.selectById(transactionId) == null) throw new BusinessException("交易不存在或已删除");
        if (!DRAFT.equals(bindingType) && !VERSION.equals(bindingType)) throw new BusinessException("字段定义来源无效");
        if (DRAFT.equals(bindingType)) {
            if (transactionVersionId != null) throw new BusinessException("当前开发配置不能关联交易版本");
            return;
        }
        if (transactionVersionId == null) throw new BusinessException("请选择交易版本");
        TransactionVersionEntity version = versions.selectById(transactionVersionId);
        if (version == null || !transactionId.equals(version.getTransactionId())) throw new BusinessException("交易版本不属于当前交易");
    }

    private String normalizePayload(String payload) {
        try {
            JsonNode node = json.readTree(payload);
            if (node == null || !node.isObject()) throw new BusinessException("请求参数必须是 JSON 对象");
            return json.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("请求参数不是合法 JSON");
        }
    }

    private void requireUniqueName(Long excludedId, Long transactionId, String bindingType, Long transactionVersionId, String name) {
        LambdaQueryWrapper<OperationTestCaseEntity> query = new LambdaQueryWrapper<OperationTestCaseEntity>()
                .eq(OperationTestCaseEntity::getTransactionId, transactionId)
                .eq(OperationTestCaseEntity::getBindingType, bindingType)
                .eq(OperationTestCaseEntity::getCaseName, name);
        if (DRAFT.equals(bindingType)) query.isNull(OperationTestCaseEntity::getTransactionVersionId);
        else query.eq(OperationTestCaseEntity::getTransactionVersionId, transactionVersionId);
        if (excludedId != null) query.ne(OperationTestCaseEntity::getId, excludedId);
        if (cases.selectCount(query) > 0) throw new BusinessException("当前字段定义下已存在同名案例");
    }

    private OperationTestCaseEntity requireCase(Long id) {
        OperationTestCaseEntity value = cases.selectById(id);
        if (value == null) throw new BusinessException("测试案例不存在或已删除");
        return value;
    }

    private OperationTestCaseVO view(OperationTestCaseEntity value) {
        return new OperationTestCaseVO(value.getId(), value.getTransactionId(), value.getBindingType(),
                value.getTransactionVersionId(), value.getCaseName(), value.getDescription(), value.getRequestPayload(),
                value.getCreatedBy(), value.getCreatedTime(), value.getUpdatedBy(), value.getUpdatedTime());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
