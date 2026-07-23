package com.jd.genie.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Server-only Google Gemini image configuration. API keys never reach the browser. */
@Data
@Component
@ConfigurationProperties(prefix = "agent.gateway.gemini")
public class GeminiImageGatewayProperties {
    private boolean enabled;
    private String endpoint = "https://generativelanguage.googleapis.com/v1beta/interactions";
    private String apiKey;
    private String nanoBanana2Model = "gemini-3.1-flash-image";
    private String nanoBananaProModel = "gemini-3-pro-image";
    private boolean proEnabled;
    private boolean proFallbackToNanoBanana2 = true;
    private String imageSize = "2K";
    private String outputMimeType = "image/png";
    private int maxReferenceImages = 6;
    private long maxReferenceImageBytes = 8L * 1024 * 1024;
    private Duration timeout = Duration.ofSeconds(600);
}