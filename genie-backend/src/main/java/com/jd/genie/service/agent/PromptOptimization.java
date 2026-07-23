package com.jd.genie.service.agent;

import java.util.List;

/** Result returned by PromptOpAgent and exposed as a typed SSE event. */
public record PromptOptimization(
        boolean applied,
        String originalPrompt,
        String optimizedPrompt,
        List<String> retrievedRules,
        List<PromptKnowledgeHit> knowledgeHits,
        String knowledgeVersion,
        boolean ragUsed,
        String provider,
        String detail) {

    public static PromptOptimization skipped(String prompt, String detail) {
        return new PromptOptimization(false, prompt, prompt, List.of(), List.of(), "", false, "not-configured", detail);
    }
}