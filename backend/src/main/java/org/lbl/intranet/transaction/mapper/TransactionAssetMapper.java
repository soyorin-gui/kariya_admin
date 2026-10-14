package org.lbl.intranet.transaction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
import org.lbl.intranet.transaction.entity.TransactionAssetEntity;

@Mapper
public interface TransactionAssetMapper extends BaseMapper<TransactionAssetEntity> {
    @Select("SELECT COUNT(1) FROM intranet_transaction WHERE transaction_code = #{code} AND id != #{excludeId}")
    long countByTransactionCode(@Param("code") String code, @Param("excludeId") long excludeId);
}
