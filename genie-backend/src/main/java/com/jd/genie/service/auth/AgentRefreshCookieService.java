package com.jd.genie.service.auth;

import com.jd.genie.config.AgentAuthProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

/** Builds a rotating HttpOnly refresh-token cookie without exposing it to JavaScript. */
@Service
public class AgentRefreshCookieService {
    private final AgentAuthProperties properties;

    public AgentRefreshCookieService(AgentAuthProperties properties) {
        this.properties = properties;
    }

    public void write(HttpHeaders headers, String token) {
        headers.add(HttpHeaders.SET_COOKIE, cookie(token, properties.getRefreshTokenTtl().getSeconds()).toString());
    }

    public void clear(HttpHeaders headers) {
        headers.add(HttpHeaders.SET_COOKIE, cookie("", 0).toString());
    }

    private ResponseCookie cookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(properties.getRefreshCookieName(), value)
                .httpOnly(true)
                .secure(properties.isRefreshCookieSecure())
                .sameSite(properties.getRefreshCookieSameSite())
                .path(properties.getRefreshCookiePath())
                .maxAge(maxAgeSeconds)
                .build();
    }
}