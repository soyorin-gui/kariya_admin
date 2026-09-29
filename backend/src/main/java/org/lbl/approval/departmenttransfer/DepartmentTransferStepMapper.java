package org.lbl.approval.departmenttransfer;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface DepartmentTransferStepMapper extends BaseMapper<DepartmentTransferStepEntity> {
    @Select("SELECT * FROM sys_department_change_step WHERE request_id=#{requestId} ORDER BY step_order")
    List<DepartmentTransferStepEntity> byRequest(Long requestId);
    @Select("SELECT * FROM sys_department_change_step WHERE request_id=#{requestId} AND step_order=#{stepOrder} LIMIT 1")
    DepartmentTransferStepEntity one(Long requestId, Integer stepOrder);
}
