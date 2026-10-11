package org.lbl.approval.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.lbl.approval.core.entity.ApprovalInstanceEntity;

@Mapper
public interface ApprovalInstanceMapper extends BaseMapper<ApprovalInstanceEntity> {
    @Select("SELECT * FROM sys_approval_instance WHERE business_type=#{businessType} AND business_id=#{businessId} LIMIT 1")
    ApprovalInstanceEntity selectByBusiness(@Param("businessType") String businessType,
                                            @Param("businessId") Long businessId);

    @Select("SELECT * FROM sys_approval_instance WHERE business_type=#{businessType} AND business_id=#{businessId} FOR UPDATE")
    ApprovalInstanceEntity lockByBusiness(@Param("businessType") String businessType,
                                          @Param("businessId") Long businessId);
}
