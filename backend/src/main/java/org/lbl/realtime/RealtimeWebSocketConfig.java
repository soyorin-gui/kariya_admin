package org.lbl.realtime;

import org.lbl.config.SecurityProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Configuration
@EnableWebSocket
public class RealtimeWebSocketConfig implements WebSocketConfigurer {
    private final RealtimeGateway gateway;
    private final RealtimeTicketService tickets;
    private final SecurityProperties security;

    public RealtimeWebSocketConfig(RealtimeGateway gateway, RealtimeTicketService tickets, SecurityProperties security) {
        this.gateway = gateway; this.tickets = tickets; this.security = security;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        var registration = registry.addHandler(gateway, "/ws/realtime").addInterceptors(new HandshakeInterceptor() {
            @Override
            public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                           WebSocketHandler handler, Map<String, Object> attributes) {
                String ticket = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("ticket");
                RealtimeTicketService.Ticket consumed = tickets.consume(ticket);
                if (consumed == null) return false;
                attributes.put("userId", consumed.userId());
                attributes.put("sid", consumed.sid());
                return true;
            }
            @Override public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                                 WebSocketHandler handler, Exception exception) {}
        });
        var origins = security.corsAllowedOriginsList();
        if (!origins.isEmpty()) registration.setAllowedOrigins(origins.toArray(String[]::new));
    }
}
