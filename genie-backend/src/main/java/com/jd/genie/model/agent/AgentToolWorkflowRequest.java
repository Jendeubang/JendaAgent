package com.jd.genie.model.agent;

import jakarta.validation.constraints.Size;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Public request for one authenticated image-tool workflow. */
public record AgentToolWorkflowRequest(
        @Size(max = 10) List<String> inputAssetIds,
        @Size(max = 8000) String prompt,
        @Size(max = 80) String modelProvider,
        Map<String, Object> parameters) {
    public AgentToolWorkflowRequest {
        inputAssetIds = inputAssetIds == null ? List.of() : inputAssetIds.stream().filter(value -> value != null && !value.isBlank()).toList();
        parameters = parameters == null ? Map.of() : new LinkedHashMap<>(parameters);
        prompt = prompt == null ? "" : prompt.trim();
        modelProvider = modelProvider == null ? "" : modelProvider.trim();
    }
}