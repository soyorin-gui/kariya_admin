package org.lbl.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Component;

@Component
public class RealtimeEventSubscriber implements MessageListener {
    private static final Logger log = LoggerFactory.getLogger(RealtimeEventSubscriber.class);
    private static final StringRedisSerializer STRINGS = new StringRedisSerializer();
    private final ObjectMapper json;
    private final RealtimeGateway gateway;

    public RealtimeEventSubscriber(ObjectMapper json, RealtimeGateway gateway) {
        this.json = json;
        this.gateway = gateway;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            RealtimeEnvelope event = json.readValue(STRINGS.deserialize(message.getBody()), RealtimeEnvelope.class);
            if (RealtimeEnvelope.USER_EVENT.equals(event.action()))
                gateway.sendLocal(event.userId(), event.type(), event.data());
            else if (RealtimeEnvelope.CLOSE_SESSION.equals(event.action())) gateway.closeSessionLocal(event.sid());
        } catch (Exception ex) {
            log.warn("Ignored malformed realtime event", ex);
        }
    }
}
