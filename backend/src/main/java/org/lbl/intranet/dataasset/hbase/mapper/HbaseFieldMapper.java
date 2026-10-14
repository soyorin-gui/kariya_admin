package org.lbl.intranet.dataasset.hbase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
import org.lbl.intranet.dataasset.hbase.entity.HbaseFieldEntity;

@Mapper
public interface HbaseFieldMapper extends BaseMapper<HbaseFieldEntity> {
    @Select("SELECT COUNT(1) FROM intranet_hbase_field WHERE hbase_table_id = #{tableId} AND column_family = #{family} AND field_name = #{fieldName} AND id != #{excludeId}")
    long countDuplicate(@Param("tableId") Long tableId, @Param("family") String family, @Param("fieldName") String fieldName, @Param("excludeId") long excludeId);
    @Delete("DELETE FROM intranet_hbase_field WHERE hbase_table_id = #{tableId}")
    int deleteByTableId(@Param("tableId") Long tableId);
}
