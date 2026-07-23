package com.jd.genie.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.config.AgentRuntimeProperties;
import com.jd.genie.config.QwenOcrGatewayProperties;
import com.jd.genie.model.agent.AgentToolGatewayRequest;
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

/** Converts the normalized Jenda tool request into a Qwen OCR chat-completions request. */
@Component
@RequiredArgsConstructor
public class QwenOcrGatewayClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final AgentRuntimeProperties runtimeProperties;
    private final QwenOcrGatewayProperties properties;
    private final ObjectMapper objectMapper;

    public String recognize(AgentToolGatewayRequest request) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("Qwen OCR gateway is disabled");
        }
        AgentRuntimeProperties.Model model = runtimeProperties.getModel();
        if (blank(model.getBaseUrl()) || blank(model.getApiKey())) {
            throw new IllegalStateException("Qwen OCR requires AGENT_RUNTIME_MODEL_BASE_URL and AGENT_RUNTIME_MODEL_API_KEY");
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", properties.getModel());
            payload.put("messages", List.of(Map.of("role", "user", "content", content(request))));
            String body = objectMapper.writeValueAsString(payload);
            Request httpRequest = new Request.Builder()
                    .url(chatCompletionsUrl(model.getBaseUrl()))
                    .header("Authorization", "Bearer " + model.getApiKey())
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(body, JSON))
                    .build();
            OkHttpClient client = new OkHttpClient.Builder().callTimeout(safeTimeout(properties.getTimeout())).build();
            try (Response response = client.newCall(httpRequest).execute()) {
                String responseBody = response.body() == null ? "" : response.body().string();
                if (!response.isSuccessful()) {
                    throw new IllegalStateException("Qwen OCR HTTP " + response.code() + ": " + concise(responseBody));
                }
                JsonNode content = objectMapper.readTree(responseBody).path("choices").path(0).path("message").path("content");
                if (content.isMissingNode() || content.asText().isBlank()) {
                    throw new IllegalStateException("Qwen OCR response is missing choices[0].message.content");
                }
                return content.asText();
            }
        } catch (IOException error) {
            throw new IllegalStateException("Qwen OCR request failed: " + error.getMessage(), error);
        }
    }

    private List<Map<String, Object>> content(AgentToolGatewayRequest request) {
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text", "Extract all visible text accurately. Return Chinese text and preserve line breaks. User task: " + request.prompt()));
        for (String imageUrl : request.image_urls()) {
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
        return timeout == null || timeout.isNegative() || timeout.isZero() ? Duration.ofSeconds(180) : timeout;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String concise(String value) {
        return value.length() > 500 ? value.substring(0, 500) : value;
    }
}
