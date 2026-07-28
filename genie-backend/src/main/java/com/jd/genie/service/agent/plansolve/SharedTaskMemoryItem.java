package com.jd.genie.service.agent.plansolve;

import java.util.List;
import java.util.Map;

/** Immutable, traceable context from one DAG node. */
public record SharedTaskMemoryItem(
        String taskId,
        String tool,
        String state,
        int attempt,
        Map<String, Object> input,
        Map<String, Object> output,
        List<String> assetIds,
        String failureReason,
        String summary) {
    public SharedTaskMemoryItem {
        input = input == null ? Map.of() : Map.copyOf(input);
        output = output == null ? Map.of() : Map.copyOf(output);
        assetIds = assetIds == null ? List.of() : List.copyOf(assetIds);
        failureReason = failureReason == null ? "" : failureReason;
        summary = summary == null ? "" : summary;
    }
}