package com.jd.genie.model.agent;

public record AgentImageUploadResponse(
        String assetId,
        String fileName,
        String imageUrl,
        String mediaType,
        long size,
        boolean modelAccessible,
        String storageProvider) {
}
