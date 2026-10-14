package org.lbl.intranet.dataasset.hbase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.lbl.common.exception.BusinessException;
import org.lbl.common.result.PageResult;
import org.lbl.intranet.dataasset.es.mapper.EsIndexMapper;
import org.lbl.intranet.dataasset.hbase.entity.*;
import org.lbl.intranet.dataasset.hbase.mapper.*;
import org.lbl.intranet.dataasset.hbase.request.*;
import org.lbl.intranet.dataasset.hbase.vo.*;
import org.lbl.intranet.dataasset.relation.mapper.TransactionHbaseResourceMapper;
import org.lbl.intranet.lineage.service.LineageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import static org.lbl.common.util.TextValues.trimToNull;

@Service
public class HbaseAssetService {
    private final HbaseTableMapper tables;
    private final HbaseFieldMapper fields;
    private final EsIndexMapper esIndexes;
    private final TransactionHbaseResourceMapper transactionResources;
    private final LineageService lineage;

    public HbaseAssetService(HbaseTableMapper tables, HbaseFieldMapper fields, EsIndexMapper esIndexes,
                             TransactionHbaseResourceMapper transactionResources, LineageService lineage) {
        this.tables = tables; this.fields = fields; this.esIndexes = esIndexes; this.transactionResources = transactionResources; this.lineage = lineage;
    }

    public PageResult<HbaseTableVO> page(long page, long size, String keyword) {
        validatePage(page, size); String value = trimToNull(keyword);
        LambdaQueryWrapper<HbaseTableEntity> q = new LambdaQueryWrapper<>();
        if (value != null) q.and(x -> x.like(HbaseTableEntity::getMarketName, value).or().like(HbaseTableEntity::getTableName, value));
        q.orderByDesc(HbaseTableEntity::getUpdatedTime).orderByDesc(HbaseTableEntity::getId);
        Page<HbaseTableEntity> result = tables.selectPage(Page.of(page, size), q);
        return new PageResult<>(result.getRecords().stream().map(this::view).toList(), result.getTotal(), page, size);
    }
    public List<HbaseTableVO> options() { return tables.selectList(new LambdaQueryWrapper<HbaseTableEntity>().orderByAsc(HbaseTableEntity::getMarketName).orderByAsc(HbaseTableEntity::getTableName)).stream().map(this::view).toList(); }
    public HbaseTableVO detail(Long id) { return view(requireTable(id)); }
    @Transactional public HbaseTableVO create(HbaseTableRequest r) { HbaseTableEntity e = new HbaseTableEntity(); apply(e, r); requireUnique(e, 0); tables.insert(e); return view(e); }
    @Transactional public HbaseTableVO update(Long id, HbaseTableRequest r) { HbaseTableEntity e = requireTable(id); apply(e, r); requireUnique(e, id); tables.updateById(e); return view(e); }
    @Transactional public void remove(Long id) {
        requireTable(id);
        if (esIndexes.countByHbaseTableId(id) > 0) throw new BusinessException("HBase 表已被 ES 索引引用，请先解除关联");
        if (transactionResources.countByHbaseTableId(id) > 0) throw new BusinessException("HBase 表已被交易关联，请先解除关联");
        fields.selectList(new LambdaQueryWrapper<HbaseFieldEntity>().eq(HbaseFieldEntity::getHbaseTableId, id)).forEach(field -> lineage.removeSource("HBASE_FIELD", field.getId()));
        fields.deleteByTableId(id); tables.deleteById(id);
    }
    public List<HbaseFieldVO> fields(Long tableId) { requireTable(tableId); return fields.selectList(new LambdaQueryWrapper<HbaseFieldEntity>().eq(HbaseFieldEntity::getHbaseTableId, tableId).orderByAsc(HbaseFieldEntity::getSortOrder).orderByAsc(HbaseFieldEntity::getId)).stream().map(this::fieldView).toList(); }
    @Transactional public HbaseFieldVO createField(Long tableId, HbaseFieldRequest r) { requireTable(tableId); HbaseFieldEntity e = new HbaseFieldEntity(); e.setHbaseTableId(tableId); apply(e, r); requireFieldUnique(e, 0); fields.insert(e); return fieldView(e); }
    @Transactional public HbaseFieldVO updateField(Long tableId, Long id, HbaseFieldRequest r) { HbaseFieldEntity e = requireField(tableId, id); apply(e, r); requireFieldUnique(e, id); fields.updateById(e); return fieldView(e); }
    @Transactional public void removeField(Long tableId, Long id) { HbaseFieldEntity field = requireField(tableId, id); lineage.removeSource("HBASE_FIELD", field.getId()); fields.deleteById(field.getId()); }

    private void apply(HbaseTableEntity e, HbaseTableRequest r) { e.setMarketName(r.marketName().trim()); e.setTableName(r.tableName().trim()); e.setDescription(trimToNull(r.description())); e.setRowkeyRule(trimToNull(r.rowkeyRule())); e.setLockKey(trimToNull(r.lockKey())); e.setDataVolume(trimToNull(r.dataVolume())); e.setDataDepth(trimToNull(r.dataDepth())); }
    private void apply(HbaseFieldEntity e, HbaseFieldRequest r) { e.setColumnFamily(r.columnFamily().trim()); e.setFieldName(r.fieldName().trim()); e.setDataType(trimToNull(r.dataType())); e.setDescription(trimToNull(r.description())); e.setSortOrder(r.sortOrder()); }
    private void requireUnique(HbaseTableEntity e, long id) { if (tables.countDuplicate(e.getMarketName(), e.getTableName(), id) > 0) throw new BusinessException("同一集市下 HBase 表名不能重复"); }
    private void requireFieldUnique(HbaseFieldEntity e, long id) { if (fields.countDuplicate(e.getHbaseTableId(), e.getColumnFamily(), e.getFieldName(), id) > 0) throw new BusinessException("同一 HBase 表列族下字段名不能重复"); }
    private HbaseTableEntity requireTable(Long id) { HbaseTableEntity e = tables.selectById(id); if (e == null) throw new BusinessException("HBase 表不存在或已删除"); return e; }
    private HbaseFieldEntity requireField(Long tableId, Long id) { HbaseFieldEntity e = fields.selectById(id); if (e == null || !tableId.equals(e.getHbaseTableId())) throw new BusinessException("HBase 字段不存在或已删除"); return e; }
    private HbaseTableVO view(HbaseTableEntity e) { return new HbaseTableVO(e.getId(), e.getMarketName(), e.getTableName(), e.getDescription(), e.getRowkeyRule(), e.getLockKey(), e.getDataVolume(), e.getDataDepth(), e.getCreatedBy(), e.getCreatedTime(), e.getUpdatedBy(), e.getUpdatedTime()); }
    private HbaseFieldVO fieldView(HbaseFieldEntity e) { return new HbaseFieldVO(e.getId(), e.getHbaseTableId(), e.getColumnFamily(), e.getFieldName(), e.getDataType(), e.getDescription(), e.getSortOrder(), e.getCreatedBy(), e.getCreatedTime(), e.getUpdatedBy(), e.getUpdatedTime()); }
    private void validatePage(long page, long size) { if (page < 1 || size < 1 || size > 100) throw new BusinessException("分页参数无效，每页最多 100 条"); }
}
