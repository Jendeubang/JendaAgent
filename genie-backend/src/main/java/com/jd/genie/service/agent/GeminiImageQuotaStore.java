package com.jd.genie.service.agent;

import com.jd.genie.config.GeminiImageQuotaProperties;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import java.sql.Date;
import java.time.LocalDate;

/** Atomically reserves one daily Gemini image request for the authenticated owner. */
@Repository
public class GeminiImageQuotaStore {
    private final JdbcTemplate jdbcTemplate;
    private final GeminiImageQuotaProperties properties;

    public GeminiImageQuotaStore(Environment environment, GeminiImageQuotaProperties properties) {
        this.properties = properties;
        this.jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(
                environment.getProperty("agent.history.jdbc-url", "jdbc:h2:file:./runtime/agent-history;MODE=MySQL"),
                environment.getProperty("agent.history.username", "sa"),
                environment.getProperty("agent.history.password", "")));
    }

    @PostConstruct
    void initializeSchema() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS agent_image_provider_usage (owner_user_id VARCHAR(64) NOT NULL, provider VARCHAR(64) NOT NULL, usage_day DATE NOT NULL, call_count INT NOT NULL, PRIMARY KEY (owner_user_id, provider, usage_day))");
    }

    public Reservation reserve(String ownerUserId, String provider, int limit) {
        if (!properties.isEnabled() || limit <= 0) return Reservation.noop();
        String owner = ownerUserId == null || ownerUserId.isBlank() ? "local-demo-user" : ownerUserId;
        Date day = Date.valueOf(LocalDate.now());
        int updated = jdbcTemplate.update("UPDATE agent_image_provider_usage SET call_count = call_count + 1 WHERE owner_user_id = ? AND provider = ? AND usage_day = ? AND call_count < ?", owner, provider, day, limit);
        if (updated == 1) return new Reservation(owner, provider, day, true);
        try {
            jdbcTemplate.update("INSERT INTO agent_image_provider_usage (owner_user_id, provider, usage_day, call_count) VALUES (?, ?, ?, 1)", owner, provider, day);
            return new Reservation(owner, provider, day, true);
        } catch (RuntimeException duplicate) {
            updated = jdbcTemplate.update("UPDATE agent_image_provider_usage SET call_count = call_count + 1 WHERE owner_user_id = ? AND provider = ? AND usage_day = ? AND call_count < ?", owner, provider, day, limit);
            if (updated == 1) return new Reservation(owner, provider, day, true);
            throw new IllegalStateException("Daily quota reached for " + provider + "; limit=" + limit);
        }
    }

    public void release(Reservation reservation) {
        if (reservation == null || !reservation.active()) return;
        jdbcTemplate.update("UPDATE agent_image_provider_usage SET call_count = CASE WHEN call_count > 0 THEN call_count - 1 ELSE 0 END WHERE owner_user_id = ? AND provider = ? AND usage_day = ?", reservation.ownerUserId(), reservation.provider(), reservation.day());
    }

    public record Reservation(String ownerUserId, String provider, Date day, boolean active) {
        static Reservation noop() { return new Reservation("", "", null, false); }
    }
}