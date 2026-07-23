package com.jd.genie.service.agent;

/** A prompt guideline returned by the RAG store, including provenance for the UI. */
public record PromptKnowledgeHit(
        PromptKnowledgeSnippet snippet,
        double score,
        String source,
        String knowledgeVersion,
        boolean vectorRetrieved) {
}
