package com.jd.genie.model.agent;

import java.time.Instant;

/** An uploaded reference or an image produced by a tool during an agent run. */
public record AgentWorkspaceAsset(
        String assetId,
        String runId,
        String title,
        String imageUrl,
        String source,
        Instant occurredAt) {
}
