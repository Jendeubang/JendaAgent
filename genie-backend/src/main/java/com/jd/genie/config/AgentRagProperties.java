package com.jd.genie.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Configuration for the PromptOp vector knowledge base. */
@Data
@Component
@ConfigurationProperties(prefix = "agent.rag")
public class AgentRagProperties {
    private boolean enabled = true;
    private String qdrantUrl = "http://qdrant:6333";
    private String qdrantApiKey;
    private String collection = "jenda_prompt_knowledge_v1";
    private String embeddingBaseUrl;
    private String embeddingApiKey;
    private String embeddingModel = "text-embedding-v4";
    private int embeddingDimensions = 1024;
    private int searchLimit = 4;
    private double scoreThreshold = 0.18;
    private boolean autoImport = true;
    private Duration timeout = Duration.ofSeconds(20);
}
