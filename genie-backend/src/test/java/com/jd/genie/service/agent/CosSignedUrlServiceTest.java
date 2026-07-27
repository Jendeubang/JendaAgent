package com.jd.genie.service.agent;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CosSignedUrlServiceTest {

    @Test
    void generatesSignedUrlWithoutExposingSecretKey() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("agent.storage.cos.bucket", "jenda-agent-1455545317")
                .withProperty("agent.storage.cos.region", "ap-shanghai")
                .withProperty("agent.storage.cos.secret-id", "test-id")
                .withProperty("agent.storage.cos.secret-key", "test-secret-key")
                .withProperty("agent.storage.cos.download-expiry-seconds", "1800");
        CosSignedUrlService service = new CosSignedUrlService(environment);

        String url = service.createGetUrl("agent/2026/07/asset-1.png");

        assertTrue(url.startsWith("https://jenda-agent-1455545317.cos.ap-shanghai.myqcloud.com/agent/2026/07/asset-1.png?"));
        assertTrue(url.contains("q-sign-algorithm=sha1"));
        assertTrue(url.contains("q-signature="));
        assertFalse(url.contains("test-secret-key"));
        assertEquals("agent/2026/07/asset-1.png", service.objectKeyIfOwned(url));
        assertEquals("https://example.com/image.png", service.createGetUrlIfOwned("https://example.com/image.png"));
    }
}
