package com.jd.genie.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Selects the provider used by the normalized image tool gateways. */
@Data
@Component
@ConfigurationProperties(prefix = "agent.image-provider")
public class ImageProviderProperties {
    /** qwen remains the safe default for existing installations. */
    private String defaultProvider = "qwen";
}