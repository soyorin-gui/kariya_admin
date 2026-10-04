package org.lbl.realtime;

import com.fasterxml.jackson.databind.JsonNode;

/** Redis 中传播的跨实例实时事件。 */
public record RealtimeEnvelope(String action, Long userId, String sid, String type, JsonNode data) {
    public static final String USER_EVENT = "USER_EVENT";
    public static final String CLOSE_SESSION = "CLOSE_SESSION";
}
