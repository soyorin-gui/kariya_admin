package org.lbl.approval.task;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ApprovalTaskMapper {
    String COLUMNS = """
            SELECT r.id request_id, 'DEPARTMENT_CHANGE' business_type, u.real_name requester_name,
                   COALESCE(fd.dept_name, '未分配部门') from_dept_name, td.dept_name target_dept_name,
                   r.reason, r.status request_status, r.current_step, s.step_type, s.status step_status,
                   COALESCE(au.real_name, '超级管理员') assigned_user_name,
                   du.real_name decided_by_name, s.decision_reason, r.created_time, s.decided_time
              FROM sys_department_change_request r
              JOIN sys_user u ON u.id = r.requester_id
              LEFT JOIN sys_dept fd ON fd.id = r.from_dept_id
              JOIN sys_dept td ON td.id = r.target_dept_id
              LEFT JOIN sys_department_change_step s ON s.request_id = r.id
              LEFT JOIN sys_user au ON au.id = s.assigned_user_id
              LEFT JOIN sys_user du ON du.id = s.decided_by
            """;

    @Select(COLUMNS + " WHERE s.step_order=r.current_step AND s.status='PENDING'"
            + " AND r.status IN ('PENDING_SOURCE','PENDING_TARGET') AND r.requester_id != #{userId}"
            + " AND (s.assigned_user_id=#{userId} OR #{superAdmin}=TRUE)"
            + " ORDER BY r.created_time ASC LIMIT #{offset},#{size}")
    List<ApprovalTaskView> pending(@Param("userId") Long userId, @Param("superAdmin") boolean superAdmin,
                                   @Param("offset") long offset, @Param("size") long size);

    @Select("SELECT COUNT(*) FROM sys_department_change_request r JOIN sys_department_change_step s"
            + " ON s.request_id=r.id AND s.step_order=r.current_step WHERE s.status='PENDING'"
            + " AND r.status IN ('PENDING_SOURCE','PENDING_TARGET') AND r.requester_id != #{userId}"
            + " AND (s.assigned_user_id=#{userId} OR #{superAdmin}=TRUE)")
    long countPending(@Param("userId") Long userId, @Param("superAdmin") boolean superAdmin);

    @Select(COLUMNS + " WHERE s.decided_by=#{userId} AND s.status IN ('APPROVED','REJECTED')"
            + " ORDER BY s.decided_time DESC LIMIT #{offset},#{size}")
    List<ApprovalTaskView> processed(@Param("userId") Long userId, @Param("offset") long offset,
                                     @Param("size") long size);

    @Select("SELECT COUNT(*) FROM sys_department_change_step WHERE decided_by=#{userId} AND status IN ('APPROVED','REJECTED')")
    long countProcessed(Long userId);

    @Select(COLUMNS + " WHERE r.requester_id=#{userId} AND s.step_order=r.current_step"
            + " ORDER BY r.created_time DESC LIMIT #{offset},#{size}")
    List<ApprovalTaskView> mine(@Param("userId") Long userId, @Param("offset") long offset,
                                @Param("size") long size);

    @Select("SELECT COUNT(*) FROM sys_department_change_request WHERE requester_id=#{userId}")
    long countMine(Long userId);
}
