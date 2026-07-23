package com.jd.genie.model.agent;

import java.time.Instant;

/** Durable ownership metadata for a COS-backed input or generated image. */
public record AgentAssetMetadata(
        String assetId,
        String ownerUserId,
        String sessionId,
        String runId,
        String fileName,
        String mediaType,
        long size,
        String objectKey,
        String imageUrl,
        String source,
        Instant createdAt) {
}
