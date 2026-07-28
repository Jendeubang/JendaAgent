package com.jd.genie.service.agent;

/** One image emitted by a tool provider. Layering providers may emit several outputs. */
public record ImageToolProviderOutput(String imageUrl, String base64Data, String mediaType, String label) {
    public boolean hasImage() {
        return (imageUrl != null && !imageUrl.isBlank()) || (base64Data != null && !base64Data.isBlank());
    }
}