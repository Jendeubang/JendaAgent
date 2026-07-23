package com.jd.genie.service.agent;

/** Normalized output from a configured image-tool provider. */
public record ImageToolProviderResult(
        String imageUrl,
        String base64Data,
        String mediaType,
        String text,
        String provider) {
    public boolean hasImage() {
        return (imageUrl != null && !imageUrl.isBlank()) || (base64Data != null && !base64Data.isBlank());
    }
}
