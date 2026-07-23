package com.jd.genie.model.auth;

public record AgentAuthResponse(String accessToken, String refreshToken, String tokenType, long expiresInSeconds,
                                long refreshExpiresInSeconds, String userId, String username) {
}