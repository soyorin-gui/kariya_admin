package org.lbl.departmentchange;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface DepartmentChangeRequestMapper extends BaseMapper<DepartmentChangeRequestEntity> {
    @Select("SELECT * FROM sys_department_change_request WHERE requester_id=#{userId} AND status IN ('PENDING_SOURCE','PENDING_TARGET') ORDER BY id DESC LIMIT 1")
    DepartmentChangeRequestEntity activeByRequester(Long userId);
    @Select("SELECT * FROM sys_department_change_request WHERE id=#{id} FOR UPDATE")
    DepartmentChangeRequestEntity lockById(Long id);
    @Select("SELECT * FROM sys_department_change_request WHERE requester_id=#{userId} ORDER BY id DESC LIMIT 50")
    List<DepartmentChangeRequestEntity> byRequester(Long userId);
}
