package com.jd.genie.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Operational limits for the bounded ReAct loop. */
@Data
@Component
@ConfigurationProperties(prefix = "agent.react")
public class AgentReActProperties {
    private int maxSteps = 6;
    private Duration timeout = Duration.ofMinutes(8);
    private int repeatedToolLimit = 2;
}
