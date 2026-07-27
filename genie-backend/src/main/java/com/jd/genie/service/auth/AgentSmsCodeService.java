package com.jd.genie.service.auth;

import com.jd.genie.config.AgentAuthProperties;
import com.jd.genie.model.auth.AgentSmsPurpose;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/** Issues one-time SMS codes through Redis first and keeps JDBC as an outage-safe fallback. */
@Slf4j
@Service
public class AgentSmsCodeService {
    private final JdbcTemplate jdbcTemplate;
    private final AgentAuthProperties properties;
    private final AgentPasswordCodec passwordCodec;
    private final TencentCloudSmsClient smsClient;
    private final AgentRedisSupport redis;
    private final SecureRandom random = new SecureRandom();

    public AgentSmsCodeService(Environment environment, AgentAuthProperties properties, AgentPasswordCodec passwordCodec,
                               TencentCloudSmsClient smsClient, AgentRedisSupport redis) {
        String url = environment.getProperty("agent.history.jdbc-url", "jdbc:h2:file:./runtime/agent-history;MODE=MySQL");
        String username = environment.getProperty("agent.history.username", "sa");
        String password = environment.getProperty("agent.history.password", "");
        this.jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(url, username, password));
        this.properties = properties;
        this.passwordCodec = passwordCodec;
        this.smsClient = smsClient;
        this.redis = redis;
    }

    @PostConstruct
    void initializeSchema() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS agent_sms_code (code_id VARCHAR(64) PRIMARY KEY, phone VARCHAR(32) NOT NULL, purpose VARCHAR(32) NOT NULL, code_hash VARCHAR(512) NOT NULL, expires_at TIMESTAMP NOT NULL, attempts INT NOT NULL, consumed_at TIMESTAMP NULL, created_at TIMESTAMP NOT NULL)");
        try { jdbcTemplate.execute("CREATE INDEX idx_agent_sms_lookup ON agent_sms_code(phone, purpose, created_at)"); } catch (RuntimeException ignored) { }
    }

    public IssuedCode issue(String phone, AgentSmsPurpose purpose) {
        String normalizedPhone = normalize(phone);
        Instant now = Instant.now();
        String baseKey = redisKey(normalizedPhone, purpose);
        boolean acquiredCooldown = redis.setIfAbsent("sms:cooldown:" + baseKey, "1", java.time.Duration.ofSeconds(60));
        // A reachable key means a real cooldown; an unavailable Redis node falls through to JDBC.
        if (!acquiredCooldown && redis.exists("sms:cooldown:" + baseKey)) {
            throw new IllegalArgumentException("Please wait 60 seconds before requesting another code");
        }
        List<Instant> recent = jdbcTemplate.query("SELECT created_at FROM agent_sms_code WHERE phone = ? AND purpose = ? ORDER BY created_at DESC LIMIT 1", (resultSet, rowNum) -> resultSet.getTimestamp(1).toInstant(), normalizedPhone, purpose.name());
        if (!recent.isEmpty() && recent.get(0).plusSeconds(60).isAfter(now)) throw new IllegalArgumentException("Please wait 60 seconds before requesting another code");
        String code = String.format("%06d", random.nextInt(1_000_000));
        Instant expiresAt = now.plus(properties.getSmsCodeTtl());
        String encoded = passwordCodec.encode(code);
        redis.set("sms:code:" + baseKey, encoded + "|0", properties.getSmsCodeTtl());
        jdbcTemplate.update("INSERT INTO agent_sms_code (code_id, phone, purpose, code_hash, expires_at, attempts, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)", java.util.UUID.randomUUID().toString(), normalizedPhone, purpose.name(), encoded, Timestamp.from(expiresAt), 0, Timestamp.from(now));
        if (properties.isSmsEnabled()) smsClient.send(normalizedPhone, code, Math.max(1, properties.getSmsCodeTtl().toMinutes()));
        else log.warn("SMS development mode code for {} {}: {}", purpose, normalizedPhone, code);
        return new IssuedCode(expiresAt, properties.isSmsDevelopmentMode() ? code : null);
    }

    public void verifyAndConsume(String phone, AgentSmsPurpose purpose, String code) {
        String normalizedPhone = normalize(phone);
        String baseKey = redisKey(normalizedPhone, purpose);
        java.util.Optional<String> cached = redis.get("sms:code:" + baseKey);
        if (cached.isPresent()) {
            String[] fields = cached.get().split("\\|", 2);
            int attempts = fields.length == 2 ? Integer.parseInt(fields[1]) : 5;
            if (attempts >= 5 || !passwordCodec.matches(code, fields[0])) {
                redis.set("sms:code:" + baseKey, fields[0] + "|" + (attempts + 1), properties.getSmsCodeTtl());
                throw new IllegalArgumentException("Verification code is invalid or expired");
            }
            if (redis.getAndDelete("sms:code:" + baseKey).isEmpty()) throw new IllegalArgumentException("Verification code is already used");
            consumeJdbc(normalizedPhone, purpose, code);
            return;
        }
        consumeJdbc(normalizedPhone, purpose, code);
    }

    private void consumeJdbc(String normalizedPhone, AgentSmsPurpose purpose, String code) {
        List<StoredCode> codes = jdbcTemplate.query("SELECT code_id, code_hash, expires_at, attempts FROM agent_sms_code WHERE phone = ? AND purpose = ? AND consumed_at IS NULL ORDER BY created_at DESC LIMIT 1", (resultSet, rowNum) -> new StoredCode(resultSet.getString("code_id"), resultSet.getString("code_hash"), resultSet.getTimestamp("expires_at").toInstant(), resultSet.getInt("attempts")), normalizedPhone, purpose.name());
        if (codes.isEmpty()) throw new IllegalArgumentException("Verification code is invalid or expired");
        StoredCode stored = codes.get(0);
        if (stored.expiresAt().isBefore(Instant.now()) || stored.attempts() >= 5) throw new IllegalArgumentException("Verification code is invalid or expired");
        if (!passwordCodec.matches(code, stored.codeHash())) {
            jdbcTemplate.update("UPDATE agent_sms_code SET attempts = attempts + 1 WHERE code_id = ?", stored.codeId());
            throw new IllegalArgumentException("Verification code is invalid or expired");
        }
        int updated = jdbcTemplate.update("UPDATE agent_sms_code SET consumed_at = ? WHERE code_id = ? AND consumed_at IS NULL", Timestamp.from(Instant.now()), stored.codeId());
        if (updated != 1) throw new IllegalArgumentException("Verification code is already used");
    }

    private String redisKey(String phone, AgentSmsPurpose purpose) { return purpose.name().toLowerCase() + ":" + phone; }
    private String normalize(String phone) { if (phone == null || !phone.matches("1[3-9]\\d{9}")) throw new IllegalArgumentException("Invalid mainland China mobile number"); return phone; }
    private record StoredCode(String codeId, String codeHash, Instant expiresAt, int attempts) { }
    public record IssuedCode(Instant expiresAt, String debugCode) { }
}