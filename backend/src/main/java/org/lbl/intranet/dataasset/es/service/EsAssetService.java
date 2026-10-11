package org.lbl.intranet.dataasset.es.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.lbl.common.exception.BusinessException;
import org.lbl.common.result.PageResult;
import org.lbl.intranet.dataasset.es.entity.*;
import org.lbl.intranet.dataasset.es.mapper.*;
import org.lbl.intranet.dataasset.es.request.*;
import org.lbl.intranet.dataasset.es.vo.*;
import org.lbl.intranet.dataasset.hbase.entity.HbaseTableEntity;
import org.lbl.intranet.dataasset.hbase.mapper.HbaseTableMapper;
import org.lbl.intranet.dataasset.relation.mapper.TransactionEsResourceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import static org.lbl.common.util.TextValues.trimToNull;

@Service
public class EsAssetService {
    public static final String ROWKEY_FIELD_NAME = "id";
    private final EsIndexMapper indexes;
    private final EsFieldMapper fields;
    private final HbaseTableMapper hbaseTables;
    private final TransactionEsResourceMapper transactionResources;

    public EsAssetService(EsIndexMapper indexes, EsFieldMapper fields, HbaseTableMapper hbaseTables,
                          TransactionEsResourceMapper transactionResources) {
        this.indexes = indexes; this.fields = fields; this.hbaseTables = hbaseTables; this.transactionResources = transactionResources;
    }

    public PageResult<EsIndexVO> page(long page, long size, String keyword) {
        validatePage(page, size); String value = trimToNull(keyword);
        LambdaQueryWrapper<EsIndexEntity> q = new LambdaQueryWrapper<>();
        if (value != null) q.and(x -> x.like(EsIndexEntity::getIndexName, value).or().like(EsIndexEntity::getIndexPattern, value));
        q.orderByDesc(EsIndexEntity::getUpdatedTime).orderByDesc(EsIndexEntity::getId);
        Page<EsIndexEntity> result = indexes.selectPage(Page.of(page, size), q);
        return new PageResult<>(result.getRecords().stream().map(this::view).toList(), result.getTotal(), page, size);
    }
    public List<EsIndexVO> options() { return indexes.selectList(new LambdaQueryWrapper<EsIndexEntity>().orderByAsc(EsIndexEntity::getIndexName)).stream().map(this::view).toList(); }
    public EsIndexVO detail(Long id) { return view(requireIndex(id)); }
    @Transactional public EsIndexVO create(EsIndexRequest r) { EsIndexEntity e = new EsIndexEntity(); apply(e, r); requireUnique(e, 0); indexes.insert(e); return view(e); }
    @Transactional public EsIndexVO update(Long id, EsIndexRequest r) { EsIndexEntity e = requireIndex(id); apply(e, r); requireUnique(e, id); indexes.updateById(e); return view(e); }
    @Transactional public void remove(Long id) {
        requireIndex(id);
        if (transactionResources.countByEsIndexId(id) > 0) throw new BusinessException("ES 索引已被交易关联，请先解除关联");
        fields.deleteByIndexId(id); indexes.deleteById(id);
    }
    public List<EsFieldVO> fields(Long indexId) { requireIndex(indexId); return fields.selectList(new LambdaQueryWrapper<EsFieldEntity>().eq(EsFieldEntity::getEsIndexId, indexId).orderByAsc(EsFieldEntity::getSortOrder).orderByAsc(EsFieldEntity::getId)).stream().map(this::fieldView).toList(); }
    @Transactional public EsFieldVO createField(Long indexId, EsFieldRequest r) { requireIndex(indexId); EsFieldEntity e = new EsFieldEntity(); e.setEsIndexId(indexId); apply(e, r); requireFieldUnique(e, 0); fields.insert(e); return fieldView(e); }
    @Transactional public EsFieldVO updateField(Long indexId, Long id, EsFieldRequest r) {
        EsFieldEntity e = requireField(indexId, id);
        if (ROWKEY_FIELD_NAME.equals(e.getFieldName()) && !ROWKEY_FIELD_NAME.equals(r.fieldName().trim())) throw new BusinessException("RowKey 固定字段 id 不能改名");
        apply(e, r); requireFieldUnique(e, id); fields.updateById(e); return fieldView(e);
    }
    @Transactional public void removeField(Long indexId, Long id) {
        EsFieldEntity e = requireField(indexId, id);
        if (ROWKEY_FIELD_NAME.equals(e.getFieldName())) throw new BusinessException("RowKey 固定字段 id 不能删除");
        fields.deleteById(id);
    }
    public void requireReadyForTransaction(Long indexId) { requireIndex(indexId); if (fields.countRowkeyField(indexId) == 0) throw new BusinessException("ES 索引缺少 RowKey 固定字段 id"); }

    private void apply(EsIndexEntity e, EsIndexRequest r) { requireHbase(r.hbaseTableId()); e.setIndexName(r.indexName().trim()); e.setIndexPattern(r.indexPattern().trim()); e.setPartitionRule(trimToNull(r.partitionRule())); e.setHbaseTableId(r.hbaseTableId()); e.setDescription(trimToNull(r.description())); }
    private void apply(EsFieldEntity e, EsFieldRequest r) { e.setFieldName(r.fieldName().trim()); e.setDataType(r.dataType().trim()); e.setSearchableFlag(r.searchableFlag()); e.setSortableFlag(r.sortableFlag()); e.setDescription(trimToNull(r.description())); e.setSortOrder(r.sortOrder()); }
    private void requireUnique(EsIndexEntity e, long id) { if (indexes.countDuplicate(e.getIndexPattern(), id) > 0) throw new BusinessException("索引命名模式不能重复"); }
    private void requireFieldUnique(EsFieldEntity e, long id) { if (fields.countDuplicate(e.getEsIndexId(), e.getFieldName(), id) > 0) throw new BusinessException("同一 ES 索引下字段名不能重复"); }
    private EsIndexEntity requireIndex(Long id) { EsIndexEntity e = indexes.selectById(id); if (e == null) throw new BusinessException("ES 索引不存在或已删除"); return e; }
    private EsFieldEntity requireField(Long indexId, Long id) { EsFieldEntity e = fields.selectById(id); if (e == null || !indexId.equals(e.getEsIndexId())) throw new BusinessException("ES 字段不存在或已删除"); return e; }
    private HbaseTableEntity requireHbase(Long id) { HbaseTableEntity e = hbaseTables.selectById(id); if (e == null) throw new BusinessException("关联的 HBase 表不存在或已删除"); return e; }
    private EsIndexVO view(EsIndexEntity e) { HbaseTableEntity h = requireHbase(e.getHbaseTableId()); return new EsIndexVO(e.getId(), e.getIndexName(), e.getIndexPattern(), e.getPartitionRule(), e.getHbaseTableId(), h.getMarketName(), h.getTableName(), e.getDescription(), e.getCreatedBy(), e.getCreatedTime(), e.getUpdatedBy(), e.getUpdatedTime()); }
    private EsFieldVO fieldView(EsFieldEntity e) { return new EsFieldVO(e.getId(), e.getEsIndexId(), e.getFieldName(), e.getDataType(), e.getSearchableFlag(), e.getSortableFlag(), e.getDescription(), e.getSortOrder(), e.getCreatedBy(), e.getCreatedTime(), e.getUpdatedBy(), e.getUpdatedTime()); }
    private void validatePage(long page, long size) { if (page < 1 || size < 1 || size > 100) throw new BusinessException("分页参数无效，每页最多 100 条"); }
}
