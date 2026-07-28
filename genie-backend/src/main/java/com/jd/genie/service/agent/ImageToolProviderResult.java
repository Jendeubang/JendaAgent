package com.jd.genie.service.agent;

import java.util.List;

/** Normalized output from a configured or native image-tool provider. */
public record ImageToolProviderResult(
        String imageUrl,
        String base64Data,
        String mediaType,
        String text,
        String provider,
        List<ImageToolProviderOutput> outputs) {
    public ImageToolProviderResult {
        outputs = outputs == null ? List.of() : List.copyOf(outputs);
    }
    public ImageToolProviderResult(String imageUrl, String base64Data, String mediaType, String text, String provider) {
        this(imageUrl, base64Data, mediaType, text, provider, List.of());
    }
    public boolean hasImage() {
        return (imageUrl != null && !imageUrl.isBlank()) || (base64Data != null && !base64Data.isBlank()) || outputs.stream().anyMatch(ImageToolProviderOutput::hasImage);
    }
    public List<ImageToolProviderOutput> allOutputs() {
        ImageToolProviderOutput primary = new ImageToolProviderOutput(imageUrl, base64Data, mediaType, "result");
        return primary.hasImage() ? concat(primary) : outputs;
    }
    private List<ImageToolProviderOutput> concat(ImageToolProviderOutput primary) {
        java.util.ArrayList<ImageToolProviderOutput> result = new java.util.ArrayList<>();
        result.add(primary);
        result.addAll(outputs);
        return List.copyOf(result);
    }
}