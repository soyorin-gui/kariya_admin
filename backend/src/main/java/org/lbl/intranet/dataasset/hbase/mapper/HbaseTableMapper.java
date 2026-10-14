package org.lbl.intranet.dataasset.hbase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
import org.lbl.intranet.dataasset.hbase.entity.HbaseTableEntity;

@Mapper
public interface HbaseTableMapper extends BaseMapper<HbaseTableEntity> {
    @Select("SELECT COUNT(1) FROM intranet_hbase_table WHERE market_name = #{marketName} AND table_name = #{tableName} AND id != #{excludeId}")
    long countDuplicate(@Param("marketName") String marketName, @Param("tableName") String tableName, @Param("excludeId") long excludeId);
}
