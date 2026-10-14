package org.lbl.approval.task;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ApprovalTaskMapper {
    String COLUMNS = """
            SELECT a.id approval_instance_id, a.business_id, a.business_type, a.business_title,
                   a.business_summary, a.requester_name, a.status request_status,
                   a.current_step, a.approval_round, t.step_type, t.step_name,
                   t.status step_status, COALESCE(t.assigned_user_name,
                   CONCAT_WS(' · ', t.candidate_dept_name, t.candidate_role_name), '超级管理员') assigned_user_name,
                   t.decided_by_name, t.decision_reason, a.submitted_time created_time, t.decided_time
              FROM sys_approval_instance a
              JOIN sys_approval_task t ON t.approval_instance_id=a.id
                                     AND t.approval_round=a.approval_round
            """;

    @Select(COLUMNS + " WHERE t.step_order=a.current_step AND t.status='PENDING'"
            + " AND a.status='PENDING' AND a.requester_id != #{userId}"
            + " AND (t.assigned_user_id=#{userId} OR (t.candidate_dept_id IS NOT NULL AND EXISTS ("
            + " SELECT 1 FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.id"
            + " JOIN sys_role r ON r.id=ur.role_id WHERE u.id=#{userId} AND u.status=1 AND u.deleted=0"
            + " AND u.dept_id=t.candidate_dept_id AND r.role_code=t.candidate_role_code"
            + " AND r.status=1 AND r.deleted=0)) OR #{superAdmin}=TRUE)"
            + " ORDER BY a.submitted_time ASC LIMIT #{offset},#{size}")
    List<ApprovalTaskView> pending(@Param("userId") Long userId, @Param("superAdmin") boolean superAdmin,
                                   @Param("offset") long offset, @Param("size") long size);

    @Select("SELECT COUNT(*) FROM sys_approval_instance a JOIN sys_approval_task t"
            + " ON t.approval_instance_id=a.id AND t.approval_round=a.approval_round"
            + " AND t.step_order=a.current_step WHERE t.status='PENDING' AND a.status='PENDING'"
            + " AND a.requester_id != #{userId}"
            + " AND (t.assigned_user_id=#{userId} OR (t.candidate_dept_id IS NOT NULL AND EXISTS ("
            + " SELECT 1 FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.id"
            + " JOIN sys_role r ON r.id=ur.role_id WHERE u.id=#{userId} AND u.status=1 AND u.deleted=0"
            + " AND u.dept_id=t.candidate_dept_id AND r.role_code=t.candidate_role_code"
            + " AND r.status=1 AND r.deleted=0)) OR #{superAdmin}=TRUE)")
    long countPending(@Param("userId") Long userId, @Param("superAdmin") boolean superAdmin);

    @Select(COLUMNS + " WHERE t.decided_by=#{userId} AND t.status IN ('APPROVED','REJECTED')"
            + " ORDER BY t.decided_time DESC LIMIT #{offset},#{size}")
    List<ApprovalTaskView> processed(@Param("userId") Long userId, @Param("offset") long offset,
                                     @Param("size") long size);

    @Select("SELECT COUNT(*) FROM sys_approval_task WHERE decided_by=#{userId} AND status IN ('APPROVED','REJECTED')")
    long countProcessed(Long userId);

    @Select(COLUMNS + " WHERE a.requester_id=#{userId} AND t.step_order=a.current_step"
            + " ORDER BY a.submitted_time DESC LIMIT #{offset},#{size}")
    List<ApprovalTaskView> mine(@Param("userId") Long userId, @Param("offset") long offset,
                                @Param("size") long size);

    @Select("SELECT COUNT(*) FROM sys_approval_instance WHERE requester_id=#{userId}")
    long countMine(Long userId);
}
