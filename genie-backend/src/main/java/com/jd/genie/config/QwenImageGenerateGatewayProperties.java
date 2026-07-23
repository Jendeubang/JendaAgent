package com.jd.genie.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Configuration for the internal Qwen image-generation adapter. */
@Data
@Component
@ConfigurationProperties(prefix = "agent.gateway.image-generate")
public class QwenImageGenerateGatewayProperties {
    private boolean enabled;
    /** Full Model Studio multimodal-generation endpoint for the selected workspace. */
    private String endpoint;
    /** Optional dedicated key. When absent, the runtime Qwen API key is reused. */
    private String apiKey;
    private String model = "qwen-image-2.0-pro";
    private String size = "1024*1024";
    private boolean promptExtend = true;
    private boolean watermark;
    private String internalKey;
    private Duration timeout = Duration.ofSeconds(600);
}
