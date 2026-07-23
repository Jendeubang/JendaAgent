package com.jd.genie.service.agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts a PromptOp result to a stable, replayable SSE payload. */
public final class PromptOptimizationEventPayload {
    private PromptOptimizationEventPayload() { }

    public static Map<String, Object> from(PromptOptimization result, List<AgentToolType> tools) {
        List<Map<String, Object>> hits = result.knowledgeHits().stream().map(hit -> Map.<String, Object>of(
                "id", hit.snippet().id(), "title", hit.snippet().title(), "score", hit.score(),
                "source", hit.source(), "version", hit.knowledgeVersion(), "vectorRetrieved", hit.vectorRetrieved())).toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", "PromptOpAgent: prompt optimization");
        payload.put("content", result.detail());
        payload.put("applied", result.applied());
        payload.put("originalPrompt", result.originalPrompt());
        payload.put("optimizedPrompt", result.optimizedPrompt());
        payload.put("retrievedRules", result.retrievedRules());
        payload.put("knowledgeHits", hits);
        payload.put("knowledgeVersion", result.knowledgeVersion());
        payload.put("ragUsed", result.ragUsed());
        payload.put("provider", result.provider());
        payload.put("toolTypes", tools.stream().map(AgentToolType::name).toList());
        return payload;
    }
}