package com.jd.genie.model.auth;

/** Authenticated product user attached to an agent API request. */
public record AgentPrincipal(String userId, String username) {
}
