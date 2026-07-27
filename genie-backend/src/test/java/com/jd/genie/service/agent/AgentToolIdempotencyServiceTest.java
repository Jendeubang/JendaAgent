package com.jd.genie.service.agent;

import com.jd.genie.service.auth.AgentRedisSupport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentToolIdempotencyServiceTest {
    @Test
    void rejectsConcurrentDuplicateAndAllowsRetryAfterRelease() {
        AgentRedisSupport redis = mock(AgentRedisSupport.class);
        when(redis.isEnabled()).thenReturn(false);
        AgentToolIdempotencyService service = new AgentToolIdempotencyService(redis);

        String lock = service.acquire("user-1", AgentToolType.IMAGE_GENERATE, "portrait", List.of("https://example.test/a.png"));
        assertNotNull(lock);
        assertNull(service.acquire("user-1", AgentToolType.IMAGE_GENERATE, "portrait", List.of("https://example.test/a.png")));

        service.release(lock);
        assertNotNull(service.acquire("user-1", AgentToolType.IMAGE_GENERATE, "portrait", List.of("https://example.test/a.png")));
    }
}