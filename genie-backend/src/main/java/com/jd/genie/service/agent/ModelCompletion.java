package com.jd.genie.service.agent;

public record ModelCompletion(boolean invoked, String content, String provider) {
    public static ModelCompletion skipped(String reason) {
        return new ModelCompletion(false, reason, "not-configured");
    }
}
