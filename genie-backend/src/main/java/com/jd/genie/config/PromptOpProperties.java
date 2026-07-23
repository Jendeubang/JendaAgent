package com.jd.genie.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Runtime switches for the prompt-optimization sub-agent. */
@Data
@Component
@ConfigurationProperties(prefix = "agent.prompt-op")
public class PromptOpProperties {
    private boolean enabled = true;
    private int maxKnowledgeItems = 4;
    private int maxPromptLength = 4000;
}
