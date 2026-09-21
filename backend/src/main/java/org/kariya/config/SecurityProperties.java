package org.kariya.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kariya.security")
public record SecurityProperties(String jwtSecret, long accessTokenMinutes, long idleHours, long rememberedIdleDays) {
}
