package com.jd.genie.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.config.AgentRuntimeProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRuntimeClientsTest {

    @Test
    void skipsModelRequestWhenModelIsDisabled() {
        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        OpenAiCompatibleChatClient client = new OpenAiCompatibleChatClient(properties, new ObjectMapper());

        ModelCompletion result = client.complete("system", "prompt", List.of("https://example.test/reference.png"));

        assertFalse(result.invoked());
        assertTrue(result.content().contains("模型未启用"));
    }

    @Test
    void skipsUnconfiguredToolWithoutMakingHttpRequest() {
        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        HttpAgentToolClient client = new HttpAgentToolClient(properties, new ObjectMapper());

        AgentToolResult result = client.execute(AgentToolType.OCR, "识别图片文字", List.of("https://example.test/reference.png"));

        assertFalse(result.invoked());
        assertFalse(result.success());
        assertTrue(result.summary().contains("未配置"));
    }
}
