package org.lbl.intranet.dataasset.es.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
import org.lbl.intranet.dataasset.es.entity.EsIndexEntity;

@Mapper
public interface EsIndexMapper extends BaseMapper<EsIndexEntity> {
    @Select("SELECT COUNT(1) FROM intranet_es_index WHERE index_pattern = #{pattern} AND id != #{excludeId}")
    long countDuplicate(@Param("pattern") String pattern, @Param("excludeId") long excludeId);
    @Select("SELECT COUNT(1) FROM intranet_es_index WHERE hbase_table_id = #{tableId}")
    long countByHbaseTableId(@Param("tableId") Long tableId);
}
