package com.jd.genie.service.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.model.auth.AgentPrincipal;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Append-only security and operation audit trail stored with agent operational data. */
@Service
public class AgentAuditLogService {
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AgentAuditLogService(@Qualifier("agentPersistenceDataSource") DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbcTemplate = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        this.objectMapper = objectMapper;
    }

    public void record(AgentPrincipal principal, String action, String target, String clientIp, String status, Map<String, ?> details) {
        try {
            jdbcTemplate.update("INSERT INTO agent_security_audit (audit_id, owner_user_id, username, action, target, client_ip, status, detail_json, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID().toString(), principal == null ? null : principal.userId(), principal == null ? null : principal.username(), action,
                    target, clientIp, status, objectMapper.writeValueAsString(details == null ? Map.of() : details), Timestamp.from(Instant.now()));
        } catch (Exception ignored) {
            // Security telemetry must not make a user-facing operation unavailable.
        }
    }
}