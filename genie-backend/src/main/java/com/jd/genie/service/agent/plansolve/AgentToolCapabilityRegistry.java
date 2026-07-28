package com.jd.genie.service.agent.plansolve;

import com.jd.genie.service.agent.AgentToolType;
import com.jd.genie.service.agent.HttpAgentToolClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Single source of truth for tools planners may select. It deliberately exposes
 * capabilities only, never endpoint URLs or provider credentials.
 */
@Component
@RequiredArgsConstructor
public class AgentToolCapabilityRegistry {
    private final HttpAgentToolClient toolClient;

    public List<AgentToolCapability> snapshot() {
        return List.of(capability(AgentToolType.OCR, true, List.of("text-recognition", "multimodal-input")),
                capability(AgentToolType.IMAGE_GENERATE, false, List.of("text-to-image", "image-output")),
                capability(AgentToolType.IMAGE_EDIT, true, List.of("image-edit", "background-replace", "image-output")));
    }

    public List<AgentToolCapability> available() {
        return snapshot().stream().filter(AgentToolCapability::available).toList();
    }

    public boolean isAvailable(AgentToolType type) {
        return toolClient.isConfigured(type);
    }

    public String plannerDescription() {
        String available = snapshot().stream().filter(AgentToolCapability::available)
                .map(item -> item.tool().name() + "(" + String.join(",", item.capabilities()) + ")")
                .collect(java.util.stream.Collectors.joining(", "));
        return available.isBlank() ? "none" : available;
    }

    private AgentToolCapability capability(AgentToolType type, boolean requiresInputImage, List<String> capabilities) {
        boolean available = toolClient.isConfigured(type);
        return new AgentToolCapability(type, available, requiresInputImage, capabilities,
                available ? "" : "runtime endpoint is not configured");
    }
}