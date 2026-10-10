package org.lbl.intranet.transaction.field.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.lbl.common.exception.BusinessException;
import org.lbl.intranet.transaction.field.entity.TransactionMessageFieldEntity;
import org.lbl.intranet.transaction.field.mapper.TransactionMessageFieldMapper;
import org.lbl.intranet.transaction.field.request.TransactionMessageFieldRequest;
import org.lbl.intranet.transaction.field.vo.TransactionMessageFieldVO;
import org.lbl.intranet.transaction.mapper.TransactionAssetMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import static org.lbl.common.util.TextValues.trimToNull;

@Service
public class TransactionMessageFieldService {
    private final TransactionMessageFieldMapper mapper;
    private final TransactionAssetMapper transactions;
    public TransactionMessageFieldService(TransactionMessageFieldMapper mapper, TransactionAssetMapper transactions) { this.mapper = mapper; this.transactions = transactions; }
    public List<TransactionMessageFieldVO> list(Long transactionId, String side) {
        requireSide(side);
        requireTransaction(transactionId);
        return mapper.selectList(new LambdaQueryWrapper<TransactionMessageFieldEntity>().eq(TransactionMessageFieldEntity::getTransactionId, transactionId).eq(TransactionMessageFieldEntity::getMessageSide, side).orderByAsc(TransactionMessageFieldEntity::getParentId).orderByAsc(TransactionMessageFieldEntity::getSortOrder).orderByAsc(TransactionMessageFieldEntity::getId)).stream().map(this::toView).toList();
    }
    @Transactional public TransactionMessageFieldVO create(Long transactionId, String side, TransactionMessageFieldRequest r) {
        requireSide(side); requireTransaction(transactionId); requireParent(transactionId, side, r.parentId(), null);
        TransactionMessageFieldEntity e = new TransactionMessageFieldEntity(); e.setTransactionId(transactionId); e.setMessageSide(side); apply(e, r); mapper.insert(e); return toView(e);
    }
    @Transactional public TransactionMessageFieldVO update(Long transactionId, String side, Long id, TransactionMessageFieldRequest r) {
        requireSide(side); requireTransaction(transactionId); TransactionMessageFieldEntity e = require(transactionId, side, id); requireParent(transactionId, side, r.parentId(), id); apply(e, r); mapper.updateById(e); return toView(e);
    }
    @Transactional public void remove(Long transactionId, String side, Long id) {
        requireTransaction(transactionId); TransactionMessageFieldEntity e = require(transactionId, side, id);
        if (mapper.selectCount(new LambdaQueryWrapper<TransactionMessageFieldEntity>().eq(TransactionMessageFieldEntity::getTransactionId, transactionId).eq(TransactionMessageFieldEntity::getMessageSide, side).eq(TransactionMessageFieldEntity::getParentId, id)) > 0) throw new BusinessException("请先删除该节点的子字段");
        mapper.deleteById(e.getId());
    }
    private void requireParent(Long transactionId, String side, Long parentId, Long selfId) {
        if (parentId == null || parentId == 0) return;
        if (selfId != null && selfId.equals(parentId)) throw new BusinessException("父节点不能选择自身");
        require(transactionId, side, parentId);
    }
    private TransactionMessageFieldEntity require(Long transactionId, String side, Long id) { TransactionMessageFieldEntity e = mapper.selectOne(new LambdaQueryWrapper<TransactionMessageFieldEntity>().eq(TransactionMessageFieldEntity::getId, id).eq(TransactionMessageFieldEntity::getTransactionId, transactionId).eq(TransactionMessageFieldEntity::getMessageSide, side)); if (e == null) throw new BusinessException("报文字段不存在"); return e; }
    private static void requireSide(String side) { if (!"REQUEST".equals(side) && !"RESPONSE".equals(side)) throw new BusinessException("报文方向无效"); }
    private void requireTransaction(Long transactionId) { if (transactions.selectById(transactionId) == null) throw new BusinessException("交易不存在或已删除"); }
    private void apply(TransactionMessageFieldEntity e, TransactionMessageFieldRequest r) { e.setParentId(r.parentId()); e.setNodeType(r.nodeType()); e.setFieldNameCn(r.fieldNameCn().trim()); e.setFieldNameEn(r.fieldNameEn().trim()); e.setDataType(trimToNull(r.dataType())); e.setDataLength(trimToNull(r.dataLength())); e.setRequiredFlag(r.requiredFlag()); e.setDescription(trimToNull(r.description())); e.setSortOrder(r.sortOrder()); }
    private TransactionMessageFieldVO toView(TransactionMessageFieldEntity e) { return new TransactionMessageFieldVO(e.getId(), e.getTransactionId(), e.getMessageSide(), e.getParentId(), e.getNodeType(), e.getFieldNameCn(), e.getFieldNameEn(), e.getDataType(), e.getDataLength(), e.getRequiredFlag(), e.getDescription(), e.getSortOrder()); }
}
