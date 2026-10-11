package org.lbl.intranet.dataasset.es.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
import org.lbl.intranet.dataasset.es.entity.EsFieldEntity;

@Mapper
public interface EsFieldMapper extends BaseMapper<EsFieldEntity> {
    @Select("SELECT COUNT(1) FROM intranet_es_field WHERE es_index_id = #{indexId} AND field_name = #{fieldName} AND id != #{excludeId}")
    long countDuplicate(@Param("indexId") Long indexId, @Param("fieldName") String fieldName, @Param("excludeId") long excludeId);
    @Select("SELECT COUNT(1) FROM intranet_es_field WHERE es_index_id = #{indexId} AND field_name = 'id'")
    long countRowkeyField(@Param("indexId") Long indexId);
    @Delete("DELETE FROM intranet_es_field WHERE es_index_id = #{indexId}")
    int deleteByIndexId(@Param("indexId") Long indexId);
}
