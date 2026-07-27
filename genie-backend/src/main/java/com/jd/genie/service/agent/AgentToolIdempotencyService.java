package com.jd.genie.service.agent;

import com.jd.genie.service.auth.AgentRedisSupport;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** Prevents duplicate provider charges for concurrent retries of the same user tool request. */
@Service
public class AgentToolIdempotencyService {
    private final AgentRedisSupport redis;
    private final ConcurrentHashMap<String, Instant> localLocks = new ConcurrentHashMap<>();

    public AgentToolIdempotencyService(AgentRedisSupport redis) { this.redis = redis; }

    public String acquire(String ownerUserId, AgentToolType type, String prompt, List<String> imageUrls) {
        String key = fingerprint(ownerUserId, type, prompt, imageUrls);
        Duration ttl = Duration.ofMinutes(12);
        if (redis.isEnabled()) {
            if (redis.setIfAbsent("tool-lock:" + key, "1", ttl)) return key;
            if (redis.exists("tool-lock:" + key)) return null;
        }
        Instant expiresAt = Instant.now().plus(ttl);
        Instant prior = localLocks.putIfAbsent(key, expiresAt);
        if (prior == null || prior.isBefore(Instant.now())) {
            localLocks.put(key, expiresAt);
            return key;
        }
        return null;
    }

    public void release(String key) {
        if (key == null) return;
        localLocks.remove(key);
        redis.delete("tool-lock:" + key);
    }

    private String fingerprint(String ownerUserId, AgentToolType type, String prompt, List<String> imageUrls) {
        try {
            String input = (ownerUserId == null ? "local-demo-user" : ownerUserId) + "|" + type.name() + "|" + (prompt == null ? "" : prompt) + "|" + String.join("|", imageUrls == null ? List.of() : imageUrls);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) { throw new IllegalStateException("Unable to create tool idempotency key", error); }
    }
}