package com.jd.genie.service.observability;

import com.jd.genie.model.auth.AgentPrincipal;
import com.jd.genie.service.auth.AgentAuthenticationFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/** Adds a request identifier to the response and every structured log emitted during the request. */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTraceFilter extends OncePerRequestFilter {
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    private final AgentFailureAlertService alerts;

    public RequestTraceFilter(AgentFailureAlertService alerts) {
        this.alerts = alerts;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = requestId(request.getHeader(REQUEST_ID_HEADER));
        long startedAt = System.nanoTime();
        MDC.put("requestId", requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            Object principal = request.getAttribute(AgentAuthenticationFilter.PRINCIPAL_ATTRIBUTE);
            if (principal instanceof AgentPrincipal agentPrincipal) MDC.put("userId", agentPrincipal.userId());
            int status = response.getStatus();
            log.info("http_request method={} path={} status={} durationMs={}", request.getMethod(), request.getRequestURI(), status, durationMs);
            if (status >= 500) alerts.reportServerFailure(requestId, request.getMethod(), request.getRequestURI(), status, durationMs);
            MDC.clear();
        }
    }

    private String requestId(String candidate) {
        if (candidate != null && candidate.matches("[A-Za-z0-9._-]{8,128}")) return candidate;
        return UUID.randomUUID().toString();
    }
}