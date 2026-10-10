package org.lbl.intranet.transaction.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.lbl.common.exception.BusinessException;
import org.lbl.common.result.PageResult;
import org.lbl.intranet.transaction.entity.TransactionAssetEntity;
import org.lbl.intranet.transaction.mapper.TransactionAssetMapper;
import org.lbl.intranet.transaction.request.TransactionRequest;
import org.lbl.intranet.transaction.vo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static org.lbl.common.util.TextValues.trimToNull;

@Service
public class TransactionAssetService {
    private final TransactionAssetMapper mapper;
    public TransactionAssetService(TransactionAssetMapper mapper) { this.mapper = mapper; }
    public PageResult<TransactionListVO> page(long page, long size, String keyword, Integer status, String sortField, String sortOrder) {
        if (page < 1 || size < 1 || size > 100) throw new BusinessException("分页参数无效，每页最多 100 条");
        if (status != null && (status < 0 || status > 3)) throw new BusinessException("交易状态无效");
        LambdaQueryWrapper<TransactionAssetEntity> q = new LambdaQueryWrapper<>(); String value = trimToNull(keyword);
        if (value != null) q.and(x -> x.like(TransactionAssetEntity::getTransactionName, value).or().like(TransactionAssetEntity::getTransactionCode, value).or().like(TransactionAssetEntity::getEsfServiceName, value));
        if (status != null) q.eq(TransactionAssetEntity::getStatus, status);
        if (sortField != null && !sortField.isBlank() && !"updatedTime".equals(sortField)) throw new BusinessException("不支持的排序字段");
        q.orderBy(true, "asc".equalsIgnoreCase(sortOrder), TransactionAssetEntity::getUpdatedTime).orderByDesc(TransactionAssetEntity::getId);
        Page<TransactionAssetEntity> result = mapper.selectPage(Page.of(page, size), q);
        return new PageResult<>(result.getRecords().stream().map(this::toList).toList(), result.getTotal(), page, size);
    }
    public TransactionVO detail(Long id) { return toView(require(id)); }
    @Transactional public TransactionVO create(TransactionRequest r) { String code = r.transactionCode().trim(); requireUnique(code, 0); TransactionAssetEntity e = new TransactionAssetEntity(); apply(e, r, code); mapper.insert(e); return toView(e); }
    @Transactional public TransactionVO update(Long id, TransactionRequest r) { TransactionAssetEntity e = require(id); String code = r.transactionCode().trim(); requireUnique(code, id); apply(e, r, code); mapper.updateById(e); return toView(e); }
    @Transactional public void remove(Long id) { mapper.deleteById(require(id).getId()); }
    private TransactionAssetEntity require(Long id) { TransactionAssetEntity e = mapper.selectById(id); if (e == null) throw new BusinessException("交易不存在或已删除"); return e; }
    private void requireUnique(String code, long id) { if (mapper.countByTransactionCode(code, id) > 0) throw new BusinessException("交易编码已被占用"); }
    private void apply(TransactionAssetEntity e, TransactionRequest r, String code) {
        e.setTransactionName(r.transactionName().trim()); e.setTransactionCode(code); e.setDescription(trimToNull(r.description())); e.setStatus(r.status());
        e.setEsfServiceName(r.esfServiceName().trim()); e.setEsfServiceOperationId(r.esfServiceOperationId().trim()); e.setEsfServiceAddress(r.esfServiceAddress().trim()); e.setEsfServiceOperationName(r.esfServiceOperationName().trim());
        e.setLabel(trimToNull(r.label())); e.setBusinessContact(trimToNull(r.businessContact())); e.setDataTimeliness(trimToNull(r.dataTimeliness())); e.setPrintFileMode(r.printFileMode()); e.setSortRule(trimToNull(r.sortRule())); e.setDataValidationScope(trimToNull(r.dataValidationScope())); e.setQueryScope(trimToNull(r.queryScope()));
        if (r.printFileMode() == 0) { e.setFileGenerationScope(null); e.setFileNameRule(null); } else { e.setFileGenerationScope(trimToNull(r.fileGenerationScope())); e.setFileNameRule(trimToNull(r.fileNameRule())); }
    }
    private TransactionListVO toList(TransactionAssetEntity e) { return new TransactionListVO(e.getId(), e.getTransactionName(), e.getTransactionCode(), e.getStatus(), e.getEsfServiceName(), e.getEsfServiceOperationName(), e.getLabel(), e.getBusinessContact(), e.getUpdatedTime()); }
    private TransactionVO toView(TransactionAssetEntity e) { return new TransactionVO(e.getId(), e.getTransactionName(), e.getTransactionCode(), e.getDescription(), e.getStatus(), e.getEsfServiceName(), e.getEsfServiceOperationId(), e.getEsfServiceAddress(), e.getEsfServiceOperationName(), e.getLabel(), e.getBusinessContact(), e.getDataTimeliness(), e.getPrintFileMode(), e.getSortRule(), e.getDataValidationScope(), e.getQueryScope(), e.getFileGenerationScope(), e.getFileNameRule(), e.getCreatedBy(), e.getCreatedTime(), e.getUpdatedBy(), e.getUpdatedTime()); }
}
