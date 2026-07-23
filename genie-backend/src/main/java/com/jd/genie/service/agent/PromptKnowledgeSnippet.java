package com.jd.genie.service.agent;

import java.util.List;

/** A versioned prompt guideline returned by the local development knowledge base. */
public record PromptKnowledgeSnippet(
        String id,
        String title,
        List<String> tools,
        List<String> keywords,
        String guidance) {
}
