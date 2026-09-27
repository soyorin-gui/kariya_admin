package org.lbl.auth.session;

import java.time.LocalDateTime;

public record SessionView(String id, boolean current, LocalDateTime createdTime, LocalDateTime lastActiveTime,
                          String loginIp, String userAgent, boolean rememberMe, String authMethod,
                          String providerKey) {
}
