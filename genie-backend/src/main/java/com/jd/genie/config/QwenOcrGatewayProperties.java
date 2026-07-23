package com.jd.genie.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Configuration for the internal Qwen OCR adapter. */
@Data
@Component
@ConfigurationProperties(prefix = "agent.gateway.ocr")
public class QwenOcrGatewayProperties {
    private boolean enabled;
    private String model = "qwen-vl-ocr-2025-11-20";
    private String internalKey;
    private Duration timeout = Duration.ofSeconds(180);
}
