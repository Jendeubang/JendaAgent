package com.jd.genie.model.agent;

public record StoredAgentImage(
        String assetId,
        String originalFileName,
        String storedFileName,
        String mediaType,
        long size) {
}
