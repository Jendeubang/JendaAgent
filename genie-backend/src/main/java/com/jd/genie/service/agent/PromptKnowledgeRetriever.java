package com.jd.genie.service.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Development knowledge retriever for prompt guidelines.
 * Replace this component with a vector-store implementation in production without changing PromptOpAgent.
 */
@Component
@RequiredArgsConstructor
public class PromptKnowledgeRetriever {
    private final ObjectMapper objectMapper;
    private List<PromptKnowledgeSnippet> snippets = List.of();

    @PostConstruct
    void load() {
        try (InputStream input = new ClassPathResource("prompt-op/image-guidelines.json").getInputStream()) {
            snippets = objectMapper.readValue(input, new TypeReference<>() { });
        } catch (Exception error) {
            throw new IllegalStateException("Unable to load prompt-op knowledge base", error);
        }
    }

    public List<PromptKnowledgeSnippet> retrieve(String prompt, List<AgentToolType> tools, int limit) {
        String query = prompt == null ? "" : prompt.toLowerCase(Locale.ROOT);
        List<String> toolNames = tools.stream().map(tool -> tool.name().toLowerCase(Locale.ROOT)).toList();
        return snippets.stream()
                .map(snippet -> new ScoredSnippet(snippet, score(snippet, query, toolNames)))
                .filter(scored -> scored.score() > 0)
                .sorted(Comparator.comparingInt(ScoredSnippet::score).reversed())
                .limit(Math.max(1, Math.min(limit, 10)))
                .map(ScoredSnippet::snippet)
                .toList();
    }

    private int score(PromptKnowledgeSnippet snippet, String query, List<String> toolNames) {
        int score = 0;
        for (String tool : snippet.tools()) {
            if (toolNames.contains(tool.toLowerCase(Locale.ROOT))) {
                score += 4;
            }
        }
        for (String keyword : snippet.keywords()) {
            if (!keyword.isBlank() && query.contains(keyword.toLowerCase(Locale.ROOT))) {
                score += 2;
            }
        }
        return score;
    }

    private record ScoredSnippet(PromptKnowledgeSnippet snippet, int score) {
    }
}
