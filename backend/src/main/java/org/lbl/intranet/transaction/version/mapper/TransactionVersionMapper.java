package org.lbl.intranet.transaction.version.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.lbl.intranet.transaction.version.entity.TransactionVersionEntity;

@Mapper
public interface TransactionVersionMapper extends BaseMapper<TransactionVersionEntity> {
    @Select("SELECT COALESCE(MAX(version_no), 0) FROM intranet_transaction_version WHERE transaction_id=#{transactionId}")
    int maxVersionNo(@Param("transactionId") Long transactionId);

    @Select("SELECT COUNT(1) FROM intranet_transaction_version WHERE requirement_id=#{requirementId}")
    long countByRequirementId(@Param("requirementId") Long requirementId);

    @Select("SELECT * FROM intranet_transaction_version WHERE requirement_id=#{requirementId} LIMIT 1")
    TransactionVersionEntity selectByRequirementId(@Param("requirementId") Long requirementId);
}
