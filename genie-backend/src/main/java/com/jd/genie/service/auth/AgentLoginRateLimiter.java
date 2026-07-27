package com.jd.genie.service.auth;

import com.jd.genie.config.AgentAuthProperties;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

/** Redis-backed login guard with the original JDBC data as an availability fallback. */
@Repository
public class AgentLoginRateLimiter {
    private final JdbcTemplate jdbcTemplate;
    private final AgentAuthProperties properties;
    private final AgentRedisSupport redis;

    public AgentLoginRateLimiter(Environment environment, AgentAuthProperties properties, AgentRedisSupport redis) {
        String url = environment.getProperty("agent.history.jdbc-url", "jdbc:h2:file:./runtime/agent-history;MODE=MySQL");
        String username = environment.getProperty("agent.history.username", "sa");
        String password = environment.getProperty("agent.history.password", "");
        this.jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(url, username, password));
        this.properties = properties;
        this.redis = redis;
    }

    @PostConstruct
    void initializeSchema() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS agent_login_guard (guard_key VARCHAR(160) PRIMARY KEY, failures INT NOT NULL, window_started_at TIMESTAMP NOT NULL, locked_until TIMESTAMP NULL, updated_at TIMESTAMP NOT NULL)");
    }

    public void assertAllowed(String username, String clientIp) {
        assertKeyAllowed("user:" + username);
        assertKeyAllowed("ip:" + clientIp);
    }

    public void recordFailure(String username, String clientIp) {
        recordKeyFailure("user:" + username);
        recordKeyFailure("ip:" + clientIp);
    }

    public void reset(String username, String clientIp) {
        String userKey = "user:" + username;
        String ipKey = "ip:" + clientIp;
        redis.delete(redisKey("login:count:", userKey));
        redis.delete(redisKey("login:count:", ipKey));
        redis.delete(redisKey("login:lock:", userKey));
        redis.delete(redisKey("login:lock:", ipKey));
        jdbcTemplate.update("DELETE FROM agent_login_guard WHERE guard_key IN (?, ?)", userKey, ipKey);
    }

    private void assertKeyAllowed(String key) {
        if (redis.exists(redisKey("login:lock:", key))) {
            throw new LoginRateLimitException("Too many failed login attempts. Try again later.");
        }
        List<GuardState> states = jdbcTemplate.query("SELECT failures, window_started_at, locked_until FROM agent_login_guard WHERE guard_key = ?", (resultSet, rowNum) -> new GuardState(resultSet.getInt("failures"), resultSet.getTimestamp("window_started_at").toInstant(), resultSet.getTimestamp("locked_until") == null ? null : resultSet.getTimestamp("locked_until").toInstant()), key);
        if (!states.isEmpty() && states.get(0).lockedUntil() != null && states.get(0).lockedUntil().isAfter(Instant.now())) {
            throw new LoginRateLimitException("Too many failed login attempts. Try again later.");
        }
    }

    private void recordKeyFailure(String key) {
        redis.increment(redisKey("login:count:", key), properties.getLoginFailureWindow()).ifPresent(failures -> {
            if (failures >= properties.getLoginMaxFailures()) {
                redis.set(redisKey("login:lock:", key), "1", properties.getLoginFailureWindow());
            }
        });
        Instant now = Instant.now();
        List<GuardState> states = jdbcTemplate.query("SELECT failures, window_started_at, locked_until FROM agent_login_guard WHERE guard_key = ?", (resultSet, rowNum) -> new GuardState(resultSet.getInt("failures"), resultSet.getTimestamp("window_started_at").toInstant(), resultSet.getTimestamp("locked_until") == null ? null : resultSet.getTimestamp("locked_until").toInstant()), key);
        int failures = 1;
        if (!states.isEmpty() && states.get(0).windowStartedAt().plus(properties.getLoginFailureWindow()).isAfter(now)) failures = states.get(0).failures() + 1;
        Instant lockUntil = failures >= properties.getLoginMaxFailures() ? now.plus(properties.getLoginFailureWindow()) : null;
        if (states.isEmpty()) {
            jdbcTemplate.update("INSERT INTO agent_login_guard (guard_key, failures, window_started_at, locked_until, updated_at) VALUES (?, ?, ?, ?, ?)", key, failures, Timestamp.from(now), lockUntil == null ? null : Timestamp.from(lockUntil), Timestamp.from(now));
        } else {
            jdbcTemplate.update("UPDATE agent_login_guard SET failures = ?, window_started_at = ?, locked_until = ?, updated_at = ? WHERE guard_key = ?", failures, Timestamp.from(now), lockUntil == null ? null : Timestamp.from(lockUntil), Timestamp.from(now), key);
        }
    }

    private String redisKey(String kind, String raw) {
        try {
            return kind + Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("Unable to create login guard key", error);
        }
    }

    private record GuardState(int failures, Instant windowStartedAt, Instant lockedUntil) { }

    public static class LoginRateLimitException extends RuntimeException {
        public LoginRateLimitException(String message) { super(message); }
    }
}