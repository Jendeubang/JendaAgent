package com.jd.genie.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Server-only Volcano Ark SeedVR2 configuration for upscale and restoration. */
@Data
@Component
@ConfigurationProperties(prefix = "agent.gateway.seedvr2")
public class SeedVr2ImageGatewayProperties {
    private boolean enabled;
    private String endpoint = "https://ark.cn-beijing.volces.com/api/v3/images/generations";
    /** Use the provisioned SeedVR2 endpoint ID from the Volcano Ark console. */
    private String model;
    private String apiKey;
    private String size = "4K";
    private String resultHostSuffixes = ".volces.com,.volcengine.com";
    private boolean watermark = false;
    private Duration timeout = Duration.ofSeconds(600);
}