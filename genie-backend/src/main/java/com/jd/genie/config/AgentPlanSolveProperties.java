package com.jd.genie.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Runtime guardrails for versioned Plan-Solve replanning. */
@Data
@Component
@ConfigurationProperties(prefix = "agent.plan-solve")
public class AgentPlanSolveProperties {
    /** Initial plan plus this many replacement plans. */
    private int maxReplans = 2;
    private boolean replanOnFailedTask = true;
    private boolean replanOnSkippedTool = true;
}