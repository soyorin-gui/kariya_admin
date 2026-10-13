package org.lbl.intranet.dataasset.relation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.lbl.common.exception.BusinessException;
import org.lbl.intranet.dataasset.es.service.EsAssetService;
import org.lbl.intranet.dataasset.hbase.mapper.HbaseTableMapper;
import org.lbl.intranet.dataasset.relation.entity.TransactionEsResourceEntity;
import org.lbl.intranet.dataasset.relation.entity.TransactionHbaseResourceEntity;
import org.lbl.intranet.dataasset.relation.mapper.TransactionEsResourceMapper;
import org.lbl.intranet.dataasset.relation.mapper.TransactionHbaseResourceMapper;
import org.lbl.intranet.dataasset.relation.request.TransactionDataResourceRequest;
import org.lbl.intranet.dataasset.relation.vo.TransactionDataResourceVO;
import org.lbl.intranet.transaction.mapper.TransactionAssetMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;

@Service
public class TransactionDataResourceService {
    private final TransactionAssetMapper transactions;
    private final HbaseTableMapper hbaseTables;
    private final EsAssetService esAssets;
    private final TransactionHbaseResourceMapper hbaseResources;
    private final TransactionEsResourceMapper esResources;

    public TransactionDataResourceService(TransactionAssetMapper transactions, HbaseTableMapper hbaseTables,
                                          EsAssetService esAssets, TransactionHbaseResourceMapper hbaseResources,
                                          TransactionEsResourceMapper esResources) {
        this.transactions = transactions;
        this.hbaseTables = hbaseTables;
        this.esAssets = esAssets;
        this.hbaseResources = hbaseResources;
        this.esResources = esResources;
    }

    public TransactionDataResourceVO detail(Long transactionId) {
        requireTransaction(transactionId, false);
        List<Long> hbaseIds = hbaseResources.selectList(new LambdaQueryWrapper<TransactionHbaseResourceEntity>()
                        .eq(TransactionHbaseResourceEntity::getTransactionId, transactionId)
                        .orderByAsc(TransactionHbaseResourceEntity::getId))
                .stream().map(TransactionHbaseResourceEntity::getHbaseTableId).toList();
        List<Long> esIds = esResources.selectList(new LambdaQueryWrapper<TransactionEsResourceEntity>()
                        .eq(TransactionEsResourceEntity::getTransactionId, transactionId)
                        .orderByAsc(TransactionEsResourceEntity::getId))
                .stream().map(TransactionEsResourceEntity::getEsIndexId).toList();
        return new TransactionDataResourceVO(hbaseIds, esIds);
    }

    @Transactional
    public TransactionDataResourceVO replace(Long transactionId, TransactionDataResourceRequest request) {
        requireTransaction(transactionId, true);
        List<Long> hbaseIds = normalize(request.hbaseTableIds(), "HBase 表");
        List<Long> esIds = normalize(request.esIndexIds(), "ES 索引");
        for (Long tableId : hbaseIds) {
            if (hbaseTables.selectById(tableId) == null) throw new BusinessException("关联的 HBase 表不存在或已删除");
        }
        esIds.forEach(esAssets::requireReadyForTransaction);

        hbaseResources.deleteByTransactionId(transactionId);
        esResources.deleteByTransactionId(transactionId);
        hbaseIds.forEach(tableId -> {
            TransactionHbaseResourceEntity entity = new TransactionHbaseResourceEntity();
            entity.setTransactionId(transactionId);
            entity.setHbaseTableId(tableId);
            hbaseResources.insert(entity);
        });
        esIds.forEach(indexId -> {
            TransactionEsResourceEntity entity = new TransactionEsResourceEntity();
            entity.setTransactionId(transactionId);
            entity.setEsIndexId(indexId);
            esResources.insert(entity);
        });
        return new TransactionDataResourceVO(hbaseIds, esIds);
    }

    private void requireTransaction(Long id, boolean editable) {
        var transaction = transactions.selectById(id);
        if (transaction == null) throw new BusinessException("交易不存在或已删除");
        if (editable && transaction.getStatus() != 0 && transaction.getStatus() != 1) throw new BusinessException("已投产或已下线交易不可修改数据资源，请通过交易变更需求实施");
    }

    private List<Long> normalize(List<Long> ids, String label) {
        if (ids.stream().anyMatch(id -> id == null || id <= 0)) throw new BusinessException(label + "编号无效");
        return List.copyOf(new LinkedHashSet<>(ids));
    }
}
