package org.lbl.approval.departmenttransfer;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface DepartmentTransferRequestMapper extends BaseMapper<DepartmentTransferRequestEntity> {
    @Select("SELECT * FROM sys_department_change_request WHERE requester_id=#{userId} AND status IN ('PENDING_SOURCE','PENDING_TARGET') ORDER BY id DESC LIMIT 1")
    DepartmentTransferRequestEntity activeByRequester(Long userId);
    @Select("SELECT * FROM sys_department_change_request WHERE id=#{id} FOR UPDATE")
    DepartmentTransferRequestEntity lockById(Long id);
    @Select("SELECT * FROM sys_department_change_request WHERE requester_id=#{userId} ORDER BY id DESC LIMIT 50")
    List<DepartmentTransferRequestEntity> byRequester(Long userId);
}
