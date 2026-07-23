package com.jd.genie.model.agent;

import java.time.Instant;

/** Lightweight session metadata used by the Workspace session catalog. */
public record AgentSessionOverview(
        String sessionId,
        String latestRunId,
        String mode,
        String latestPrompt,
        String status,
        Instant createdAt,
        Instant updatedAt,
        int runCount,
        int eventCount) {
}
