package org.lbl.approval.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.lbl.approval.core.entity.ApprovalTaskEntity;

import java.util.List;

@Mapper
public interface ApprovalTaskRecordMapper extends BaseMapper<ApprovalTaskEntity> {
    @Select("SELECT * FROM sys_approval_task WHERE approval_instance_id=#{instanceId} AND approval_round=#{approvalRound} ORDER BY step_order")
    List<ApprovalTaskEntity> byInstance(@Param("instanceId") Long instanceId,
                                        @Param("approvalRound") Integer approvalRound);

    @Select("SELECT * FROM sys_approval_task WHERE approval_instance_id=#{instanceId} AND approval_round=#{approvalRound} AND step_order=#{stepOrder} LIMIT 1")
    ApprovalTaskEntity one(@Param("instanceId") Long instanceId,
                           @Param("approvalRound") Integer approvalRound,
                           @Param("stepOrder") Integer stepOrder);

    @Select("SELECT * FROM sys_approval_task WHERE approval_instance_id=#{instanceId} AND approval_round=#{approvalRound} AND step_order>#{stepOrder} AND status='WAITING' ORDER BY step_order LIMIT 1")
    ApprovalTaskEntity nextWaiting(@Param("instanceId") Long instanceId,
                                   @Param("approvalRound") Integer approvalRound,
                                   @Param("stepOrder") Integer stepOrder);
}
