package org.lbl.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RealtimeEventPublisher {
    public static final String CHANNEL = "kariya-admin:realtime:events";
    private static final Logger log = LoggerFactory.getLogger(RealtimeEventPublisher.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    public RealtimeEventPublisher(StringRedisTemplate redis, ObjectMapper json) {
        this.redis = redis;
        this.json = json;
    }

    public boolean userEvent(Long userId, String type, Object data) {
        return publish(new RealtimeEnvelope(RealtimeEnvelope.USER_EVENT, userId, null, type, json.valueToTree(data)));
    }

    public boolean closeSession(String sid) {
        return publish(new RealtimeEnvelope(RealtimeEnvelope.CLOSE_SESSION, null, sid, null, null));
    }

    private boolean publish(RealtimeEnvelope event) {
        try {
            redis.convertAndSend(CHANNEL, json.writeValueAsString(event));
            return true;
        } catch (Exception ex) {
            log.warn("Failed to publish realtime event action={}", event.action(), ex);
            return false;
        }
    }
}
