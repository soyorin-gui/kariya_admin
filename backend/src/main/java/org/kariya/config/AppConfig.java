package org.kariya.config;

import org.kariya.system.log.LogRetentionProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({SecurityProperties.class, LogRetentionProperties.class})
public class AppConfig {
}
