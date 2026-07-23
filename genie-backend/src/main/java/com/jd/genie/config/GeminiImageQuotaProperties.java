package com.jd.genie.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Per-user daily request caps for Gemini image models. */
@Data
@Component
@ConfigurationProperties(prefix = "agent.image-quota")
public class GeminiImageQuotaProperties {
    private boolean enabled = true;
    private int nanoBanana2DailyLimit = 20;
    private int nanoBananaProDailyLimit = 3;
}