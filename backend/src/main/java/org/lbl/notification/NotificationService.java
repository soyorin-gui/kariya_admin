package org.lbl.notification;

import org.lbl.common.exception.BusinessException;
import org.lbl.realtime.RealtimeGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.List;
import java.util.Map;

@Service
public class NotificationService {
    private final NotificationMapper mapper;
    private final RealtimeGateway realtime;
    public NotificationService(NotificationMapper mapper, RealtimeGateway realtime) { this.mapper = mapper; this.realtime = realtime; }

    public NotificationEntity create(Long recipientId, String type, String title, String content,
                                     String businessType, Long businessId) {
        NotificationEntity value = new NotificationEntity();
        value.setRecipientId(recipientId); value.setType(type); value.setTitle(title); value.setContent(content);
        value.setBusinessType(businessType); value.setBusinessId(businessId);
        mapper.insert(value);
        afterCommit(() -> realtime.send(recipientId, "notification.created", Map.of(
                "id", value.getId(), "type", type, "title", title, "content", content,
                "businessType", businessType, "businessId", businessId)));
        return value;
    }

    public List<NotificationEntity> latest(Long userId, int limit) { return mapper.latest(userId, Math.max(1, Math.min(limit, 100))); }
    public long unread(Long userId) { return mapper.unread(userId); }
    public void read(Long id, Long userId) {
        if (mapper.markRead(id, userId) == 0 && mapper.selectById(id) == null) throw new BusinessException("消息不存在");
    }
    public void readAll(Long userId) { mapper.markAllRead(userId); }

    public void workflowUpdated(Iterable<Long> users, Long requestId) {
        afterCommit(() -> users.forEach(userId -> realtime.send(userId, "department.request.updated", Map.of("requestId", requestId))));
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) { action.run(); return; }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { action.run(); }
        });
    }
}
