package com.jd.genie.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Optional outbound alerting. Disabled unless a deployment explicitly enables it. */
@Data
@Component
@ConfigurationProperties(prefix = "agent.alert")
public class AgentAlertProperties {
    private boolean enabled;
    private String webhookUrl;
    private Duration timeout = Duration.ofSeconds(2);
}