package com.jd.genie.service.agent;

import com.jd.genie.model.agent.AgentToolGatewayRequest;

/** Provider boundary for image generation and reference-image editing. */
public interface ImageModelProvider {
    String id();

    ImageModelResult generate(AgentToolGatewayRequest request);

    ImageModelResult edit(AgentToolGatewayRequest request);
}