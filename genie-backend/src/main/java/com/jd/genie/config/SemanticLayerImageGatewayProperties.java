package com.jd.genie.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Server-only configuration for a semantic segmentation/layering image endpoint. */
@Data
@Component
@ConfigurationProperties(prefix = "agent.gateway.semantic-layer")
public class SemanticLayerImageGatewayProperties {
    private boolean enabled;
    /** The vendor endpoint must accept the documented normalized layer request and return image URLs/base64 layers. */
    private String endpoint;
    private String model;
    private String apiKey;
    private String resultHostSuffixes = "";
    private String outputFormat = "png";
    private Duration timeout = Duration.ofSeconds(600);
}