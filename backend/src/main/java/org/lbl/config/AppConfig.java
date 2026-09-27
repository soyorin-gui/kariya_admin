package org.lbl.config;

import org.lbl.agent.config.AgentProperties;
import org.lbl.system.log.retention.LogRetentionProperties;
import org.lbl.auth.external.ExternalAuthProperties;
import org.lbl.file.FileUploadProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({SecurityProperties.class, LogRetentionProperties.class, ExternalAuthProperties.class, FileUploadProperties.class, AgentProperties.class})
public class AppConfig {
}
