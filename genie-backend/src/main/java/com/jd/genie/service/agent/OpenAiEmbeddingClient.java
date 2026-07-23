package com.jd.genie.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.config.AgentRagProperties;
import com.jd.genie.config.AgentRuntimeProperties;
import lombok.RequiredArgsConstructor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Calls an OpenAI-compatible embeddings endpoint such as DashScope Model Studio. */
@Component
@RequiredArgsConstructor
public class OpenAiEmbeddingClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final AgentRagProperties properties;
    private final AgentRuntimeProperties runtimeProperties;
    private final ObjectMapper objectMapper;

    public List<List<Float>> embed(List<String> inputs) {
        if (inputs == null || inputs.isEmpty()) return List.of();
        String baseUrl = valueOr(properties.getEmbeddingBaseUrl(), runtimeProperties.getModel().getBaseUrl());
        String apiKey = valueOr(properties.getEmbeddingApiKey(), runtimeProperties.getModel().getApiKey());
        if (blank(baseUrl) || blank(apiKey)) {
            throw new IllegalStateException("RAG embedding configuration is incomplete");
        }
        try {
            Map<String, Object> payload = Map.of(
                    "model", properties.getEmbeddingModel(),
                    "input", inputs,
                    "dimensions", boundedDimensions(),
                    "encoding_format", "float");
            OkHttpClient client = new OkHttpClient.Builder().callTimeout(safeTimeout()).build();
            Request request = new Request.Builder().url(embeddingsUrl(baseUrl))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(objectMapper.writeValueAsString(payload), JSON)).build();
            try (Response response = client.newCall(request).execute()) {
                String body = response.body() == null ? "" : response.body().string();
                if (!response.isSuccessful()) throw new IllegalStateException("Embedding HTTP " + response.code() + ": " + concise(body));
                JsonNode data = objectMapper.readTree(body).path("data");
                if (!data.isArray() || data.size() != inputs.size()) throw new IllegalStateException("Embedding response count mismatch");
                List<List<Float>> vectors = new ArrayList<>();
                for (JsonNode item : data) {
                    JsonNode vector = item.path("embedding");
                    if (!vector.isArray() || vector.isEmpty()) throw new IllegalStateException("Embedding response is missing a vector");
                    List<Float> values = new ArrayList<>();
                    for (JsonNode value : vector) values.add((float) value.asDouble());
                    if (values.size() != boundedDimensions()) throw new IllegalStateException("Embedding dimension mismatch: expected " + boundedDimensions() + ", got " + values.size());
                    vectors.add(values);
                }
                return vectors;
            }
        } catch (Exception error) {
            throw new IllegalStateException("Embedding request failed: " + (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()), error);
        }
    }

    private String embeddingsUrl(String baseUrl) {
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return normalized.endsWith("/embeddings") ? normalized : normalized + "/embeddings";
    }
    private int boundedDimensions() { return Math.max(64, Math.min(properties.getEmbeddingDimensions(), 4096)); }
    private Duration safeTimeout() { return properties.getTimeout() == null || properties.getTimeout().isNegative() || properties.getTimeout().isZero() ? Duration.ofSeconds(20) : properties.getTimeout(); }
    private String valueOr(String preferred, String fallback) { return blank(preferred) ? fallback : preferred; }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String concise(String body) { return body.length() > 400 ? body.substring(0, 400) : body; }
}
