package com.jd.genie.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Server-only configuration for the Volcano Ark SeedDream image gateway. */
@Data
@Component
@ConfigurationProperties(prefix = "agent.gateway.seedream")
public class SeedDreamImageGatewayProperties {
    private boolean enabled;
    private String endpoint = "https://ark.cn-beijing.volces.com/api/v3/images/generations";
    private String apiKey;
    /** Prefer a provisioned ep-... inference endpoint over a mutable public model alias. */
    private String model;
    private String size = "2K";
    private boolean watermark = true;
    /** Temporary Ark result URLs are only downloaded from these explicit suffixes. */
    private String resultHostSuffixes = ".volces.com,.volcengine.com,.byteimg.com";
    private Duration timeout = Duration.ofSeconds(600);
}