package org.lbl.approval.departmenttransfer;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

@Mapper
public interface DepartmentTransferRequestMapper extends BaseMapper<DepartmentTransferRequestEntity> {
    @Select("SELECT r.* FROM sys_department_change r JOIN sys_approval_instance a"
            + " ON a.business_type='DEPARTMENT_CHANGE' AND a.business_id=r.id"
            + " WHERE r.requester_id=#{userId} AND a.status='PENDING' ORDER BY r.id DESC LIMIT 1")
    DepartmentTransferRequestEntity activeByRequester(Long userId);
}
