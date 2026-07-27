package com.jd.genie.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Redis is the shared fast-path for short-lived security and idempotency state. */
@Data
@Component
@ConfigurationProperties(prefix = "agent.redis")
public class AgentRedisProperties {
    private boolean enabled = true;
    private String keyPrefix = "jenda:security:";
}