package com.jd.genie.service.auth;

import com.jd.genie.config.AgentAuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRefreshCookieServiceTest {
    @Test
    void createsHttpOnlySecureRefreshCookie() {
        AgentAuthProperties properties = new AgentAuthProperties();
        properties.setRefreshCookieSecure(true);
        properties.setRefreshCookieSameSite("Lax");
        HttpHeaders headers = new HttpHeaders();

        new AgentRefreshCookieService(properties).write(headers, "rotating-token");

        String cookie = headers.getFirst(HttpHeaders.SET_COOKIE);
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("Secure"));
        assertTrue(cookie.contains("SameSite=Lax"));
        assertTrue(cookie.contains("Path=/api/v1/auth"));
    }
}