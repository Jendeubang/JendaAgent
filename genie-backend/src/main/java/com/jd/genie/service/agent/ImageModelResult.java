package com.jd.genie.service.agent;

/** Normalized provider output consumed by COS archival and agent SSE events. */
public record ImageModelResult(String imageUrl, String text, String provider, boolean archivedToCos) {
}