package com.jd.genie.service.agent;

import com.jd.genie.model.agent.StoredAgentImage;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CosAgentImageStorageTest {

    @Test
    void buildsHttpsUrlFromBucketRegionAndObjectKey() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("agent.storage.cos.bucket", "jenda-agent-1455545317")
                .withProperty("agent.storage.cos.region", "ap-shanghai")
                .withProperty("agent.storage.cos.secret-id", "test-id")
                .withProperty("agent.storage.cos.secret-key", "test-key")
                .withProperty("agent.storage.cos.prefix", "agent/");
        CosAgentImageStorage storage = new CosAgentImageStorage(environment);
        StoredAgentImage image = new StoredAgentImage("asset-1", "reference image.png",
                "agent/2026/07/asset-1.png", "image/png", 10);

        assertEquals("https://jenda-agent-1455545317.cos.ap-shanghai.myqcloud.com/agent/2026/07/asset-1.png",
                storage.imageUrl(image));
    }
}
