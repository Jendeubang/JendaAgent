package com.jd.genie.service.auth;

import com.jd.genie.model.auth.AgentPrincipal;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Isolated product user table, with a unique mobile number for SMS verification. */
@Repository
public class AgentUserStore {
    private final JdbcTemplate jdbcTemplate;

    public AgentUserStore(Environment environment) {
        String url = environment.getProperty("agent.history.jdbc-url", "jdbc:h2:file:./runtime/agent-history;MODE=MySQL");
        String username = environment.getProperty("agent.history.username", "sa");
        String password = environment.getProperty("agent.history.password", "");
        this.jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(url, username, password));
    }

    @PostConstruct
    void initializeSchema() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS agent_user (user_id VARCHAR(64) PRIMARY KEY, username VARCHAR(32) NOT NULL UNIQUE, password_hash VARCHAR(512) NOT NULL, phone VARCHAR(32) NULL, created_at TIMESTAMP NOT NULL)");
        try { jdbcTemplate.execute("ALTER TABLE agent_user ADD COLUMN phone VARCHAR(32) NULL"); } catch (RuntimeException ignored) { }
        try { jdbcTemplate.execute("CREATE UNIQUE INDEX idx_agent_user_phone ON agent_user(phone)"); } catch (RuntimeException ignored) { }
    }

    public AgentPrincipal create(String username, String phone, String passwordHash) {
        String normalizedUsername = normalizeUsername(username);
        String normalizedPhone = normalizePhone(phone);
        if (findByUsername(normalizedUsername) != null) throw new IllegalArgumentException("Username is already registered");
        if (findByPhone(normalizedPhone) != null) throw new IllegalArgumentException("Phone is already registered");
        AgentPrincipal principal = new AgentPrincipal(UUID.randomUUID().toString(), normalizedUsername);
        jdbcTemplate.update("INSERT INTO agent_user (user_id, username, password_hash, phone, created_at) VALUES (?, ?, ?, ?, ?)", principal.userId(), principal.username(), passwordHash, normalizedPhone, Timestamp.from(Instant.now()));
        return principal;
    }

    public StoredUser findByUsername(String username) {
        List<StoredUser> users = jdbcTemplate.query("SELECT user_id, username, password_hash, phone FROM agent_user WHERE username = ?", (resultSet, rowNum) -> new StoredUser(new AgentPrincipal(resultSet.getString("user_id"), resultSet.getString("username")), resultSet.getString("password_hash"), resultSet.getString("phone")), normalizeUsername(username));
        return users.isEmpty() ? null : users.get(0);
    }

    public StoredUser findByPhone(String phone) {
        List<StoredUser> users = jdbcTemplate.query("SELECT user_id, username, password_hash, phone FROM agent_user WHERE phone = ?", (resultSet, rowNum) -> new StoredUser(new AgentPrincipal(resultSet.getString("user_id"), resultSet.getString("username")), resultSet.getString("password_hash"), resultSet.getString("phone")), normalizePhone(phone));
        return users.isEmpty() ? null : users.get(0);
    }

    public void updatePassword(String userId, String passwordHash) {
        jdbcTemplate.update("UPDATE agent_user SET password_hash = ? WHERE user_id = ?", passwordHash, userId);
    }

    public String normalizeUsername(String username) { return username.strip().toLowerCase(java.util.Locale.ROOT); }
    private String normalizePhone(String phone) { return phone == null ? null : phone.strip(); }

    public record StoredUser(AgentPrincipal principal, String passwordHash, String phone) {
    }
}