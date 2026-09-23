package org.lbl.config;

import org.lbl.system.log.retention.LogRetentionProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({SecurityProperties.class, LogRetentionProperties.class})
public class AppConfig {
}
