package com.jd.genie.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Environment-driven configuration for the new agent runtime.
 * Do not commit real provider credentials to application.yml.
 */
@Data
@Component
@ConfigurationProperties(prefix = "agent.runtime")
public class AgentRuntimeProperties {
    private Model model = new Model();
    private Tools tools = new Tools();

    @Data
    public static class Model {
        private boolean enabled;
        private String baseUrl;
        private String apiKey;
        private String model = "qwen-plus";
        private double temperature = 0.2;
        private Duration timeout = Duration.ofSeconds(90);
    }

    @Data
    public static class Tools {
        private Endpoint ocr = new Endpoint();
        private Endpoint imageGenerate = new Endpoint();
        private Endpoint imageEdit = new Endpoint();
    }

    @Data
    public static class Endpoint {
        private boolean enabled;
        private String url;
        private String apiKey;
        private Duration timeout = Duration.ofSeconds(120);
    }
}
