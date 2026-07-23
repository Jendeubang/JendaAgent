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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calls OpenAI-compatible chat endpoints, including DashScope's Qwen compatible mode.
 */
@Component
@RequiredArgsConstructor
public class OpenAiCompatibleChatClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final AgentRuntimeProperties properties;
    private final ObjectMapper objectMapper;

    public ModelCompletion complete(String systemPrompt, String userPrompt, List<String> imageUrls) {
        AgentRuntimeProperties.Model model = properties.getModel();
        if (!model.isEnabled()) {
            return ModelCompletion.skipped("模型未启用：设置 AGENT_RUNTIME_MODEL_ENABLED=true 后调用真实模型");
        }
        if (blank(model.getBaseUrl()) || blank(model.getApiKey())) {
            return ModelCompletion.skipped("模型配置不完整：缺少 AGENT_RUNTIME_MODEL_BASE_URL 或 AGENT_RUNTIME_MODEL_API_KEY");
        }

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", model.getModel());
            payload.put("temperature", model.getTemperature());
            payload.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userContent(userPrompt, imageUrls))));
            String body = objectMapper.writeValueAsString(payload);
            OkHttpClient client = new OkHttpClient.Builder().callTimeout(safeTimeout(model.getTimeout())).build();
            Request request = new Request.Builder()
                    .url(chatCompletionsUrl(model.getBaseUrl()))
                    .header("Authorization", "Bearer " + model.getApiKey())
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(body, JSON))
                    .build();
            try (Response response = client.newCall(request).execute()) {
                String responseBody = response.body() == null ? "" : response.body().string();
                if (!response.isSuccessful()) {
                    throw new IllegalStateException("模型 HTTP " + response.code() + ": " + concise(responseBody));
                }
                JsonNode root = objectMapper.readTree(responseBody);
                JsonNode content = root.path("choices").path(0).path("message").path("content");
                if (content.isMissingNode() || content.asText().isBlank()) {
                    throw new IllegalStateException("模型响应缺少 choices[0].message.content");
                }
                return new ModelCompletion(true, content.asText(), "openai-compatible");
            }
        } catch (Exception error) {
            // Planning and summary are advisory. A provider outage must not prevent tool execution or history persistence.
            return ModelCompletion.skipped("Model temporarily unavailable; using rule routing: " + (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()));
        }
    }

    private List<Map<String, Object>> userContent(String prompt, List<String> imageUrls) {
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text", prompt));
        for (String imageUrl : imageUrls) {
            if (!blank(imageUrl)) {
                content.add(Map.of("type", "image_url", "image_url", Map.of("url", imageUrl)));
            }
        }
        return content;
    }

    private String chatCompletionsUrl(String baseUrl) {
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return normalized.endsWith("/chat/completions") ? normalized : normalized + "/chat/completions";
    }

    private Duration safeTimeout(Duration timeout) {
        return timeout == null || timeout.isNegative() || timeout.isZero() ? Duration.ofSeconds(90) : timeout;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String concise(String value) {
        return value.length() > 500 ? value.substring(0, 500) : value;
    }
}
