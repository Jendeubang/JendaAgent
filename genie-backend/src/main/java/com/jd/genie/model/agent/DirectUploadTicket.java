package com.jd.genie.model.agent;

public record DirectUploadTicket(
        String uploadId,
        String uploadUrl,
        String objectKey,
        String bucket,
        String region,
        long expiresAt,
        TemporaryCredentials credentials) {

    public record TemporaryCredentials(String tmpSecretId, String tmpSecretKey, String token) {
    }
}
