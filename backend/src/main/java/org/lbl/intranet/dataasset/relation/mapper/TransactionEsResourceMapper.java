package org.lbl.intranet.dataasset.relation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
import org.lbl.intranet.dataasset.relation.entity.TransactionEsResourceEntity;

@Mapper
public interface TransactionEsResourceMapper extends BaseMapper<TransactionEsResourceEntity> {
    @Select("SELECT COUNT(1) FROM intranet_transaction_es_resource WHERE es_index_id = #{indexId}")
    long countByEsIndexId(@Param("indexId") Long indexId);
    @Delete("DELETE FROM intranet_transaction_es_resource WHERE transaction_id = #{transactionId}")
    int deleteByTransactionId(@Param("transactionId") Long transactionId);
}
