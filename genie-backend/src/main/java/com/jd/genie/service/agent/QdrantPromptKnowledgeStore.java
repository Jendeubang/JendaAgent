package com.jd.genie.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.config.AgentRagProperties;
import lombok.RequiredArgsConstructor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small REST client isolated from the legacy DataAgent Qdrant gRPC client. */
@Component
@RequiredArgsConstructor
public class QdrantPromptKnowledgeStore {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final AgentRagProperties properties;
    private final ObjectMapper objectMapper;

    public void ensureCollection() {
        ResponseResult existing = request("GET", "/collections/" + properties.getCollection(), null);
        if (existing.status() == 200) return;
        if (existing.status() != 404) throw new IllegalStateException("Qdrant collection check failed: HTTP " + existing.status());
        Map<String, Object> payload = Map.of("vectors", Map.of("size", dimensions(), "distance", "Cosine"));
        ResponseResult created = request("PUT", "/collections/" + properties.getCollection(), payload);
        if (created.status() / 100 != 2) throw new IllegalStateException("Qdrant collection creation failed: HTTP " + created.status());
    }

    public void upsert(List<StoredKnowledge> records) {
        if (records.isEmpty()) return;
        List<Map<String, Object>> points = new ArrayList<>();
        for (StoredKnowledge record : records) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("id", record.snippet().id());
            payload.put("title", record.snippet().title());
            payload.put("tools", record.snippet().tools());
            payload.put("keywords", record.snippet().keywords());
            payload.put("guidance", record.snippet().guidance());
            payload.put("source", record.source());
            payload.put("version", record.version());
            payload.put("contentHash", record.contentHash());
            points.add(Map.of("id", pointId(record.snippet().id(), record.version()), "vector", record.vector(), "payload", payload));
        }
        ResponseResult result = request("PUT", "/collections/" + properties.getCollection() + "/points?wait=true", Map.of("points", points));
        if (result.status() / 100 != 2) throw new IllegalStateException("Qdrant upsert failed: HTTP " + result.status());
    }

    public List<PromptKnowledgeHit> search(List<Float> vector, int limit) {
        Map<String, Object> payload = Map.of("query", vector, "limit", Math.max(1, Math.min(limit, 10)), "with_payload", true, "score_threshold", properties.getScoreThreshold());
        ResponseResult result = request("POST", "/collections/" + properties.getCollection() + "/points/query", payload);
        if (result.status() == 404) {
            payload = Map.of("vector", vector, "limit", Math.max(1, Math.min(limit, 10)), "with_payload", true, "score_threshold", properties.getScoreThreshold());
            result = request("POST", "/collections/" + properties.getCollection() + "/points/search", payload);
        }
        if (result.status() / 100 != 2) throw new IllegalStateException("Qdrant search failed: HTTP " + result.status());
        try {
            JsonNode root = objectMapper.readTree(result.body());
            JsonNode points = root.path("result").isArray() ? root.path("result") : root.path("result").path("points");
            List<PromptKnowledgeHit> hits = new ArrayList<>();
            for (JsonNode point : points) {
                JsonNode source = point.path("payload");
                PromptKnowledgeSnippet snippet = new PromptKnowledgeSnippet(source.path("id").asText(), source.path("title").asText(), strings(source.path("tools")), strings(source.path("keywords")), source.path("guidance").asText());
                hits.add(new PromptKnowledgeHit(snippet, point.path("score").asDouble(), source.path("source").asText(), source.path("version").asText(), true));
            }
            return hits;
        } catch (Exception error) {
            throw new IllegalStateException("Unable to parse Qdrant search response", error);
        }
    }

    private ResponseResult request(String method, String path, Object payload) {
        try {
            Request.Builder builder = new Request.Builder().url(baseUrl() + path).header("Content-Type", "application/json");
            if (properties.getQdrantApiKey() != null && !properties.getQdrantApiKey().isBlank()) builder.header("api-key", properties.getQdrantApiKey());
            RequestBody body = payload == null ? null : RequestBody.create(objectMapper.writeValueAsString(payload), JSON);
            if ("GET".equals(method)) builder.get(); else if ("PUT".equals(method)) builder.put(body); else builder.post(body);
            OkHttpClient client = new OkHttpClient.Builder().callTimeout(timeout()).build();
            try (Response response = client.newCall(builder.build()).execute()) { return new ResponseResult(response.code(), response.body() == null ? "" : response.body().string()); }
        } catch (Exception error) { throw new IllegalStateException("Qdrant request failed: " + (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()), error); }
    }
    private List<String> strings(JsonNode values) { List<String> result = new ArrayList<>(); if (values.isArray()) for (JsonNode value : values) result.add(value.asText()); return result; }
    private String baseUrl() { String value = properties.getQdrantUrl(); return value.endsWith("/") ? value.substring(0, value.length() - 1) : value; }
    private int dimensions() { return Math.max(64, Math.min(properties.getEmbeddingDimensions(), 4096)); }
    private Duration timeout() { return properties.getTimeout() == null || properties.getTimeout().isNegative() || properties.getTimeout().isZero() ? Duration.ofSeconds(20) : properties.getTimeout(); }
    private String pointId(String id, String version) { return sha256(id + "|" + version).substring(0, 32); }
    private String sha256(String value) { try { byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); StringBuilder out = new StringBuilder(); for (byte valueByte : bytes) out.append(String.format("%02x", valueByte)); return out.toString(); } catch (Exception error) { throw new IllegalStateException(error); } }

    public record StoredKnowledge(PromptKnowledgeSnippet snippet, List<Float> vector, String source, String version, String contentHash) { }
    private record ResponseResult(int status, String body) { }
}
