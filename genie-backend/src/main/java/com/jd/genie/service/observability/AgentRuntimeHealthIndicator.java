package com.jd.genie.service.observability;

import com.jd.genie.config.AgentRuntimeProperties;
import com.jd.genie.service.auth.AgentRedisSupport;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/** Reports configuration readiness without making paid model or storage requests. */
@Component("agentRuntime")
public class AgentRuntimeHealthIndicator implements HealthIndicator {
    private final AgentRuntimeProperties runtime;
    private final AgentRedisSupport redis;

    public AgentRuntimeHealthIndicator(AgentRuntimeProperties runtime, AgentRedisSupport redis) {
        this.runtime = runtime;
        this.redis = redis;
    }

    @Override
    public Health health() {
        return Health.up()
                .withDetail("modelConfigured", runtime.getModel().isEnabled())
                .withDetail("ocrConfigured", runtime.getTools().getOcr().isEnabled())
                .withDetail("imageGenerateConfigured", runtime.getTools().getImageGenerate().isEnabled())
                .withDetail("imageEditConfigured", runtime.getTools().getImageEdit().isEnabled())
                .withDetail("redisFastPath", redis.isEnabled())
                .build();
    }
}