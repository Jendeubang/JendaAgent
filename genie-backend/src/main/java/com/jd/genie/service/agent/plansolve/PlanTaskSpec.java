package com.jd.genie.service.agent.plansolve;

import java.util.List;

/** A single DAG node emitted by PlanningAgent. */
public record PlanTaskSpec(
        String id,
        String title,
        PlanTaskKind kind,
        String prompt,
        List<String> dependsOn,
        String parallelGroup,
        int maxAttempts,
        String skipWhen,
        boolean requiresConfirmation,
        String confirmationMessage) {

    public PlanTaskSpec {
        dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
        parallelGroup = parallelGroup == null ? "default" : parallelGroup;
        maxAttempts = maxAttempts <= 0 ? 1 : maxAttempts;
        skipWhen = skipWhen == null ? "never" : skipWhen;
        confirmationMessage = confirmationMessage == null ? "" : confirmationMessage;
    }
}
