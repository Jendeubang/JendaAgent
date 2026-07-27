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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
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
    private final ObjectProvider<CosAgentImageStorage> cosStorageProvider;

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
                String text = OcrTextNormalizer.normalize(responseText(objectMapper.readTree(responseBody)));
                return text.isBlank() ? "未识别到文字" : text;
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
                content.add(Map.of("type", "image_url", "image_url", Map.of("url", modelAccessibleImage(imageUrl))));
            }
        }
        return content;
    }

    private String responseText(JsonNode response) {
        JsonNode content = response.path("choices").path(0).path("message").path("content");
        if (content.isTextual()) {
            return content.asText();
        }
        if (content.isArray()) {
            StringBuilder result = new StringBuilder();
            for (JsonNode item : content) {
                String value = item.path("text").asText(item.asText());
                if (!value.isBlank()) {
                    if (!result.isEmpty()) result.append('\n');
                    result.append(value);
                }
            }
            return result.toString();
        }
        return "";
    }

    private String modelAccessibleImage(String imageUrl) {
        if (imageUrl.startsWith("data:image/")) {
            return imageUrl;
        }
        CosAgentImageStorage cosStorage = cosStorageProvider.getIfAvailable();
        if (cosStorage == null || !cosStorage.managesUrl(imageUrl)) {
            return imageUrl;
        }
        byte[] bytes = cosStorage.readObjectFromUrl(imageUrl);
        if (bytes.length == 0 || bytes.length > 10 * 1024 * 1024) {
            throw new IllegalStateException("OCR image must be between 1 byte and 10 MB after COS download");
        }
        return "data:" + mediaType(imageUrl) + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    private String mediaType(String imageUrl) {
        String lower = imageUrl.toLowerCase();
        if (lower.contains(".webp")) return "image/webp";
        if (lower.contains(".gif")) return "image/gif";
        if (lower.contains(".jpg") || lower.contains(".jpeg")) return "image/jpeg";
        return "image/png";
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
