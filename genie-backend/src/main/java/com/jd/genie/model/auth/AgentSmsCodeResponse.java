package com.jd.genie.model.auth;

public record AgentSmsCodeResponse(boolean delivered, long expiresInSeconds, String debugCode) {
}
