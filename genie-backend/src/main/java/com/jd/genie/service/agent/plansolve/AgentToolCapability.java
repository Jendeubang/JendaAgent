package com.jd.genie.service.agent.plansolve;

import com.jd.genie.service.agent.AgentToolType;

import java.util.List;

/** Safe, credential-free tool facts made available to planners and clients. */
public record AgentToolCapability(
        AgentToolType tool,
        boolean available,
        boolean requiresInputImage,
        List<String> capabilities,
        String unavailableReason) {
    public AgentToolCapability {
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
        unavailableReason = unavailableReason == null ? "" : unavailableReason;
    }
}