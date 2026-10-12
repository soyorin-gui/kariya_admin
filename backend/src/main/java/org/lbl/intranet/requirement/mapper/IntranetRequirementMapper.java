package org.lbl.intranet.requirement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.lbl.intranet.requirement.entity.IntranetRequirementEntity;

@Mapper
public interface IntranetRequirementMapper extends BaseMapper<IntranetRequirementEntity> {
    @Select("SELECT COUNT(1) FROM intranet_requirement WHERE transaction_id = #{transactionId} AND id != #{excludeId}")
    long countByTransactionId(@Param("transactionId") Long transactionId, @Param("excludeId") Long excludeId);
}
