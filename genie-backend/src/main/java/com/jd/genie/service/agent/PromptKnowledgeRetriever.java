package com.jd.genie.service.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.config.AgentRagProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * RAG-first prompt knowledge retriever. The classpath JSON is a versioned seed source,
 * not the serving index; it is imported into Qdrant on first successful retrieval.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PromptKnowledgeRetriever {
    private final ObjectMapper objectMapper;
    private final AgentRagProperties ragProperties;
    private final OpenAiEmbeddingClient embeddingClient;
    private final QdrantPromptKnowledgeStore qdrantStore;
    private List<PromptKnowledgeSnippet> snippets = List.of();
    private String knowledgeVersion = "image-guidelines-v1";
    private final AtomicBoolean imported = new AtomicBoolean();

    @PostConstruct
    void load() {
        try (InputStream input = new ClassPathResource("prompt-op/image-guidelines.json").getInputStream()) {
            snippets = objectMapper.readValue(input, new TypeReference<>() { });
            knowledgeVersion = "image-guidelines-v1-" + hash(snippets.toString()).substring(0, 12);
        } catch (Exception error) {
            throw new IllegalStateException("Unable to load prompt-op seed knowledge", error);
        }
    }

    public List<PromptKnowledgeHit> retrieve(String prompt, List<AgentToolType> tools, int limit) {
        if (ragProperties.isEnabled()) {
            try {
                ensureImported();
                List<Float> vector = embeddingClient.embed(List.of(query(prompt, tools))).get(0);
                List<PromptKnowledgeHit> hits = qdrantStore.search(vector, Math.min(limit, ragProperties.getSearchLimit()));
                if (!hits.isEmpty()) return hits;
            } catch (Exception error) {
                log.warn("Prompt RAG unavailable; using local-rule fallback: {}", error.getMessage());
            }
        }
        return localRetrieve(prompt, tools, limit);
    }

    public String knowledgeVersion() { return knowledgeVersion; }

    private synchronized void ensureImported() {
        if (imported.get() || !ragProperties.isAutoImport()) return;
        qdrantStore.ensureCollection();
        List<List<Float>> vectors = embeddingClient.embed(snippets.stream().map(this::document).toList());
        List<QdrantPromptKnowledgeStore.StoredKnowledge> records = new java.util.ArrayList<>();
        for (int index = 0; index < snippets.size(); index++) {
            PromptKnowledgeSnippet snippet = snippets.get(index);
            records.add(new QdrantPromptKnowledgeStore.StoredKnowledge(snippet, vectors.get(index), "classpath:prompt-op/image-guidelines.json", knowledgeVersion, hash(document(snippet))));
        }
        qdrantStore.upsert(records);
        imported.set(true);
        log.info("Imported {} PromptOp knowledge records into Qdrant version {}", records.size(), knowledgeVersion);
    }

    private List<PromptKnowledgeHit> localRetrieve(String prompt, List<AgentToolType> tools, int limit) {
        String query = prompt == null ? "" : prompt.toLowerCase(Locale.ROOT);
        List<String> toolNames = tools.stream().map(tool -> tool.name().toLowerCase(Locale.ROOT)).toList();
        return snippets.stream()
                .map(snippet -> new ScoredSnippet(snippet, score(snippet, query, toolNames)))
                .filter(scored -> scored.score() > 0)
                .sorted(Comparator.comparingInt(ScoredSnippet::score).reversed())
                .limit(Math.max(1, Math.min(limit, 10)))
                .map(scored -> new PromptKnowledgeHit(scored.snippet(), scored.score(), "classpath:prompt-op/image-guidelines.json", knowledgeVersion, false))
                .toList();
    }

    private String query(String prompt, List<AgentToolType> tools) {
        return "User image request: " + (prompt == null ? "" : prompt) + "\nRequested tools: " + tools.stream().map(AgentToolType::name).reduce((left, right) -> left + "," + right).orElse("none");
    }

    private String document(PromptKnowledgeSnippet snippet) {
        return "Title: " + snippet.title() + "\nTools: " + String.join(",", snippet.tools()) + "\nKeywords: " + String.join(",", snippet.keywords()) + "\nGuidance: " + snippet.guidance();
    }

    private String hash(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder output = new StringBuilder();
            for (byte valueByte : bytes) output.append(String.format("%02x", valueByte));
            return output.toString();
        } catch (Exception error) {
            throw new IllegalStateException("Unable to compute prompt knowledge version", error);
        }
    }

    private int score(PromptKnowledgeSnippet snippet, String query, List<String> toolNames) {
        int score = 0;
        for (String tool : snippet.tools()) if (toolNames.contains(tool.toLowerCase(Locale.ROOT))) score += 4;
        for (String keyword : snippet.keywords()) if (!keyword.isBlank() && query.contains(keyword.toLowerCase(Locale.ROOT))) score += 2;
        return score;
    }

    private record ScoredSnippet(PromptKnowledgeSnippet snippet, int score) { }
}