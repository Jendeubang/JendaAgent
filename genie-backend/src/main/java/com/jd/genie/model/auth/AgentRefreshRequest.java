package com.jd.genie.model.auth;

/** Deprecated body field is accepted only during migration; browser clients use an HttpOnly cookie. */
public record AgentRefreshRequest(String refreshToken) {
}