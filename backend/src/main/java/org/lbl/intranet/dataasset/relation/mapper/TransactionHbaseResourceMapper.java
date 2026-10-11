package org.lbl.intranet.dataasset.relation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
import org.lbl.intranet.dataasset.relation.entity.TransactionHbaseResourceEntity;

@Mapper
public interface TransactionHbaseResourceMapper extends BaseMapper<TransactionHbaseResourceEntity> {
    @Select("SELECT COUNT(1) FROM intranet_transaction_hbase_resource WHERE hbase_table_id = #{tableId}")
    long countByHbaseTableId(@Param("tableId") Long tableId);
    @Delete("DELETE FROM intranet_transaction_hbase_resource WHERE transaction_id = #{transactionId}")
    int deleteByTransactionId(@Param("transactionId") Long transactionId);
}
