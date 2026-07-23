package com.jd.genie.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Configuration for the internal Qwen image-edit adapter. */
@Data
@Component
@ConfigurationProperties(prefix = "agent.gateway.image-edit")
public class QwenImageEditGatewayProperties {
    private boolean enabled;
    private String endpoint;
    /** Optional dedicated key. When absent, the runtime Qwen API key is reused. */
    private String apiKey;
    private String model = "qwen-image-2.0";
    private String size = "1024*1024";
    private boolean promptExtend = true;
    private boolean watermark;
    private String internalKey;
    private Duration timeout = Duration.ofSeconds(600);
}
