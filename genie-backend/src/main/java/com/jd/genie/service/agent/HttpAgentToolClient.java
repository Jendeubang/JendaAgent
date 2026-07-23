package com.jd.genie.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.config.AgentRuntimeProperties;
import lombok.RequiredArgsConstructor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Provider-neutral HTTP adapter. Provider-specific request/response mapping stays here, not in agents.
 */
@Component
@RequiredArgsConstructor
public class HttpAgentToolClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final AgentRuntimeProperties properties;
    private final ObjectMapper objectMapper;

    public AgentToolResult execute(AgentToolType type, String prompt, List<String> imageUrls) {
        return execute(type, prompt, imageUrls, null, null);
    }

    public AgentToolResult execute(AgentToolType type, String prompt, List<String> imageUrls, String imageProvider) {
        return execute(type, prompt, imageUrls, imageProvider, null);
    }

    /** Owner is resolved from the authenticated API request, never accepted from browser JSON. */
    public AgentToolResult execute(AgentToolType type, String prompt, List<String> imageUrls,
                                   String imageProvider, String ownerUserId) {
        AgentRuntimeProperties.Endpoint endpoint = endpoint(type);
        if (!endpoint.isEnabled() || blank(endpoint.getUrl())) {
            return AgentToolResult.skipped(type, type.getDisplayName() + " 未配置，已跳过");
        }
        try {
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("prompt", prompt);
            payload.put("image_urls", imageUrls);
            payload.put("task_type", type.name().toLowerCase());
            if ((type == AgentToolType.IMAGE_GENERATE || type == AgentToolType.IMAGE_EDIT)
                    && imageProvider != null && !imageProvider.isBlank()) {
                payload.put("model_provider", imageProvider.trim());
            }
            if (ownerUserId != null && !ownerUserId.isBlank()) {
                payload.put("owner_user_id", ownerUserId);
            }
            String body = objectMapper.writeValueAsString(payload);
            Request.Builder requestBuilder = new Request.Builder()
                    .url(endpoint.getUrl())
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(body, JSON));
            if (!blank(endpoint.getApiKey())) {
                requestBuilder.header("Authorization", "Bearer " + endpoint.getApiKey());
            }
            Duration timeout = safeTimeout(endpoint.getTimeout());
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .readTimeout(timeout)
                    .writeTimeout(timeout)
                    .callTimeout(timeout.plusSeconds(30))
                    .retryOnConnectionFailure(true)
                    .build();
            try (Response response = client.newCall(requestBuilder.build()).execute()) {
                String responseBody = response.body() == null ? "" : response.body().string();
                if (!response.isSuccessful()) {
                    return new AgentToolResult(type, true, false, "工具 HTTP " + response.code() + ": " + concise(responseBody), null, endpoint.getUrl());
                }
                JsonNode root = objectMapper.readTree(responseBody);
                String imageUrl = firstText(root, "image_url", "imageUrl", "url", "output.url", "data.0.url");
                String summary = firstText(root, "text", "result", "message", "output.text");
                if (summary.isBlank()) {
                    summary = type.getDisplayName() + " 调用成功";
                }
                return new AgentToolResult(type, true, true, summary, imageUrl, endpoint.getUrl());
            }
        } catch (IOException error) {
            return new AgentToolResult(type, true, false, "工具请求失败: " + error.getMessage(), null, endpoint.getUrl());
        }
    }

    private AgentRuntimeProperties.Endpoint endpoint(AgentToolType type) {
        return switch (type) {
            case OCR -> properties.getTools().getOcr();
            case IMAGE_GENERATE -> properties.getTools().getImageGenerate();
            case IMAGE_EDIT -> properties.getTools().getImageEdit();
        };
    }

    /** Lets the DAG skip explicitly unavailable nodes before spending a retry. */
    public boolean isConfigured(AgentToolType type) {
        AgentRuntimeProperties.Endpoint endpoint = endpoint(type);
        return endpoint.isEnabled() && !blank(endpoint.getUrl());
    }
    private String firstText(JsonNode root, String... paths) {
        for (String path : paths) {
            JsonNode node = root;
            for (String part : path.split("\\.")) {
                if (part.matches("\\d+")) {
                    node = node.path(Integer.parseInt(part));
                } else {
                    node = node.path(part);
                }
            }
            if (!node.isMissingNode() && !node.isNull() && !node.asText().isBlank()) {
                return node.asText();
            }
        }
        return "";
    }

    private Duration safeTimeout(Duration timeout) {
        return timeout == null || timeout.isZero() || timeout.isNegative() ? Duration.ofSeconds(120) : timeout;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String concise(String value) {
        return value.length() > 500 ? value.substring(0, 500) : value;
    }
}
