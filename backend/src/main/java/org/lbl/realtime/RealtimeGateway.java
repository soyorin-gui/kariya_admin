package org.lbl.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RealtimeGateway extends TextWebSocketHandler {
    private final ObjectMapper json;
    private final RealtimeEventPublisher publisher;
    private final Map<Long, Map<String, WebSocketSession>> connections = new ConcurrentHashMap<>();

    public RealtimeGateway(ObjectMapper json, RealtimeEventPublisher publisher) {
        this.json = json;
        this.publisher = publisher;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId == null) { session.close(CloseStatus.NOT_ACCEPTABLE); return; }
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(session, 5_000, 128 * 1024);
        connections.computeIfAbsent(userId, ignored -> new ConcurrentHashMap<>()).put(session.getId(), safe);
        safe.sendMessage(new TextMessage(json.writeValueAsString(Map.of("type", "connection.ready"))));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId == null) return;
        Map<String, WebSocketSession> sessions = connections.get(userId);
        if (sessions != null) {
            sessions.remove(session.getId());
            if (sessions.isEmpty()) connections.remove(userId, sessions);
        }
    }

    public void send(Long userId, String type, Object data) {
        // 所有实例（包括本实例）统一从 Redis 订阅后落到本地连接，避免发送两次。
        if (!publisher.userEvent(userId, type, data)) sendLocal(userId, type, data);
    }

    void sendLocal(Long userId, String type, Object data) {
        Map<String, WebSocketSession> sessions = connections.get(userId);
        if (sessions == null || sessions.isEmpty()) return;
        try {
            TextMessage message = new TextMessage(json.writeValueAsString(Map.of("type", type, "data", data)));
            for (Map.Entry<String, WebSocketSession> entry : Map.copyOf(sessions).entrySet()) {
                WebSocketSession session = entry.getValue();
                try { if (session.isOpen()) session.sendMessage(message); else sessions.remove(entry.getKey()); }
                catch (Exception ex) { sessions.remove(entry.getKey()); try { session.close(); } catch (Exception ignored) {} }
            }
        } catch (Exception ignored) {
            // 数据已经持久化；实时投递失败由前端重连后的 REST 补拉兜底。
        }
    }

    public void closeSession(String sid) {
        if (!publisher.closeSession(sid)) closeSessionLocal(sid);
    }

    void closeSessionLocal(String sid) {
        if (sid == null) return;
        for (Map<String, WebSocketSession> sessions : connections.values()) {
            for (Map.Entry<String, WebSocketSession> entry : Map.copyOf(sessions).entrySet()) {
                if (!sid.equals(entry.getValue().getAttributes().get("sid"))) continue;
                sessions.remove(entry.getKey());
                try { entry.getValue().close(CloseStatus.POLICY_VIOLATION); } catch (Exception ignored) {}
            }
        }
    }

    /** 防止反向代理把长期空闲的实时连接回收。 */
    @Scheduled(fixedDelay = 25_000)
    public void heartbeat() {
        // 心跳只针对本节点持有的连接，不应通过 Redis 广播，否则实例数越多心跳越密。
        for (Long userId : Set.copyOf(connections.keySet())) sendLocal(userId, "connection.ping", Map.of());
    }
}
