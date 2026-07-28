package com.jd.genie.service.agent.plansolve;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.config.AgentRuntimeProperties;
import com.jd.genie.model.agent.AgentRunMode;
import com.jd.genie.model.agent.AgentRunRequest;
import com.jd.genie.service.agent.HttpAgentToolClient;
import com.jd.genie.service.agent.OpenAiCompatibleChatClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructuredPlanGeneratorTest {

    @Test
    void fallbackOnlySelectsConfiguredCapability() {
        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        properties.getTools().getImageGenerate().setEnabled(true);
        properties.getTools().getImageGenerate().setUrl("http://tool.test/generate");
        ObjectMapper mapper = new ObjectMapper();
        StructuredPlanGenerator generator = new StructuredPlanGenerator(new OpenAiCompatibleChatClient(properties, mapper), mapper,
                new PlanJsonSchemaValidator(), new AgentToolCapabilityRegistry(new HttpAgentToolClient(properties, mapper)));

        AgentRunRequest request = request("generate an image of a city", List.of());
        StructuredPlanGenerator.GeneratedPlan plan = generator.generate(request);

        assertFalse(plan.modelGenerated());
        assertEquals(1, plan.plan().tasks().size());
        assertEquals(PlanTaskKind.IMAGE_GENERATE, plan.plan().tasks().get(0).kind());
    }

    @Test
    void fallbackDoesNotInventUnavailableTool() {
        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        ObjectMapper mapper = new ObjectMapper();
        StructuredPlanGenerator generator = new StructuredPlanGenerator(new OpenAiCompatibleChatClient(properties, mapper), mapper,
                new PlanJsonSchemaValidator(), new AgentToolCapabilityRegistry(new HttpAgentToolClient(properties, mapper)));

        StructuredPlanGenerator.GeneratedPlan plan = generator.generate(request("generate an image", List.of()));

        assertEquals(PlanTaskKind.HUMAN_CONFIRMATION, plan.plan().tasks().get(0).kind());
        assertTrue(plan.plan().tasks().get(0).confirmationMessage().contains("No compatible tool"));
    }

    private AgentRunRequest request(String prompt, List<String> images) {
        AgentRunRequest request = new AgentRunRequest();
        request.setMode(AgentRunMode.PLAN_SOLVE);
        request.setPrompt(prompt);
        request.setImageUrls(images);
        return request;
    }
}