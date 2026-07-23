package com.jd.genie.service.agent.plansolve;

import java.util.List;

/** Versioned plan contract stored with every Plan-Solve run. */
public record StructuredAgentPlan(String version, String goal, List<PlanTaskSpec> tasks) {
    public StructuredAgentPlan {
        version = version == null || version.isBlank() ? "1.0" : version;
        goal = goal == null ? "" : goal;
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
    }
}
