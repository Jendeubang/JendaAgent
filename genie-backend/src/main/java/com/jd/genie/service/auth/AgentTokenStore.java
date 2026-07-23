package com.jd.genie.service.auth;

import com.jd.genie.model.auth.AgentPrincipal;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/** Stores only hashes of opaque refresh tokens and revoked access-token IDs. */
@Repository
public class AgentTokenStore {
    private final JdbcTemplate jdbcTemplate;
    private final SecureRandom random = new SecureRandom();

    public AgentTokenStore(Environment environment) {
        String url = environment.getProperty("agent.history.jdbc-url", "jdbc:h2:file:./runtime/agent-history;MODE=MySQL");
        String username = environment.getProperty("agent.history.username", "sa");
        String password = environment.getProperty("agent.history.password", "");
        this.jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(url, username, password));
    }

    @PostConstruct
    void initializeSchema() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS agent_refresh_token (token_id VARCHAR(64) PRIMARY KEY, user_id VARCHAR(64) NOT NULL, token_hash VARCHAR(128) NOT NULL UNIQUE, expires_at TIMESTAMP NOT NULL, revoked_at TIMESTAMP NULL, created_at TIMESTAMP NOT NULL)");
        try { jdbcTemplate.execute("CREATE INDEX idx_agent_refresh_user ON agent_refresh_token(user_id)"); } catch (RuntimeException ignored) { }
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS agent_access_token_revocation (token_id VARCHAR(64) PRIMARY KEY, expires_at TIMESTAMP NOT NULL, revoked_at TIMESTAMP NOT NULL)");
    }

    public IssuedRefreshToken issue(AgentPrincipal principal, Instant expiresAt) {
        byte[] value = new byte[48];
        random.nextBytes(value);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(value);
        jdbcTemplate.update("INSERT INTO agent_refresh_token (token_id, user_id, token_hash, expires_at, created_at) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID().toString(), principal.userId(), hash(rawToken), Timestamp.from(expiresAt), Timestamp.from(Instant.now()));
        return new IssuedRefreshToken(rawToken, principal, expiresAt);
    }

    public AgentPrincipal consume(String rawToken) {
        List<AgentPrincipal> principals = jdbcTemplate.query("SELECT r.user_id, u.username FROM agent_refresh_token r JOIN agent_user u ON u.user_id = r.user_id WHERE r.token_hash = ? AND r.revoked_at IS NULL AND r.expires_at > ?",
                (resultSet, rowNum) -> new AgentPrincipal(resultSet.getString("user_id"), resultSet.getString("username")), hash(rawToken), Timestamp.from(Instant.now()));
        if (principals.isEmpty()) throw new IllegalArgumentException("Refresh token is invalid or expired");
        int updated = jdbcTemplate.update("UPDATE agent_refresh_token SET revoked_at = ? WHERE token_hash = ? AND revoked_at IS NULL", Timestamp.from(Instant.now()), hash(rawToken));
        if (updated != 1) throw new IllegalArgumentException("Refresh token was already consumed");
        return principals.get(0);
    }

    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        jdbcTemplate.update("UPDATE agent_refresh_token SET revoked_at = ? WHERE token_hash = ? AND revoked_at IS NULL", Timestamp.from(Instant.now()), hash(rawToken));
    }

    public void revokeAllForUser(String userId) {
        jdbcTemplate.update("UPDATE agent_refresh_token SET revoked_at = ? WHERE user_id = ? AND revoked_at IS NULL", Timestamp.from(Instant.now()), userId);
    }

    public void revokeAccess(String tokenId, Instant expiresAt) {
        if (tokenId == null || tokenId.isBlank()) return;
        jdbcTemplate.update("INSERT INTO agent_access_token_revocation (token_id, expires_at, revoked_at) VALUES (?, ?, ?)", tokenId, Timestamp.from(expiresAt), Timestamp.from(Instant.now()));
    }

    public boolean isAccessRevoked(String tokenId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM agent_access_token_revocation WHERE token_id = ? AND expires_at > ?", Integer.class, tokenId, Timestamp.from(Instant.now()));
        return count != null && count > 0;
    }

    private String hash(String value) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("Unable to hash token", error);
        }
    }

    public record IssuedRefreshToken(String rawToken, AgentPrincipal principal, Instant expiresAt) {
    }
}
