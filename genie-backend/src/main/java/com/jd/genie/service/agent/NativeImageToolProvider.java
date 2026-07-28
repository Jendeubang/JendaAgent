package com.jd.genie.service.agent;

import com.jd.genie.model.agent.AgentToolWorkflowRequest;

import java.util.List;

/** Provider boundary for tool-specific image capabilities that are not generic image generation. */
public interface NativeImageToolProvider {
    String id();
    boolean supports(String toolId);
    boolean isConfigured();
    String endpoint();
    String resultHostSuffixes();
    ImageToolProviderResult execute(String toolId, AgentToolWorkflowRequest request, List<String> imageUrls);
}