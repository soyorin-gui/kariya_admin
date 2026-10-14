package org.lbl.approval.core.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lbl.approval.core.entity.ApprovalInstanceEntity;
import org.lbl.approval.core.entity.ApprovalTaskEntity;
import org.lbl.approval.core.mapper.ApprovalInstanceMapper;
import org.lbl.approval.core.mapper.ApprovalTaskRecordMapper;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApprovalWorkflowServiceTest {
    @Mock
    private ApprovalInstanceMapper instances;
    @Mock
    private ApprovalTaskRecordMapper tasks;

    @Test
    void approveCurrentAdvancesToNextWaitingTask() {
        ApprovalWorkflowService service = new ApprovalWorkflowService(instances, tasks);
        ApprovalInstanceEntity instance = instance();
        ApprovalTaskEntity current = task(1, "PENDING");
        ApprovalTaskEntity next = task(2, "WAITING");
        when(tasks.nextWaiting(10L, 1, 1)).thenReturn(next);

        service.approveCurrent(instance, current, 7L, "审批人", 8L, "下一审批人");

        assertEquals("APPROVED", current.getStatus());
        assertEquals("PENDING", next.getStatus());
        assertEquals(8L, next.getAssignedUserId());
        assertEquals(2, instance.getCurrentStep());
        assertEquals("PENDING", instance.getStatus());
        verify(tasks).updateById(current);
        verify(tasks).updateById(next);
        verify(instances).updateById(instance);
    }

    @Test
    void finalApprovalCompletesInstance() {
        ApprovalWorkflowService service = new ApprovalWorkflowService(instances, tasks);
        ApprovalInstanceEntity instance = instance();
        ApprovalTaskEntity current = task(1, "PENDING");
        when(tasks.nextWaiting(10L, 1, 1)).thenReturn(null);

        service.approveCurrent(instance, current, 7L, "审批人", null, null);

        assertEquals("APPROVED", instance.getStatus());
        assertNotNull(instance.getFinishedTime());
        verify(instances).updateById(instance);
    }

    @Test
    void rejectCancelsRemainingWaitingTasks() {
        ApprovalWorkflowService service = new ApprovalWorkflowService(instances, tasks);
        ApprovalInstanceEntity instance = instance();
        ApprovalTaskEntity current = task(1, "PENDING");
        ApprovalTaskEntity waiting = task(2, "WAITING");
        when(tasks.byInstance(10L, 1)).thenReturn(List.of(current, waiting));

        service.rejectCurrent(instance, current, 7L, "审批人", "资料不完整");

        assertEquals("REJECTED", current.getStatus());
        assertEquals("CANCELLED", waiting.getStatus());
        assertEquals("REJECTED", instance.getStatus());
        assertNotNull(instance.getFinishedTime());
    }

    @Test
    void resubmitRejectedInstanceStartsNextApprovalRound() {
        ApprovalWorkflowService service = new ApprovalWorkflowService(instances, tasks);
        ApprovalInstanceEntity instance = instance();
        instance.setStatus("REJECTED");
        instance.setFinishedTime(java.time.LocalDateTime.now());
        ApprovalTaskEntity retry = task(1, "PENDING");

        service.resubmit(instance, "修订后的需求", "修订后的摘要", List.of(retry));

        assertEquals("PENDING", instance.getStatus());
        assertEquals(2, instance.getApprovalRound());
        assertEquals(1, instance.getCurrentStep());
        assertNull(instance.getFinishedTime());
        assertEquals(10L, retry.getApprovalInstanceId());
        assertEquals(2, retry.getApprovalRound());
        verify(instances).updateById(instance);
        verify(tasks).insert(retry);
    }

    private ApprovalInstanceEntity instance() {
        ApprovalInstanceEntity value = new ApprovalInstanceEntity();
        value.setId(10L);
        value.setStatus("PENDING");
        value.setCurrentStep(1);
        value.setApprovalRound(1);
        return value;
    }

    private ApprovalTaskEntity task(int order, String status) {
        ApprovalTaskEntity value = new ApprovalTaskEntity();
        value.setId((long) order);
        value.setStepOrder(order);
        value.setStatus(status);
        return value;
    }
}
