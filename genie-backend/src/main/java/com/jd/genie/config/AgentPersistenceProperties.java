package com.jd.genie.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration for the isolated agent operational database. */
@Data
@ConfigurationProperties(prefix = "agent.history")
public class AgentPersistenceProperties {
    private String jdbcUrl = "jdbc:h2:file:./runtime/agent-history;MODE=MySQL";
    private String username = "sa";
    private String password = "";
}