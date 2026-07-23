package com.jd.genie.service.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.config.AgentAuthProperties;
import com.jd.genie.model.auth.AgentPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/** Protects only the product agent APIs and leaves upstream JoyAgent APIs unchanged. */
@Component
public class AgentAuthenticationFilter extends OncePerRequestFilter {
    public static final String PRINCIPAL_ATTRIBUTE = "jendaAgentPrincipal";
    private static final AgentPrincipal LOCAL_DEMO = new AgentPrincipal("local-demo-user", "local-demo");
    private final AgentAuthProperties properties;
    private final AgentJwtTokenService tokenService;
    private final ObjectMapper objectMapper;

    public AgentAuthenticationFilter(AgentAuthProperties properties, AgentJwtTokenService tokenService, ObjectMapper objectMapper) {
        this.properties = properties;
        this.tokenService = tokenService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !(uri.startsWith("/api/v1/agent") || uri.startsWith("/api/v2/agent"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        if (!properties.isEnabled()) {
            request.setAttribute(PRINCIPAL_ATTRIBUTE, LOCAL_DEMO);
            chain.doFilter(request, response);
            return;
        }
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            reject(response, "Missing bearer token");
            return;
        }
        try {
            request.setAttribute(PRINCIPAL_ATTRIBUTE, tokenService.verify(header.substring(7)));
            chain.doFilter(request, response);
        } catch (IllegalArgumentException | IllegalStateException error) {
            reject(response, "Invalid or expired bearer token");
        }
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), Map.of("code", "UNAUTHORIZED", "message", message));
    }
}
