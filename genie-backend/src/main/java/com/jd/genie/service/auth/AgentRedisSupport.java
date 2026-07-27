package com.jd.genie.service.auth;

import com.jd.genie.config.AgentRedisProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis fast-path with a deliberate fail-open signal for local development.
 * Callers retain their persistent-store fallback when Redis is unavailable.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentRedisSupport {
    private final ObjectProvider<StringRedisTemplate> templates;
    private final AgentRedisProperties properties;

    public Optional<String> get(String suffix) {
        try {
            return Optional.ofNullable(template().opsForValue().get(key(suffix)));
        } catch (RuntimeException error) {
            unavailable(error);
            return Optional.empty();
        }
    }

    public Optional<String> getAndDelete(String suffix) {
        try {
            return Optional.ofNullable(template().opsForValue().getAndDelete(key(suffix)));
        } catch (RuntimeException error) {
            unavailable(error);
            return Optional.empty();
        }
    }

    public boolean setIfAbsent(String suffix, String value, Duration ttl) {
        try {
            return Boolean.TRUE.equals(template().opsForValue().setIfAbsent(key(suffix), value, ttl));
        } catch (RuntimeException error) {
            unavailable(error);
            return false;
        }
    }

    public boolean set(String suffix, String value, Duration ttl) {
        try {
            template().opsForValue().set(key(suffix), value, ttl);
            return true;
        } catch (RuntimeException error) {
            unavailable(error);
            return false;
        }
    }

    public Optional<Long> increment(String suffix, Duration firstWriteTtl) {
        try {
            Long value = template().opsForValue().increment(key(suffix));
            if (value != null && value == 1L) template().expire(key(suffix), firstWriteTtl);
            return Optional.ofNullable(value);
        } catch (RuntimeException error) {
            unavailable(error);
            return Optional.empty();
        }
    }

    public boolean exists(String suffix) {
        try {
            return Boolean.TRUE.equals(template().hasKey(key(suffix)));
        } catch (RuntimeException error) {
            unavailable(error);
            return false;
        }
    }

    public void delete(String suffix) {
        try {
            template().delete(key(suffix));
        } catch (RuntimeException error) {
            unavailable(error);
        }
    }

    public boolean isEnabled() {
        return properties.isEnabled() && templates.getIfAvailable() != null;
    }

    private StringRedisTemplate template() {
        if (!isEnabled()) throw new IllegalStateException("Redis is disabled");
        return templates.getObject();
    }

    private String key(String suffix) {
        return properties.getKeyPrefix() + suffix;
    }

    private void unavailable(RuntimeException error) {
        log.debug("Redis security fast-path unavailable; using persistent fallback: {}", error.getClass().getSimpleName());
    }
}