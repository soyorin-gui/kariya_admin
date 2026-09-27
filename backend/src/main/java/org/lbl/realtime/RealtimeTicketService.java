package org.lbl.realtime;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

@Service
public class RealtimeTicketService {
    private final StringRedisTemplate redis;
    private final SecureRandom random = new SecureRandom();

    public RealtimeTicketService(StringRedisTemplate redis) { this.redis = redis; }

    public String issue(Long userId, String sid) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redis.opsForValue().set(key(ticket), userId + "|" + sid, Duration.ofSeconds(60));
        return ticket;
    }

    public Ticket consume(String ticket) {
        if (ticket == null || ticket.isBlank()) return null;
        String value = redis.opsForValue().getAndDelete(key(ticket));
        try {
            if (value == null) return null;
            String[] parts = value.split("\\|", 2);
            return parts.length == 2 ? new Ticket(Long.valueOf(parts[0]), parts[1]) : null;
        } catch (RuntimeException ex) { return null; }
    }

    private String key(String ticket) { return "realtime:ticket:" + ticket; }
    public record Ticket(Long userId, String sid) {}
}
