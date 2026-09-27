package org.lbl.departmentchange;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface DepartmentChangeStepMapper extends BaseMapper<DepartmentChangeStepEntity> {
    @Select("SELECT * FROM sys_department_change_step WHERE request_id=#{requestId} ORDER BY step_order")
    List<DepartmentChangeStepEntity> byRequest(Long requestId);
    @Select("SELECT * FROM sys_department_change_step WHERE request_id=#{requestId} AND step_order=#{stepOrder} LIMIT 1")
    DepartmentChangeStepEntity one(Long requestId, Integer stepOrder);
}
