package com.jd.genie.model.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Normalized request accepted by internal tool adapters. */
public record AgentToolGatewayRequest(
        @NotBlank @Size(max = 8000) String prompt,
        @Size(max = 10) List<String> image_urls,
        @NotBlank String task_type,
        String model_provider,
        String owner_user_id) {
}
