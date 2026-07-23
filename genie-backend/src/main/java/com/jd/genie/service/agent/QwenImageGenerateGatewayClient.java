package com.jd.genie.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.config.AgentRuntimeProperties;
import com.jd.genie.config.QwenImageGenerateGatewayProperties;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts the normalized Jenda image request into a Model Studio Qwen-Image request. */
@Component
@RequiredArgsConstructor
public class QwenImageGenerateGatewayClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final AgentRuntimeProperties runtimeProperties;
    private final QwenImageGenerateGatewayProperties properties;
    private final ObjectMapper objectMapper;

    public GeneratedImage generate(AgentToolGatewayRequest request) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("Qwen image generation gateway is disabled");
        }
        if (blank(properties.getEndpoint())) {
            throw new IllegalStateException("Qwen image generation requires AGENT_GATEWAY_IMAGE_GENERATE_ENDPOINT");
        }
        String apiKey = blank(properties.getApiKey())
                ? runtimeProperties.getModel().getApiKey()
                : properties.getApiKey();
        if (blank(apiKey)) {
            throw new IllegalStateException("Qwen image generation requires AGENT_GATEWAY_IMAGE_GENERATE_API_KEY or AGENT_RUNTIME_MODEL_API_KEY");
        }

        try {
            String body = objectMapper.writeValueAsString(payload(request.prompt()));
            Request httpRequest = new Request.Builder()
                    .url(properties.getEndpoint())
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(body, JSON))
                    .build();
            Duration timeout = safeTimeout(properties.getTimeout());
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .readTimeout(timeout)
                    .writeTimeout(timeout)
                    .callTimeout(timeout.plusSeconds(30))
                    .retryOnConnectionFailure(true)
                    .build();
            try (Response response = client.newCall(httpRequest).execute()) {
                String responseBody = response.body() == null ? "" : response.body().string();
                if (!response.isSuccessful()) {
                    throw new IllegalStateException("Qwen image generation HTTP " + response.code() + ": " + concise(responseBody));
                }
                JsonNode root = objectMapper.readTree(responseBody);
                String imageUrl = firstImage(root);
                if (blank(imageUrl)) {
                    throw new IllegalStateException("Qwen image generation response is missing output.choices[0].message.content[].image");
                }
                return new GeneratedImage(imageUrl, "Qwen image generated successfully");
            }
        } catch (IOException error) {
            throw new IllegalStateException("Qwen image generation request failed: " + error.getMessage(), error);
        }
    }

    private Map<String, Object> payload(String prompt) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("size", properties.getSize());
        parameters.put("prompt_extend", properties.isPromptExtend());
        parameters.put("watermark", properties.isWatermark());

        Map<String, Object> message = Map.of(
                "role", "user",
                "content", List.of(Map.of("text", prompt)));
        return Map.of(
                "model", properties.getModel(),
                "input", Map.of("messages", List.of(message)),
                "parameters", parameters);
    }

    private String firstImage(JsonNode root) {
        JsonNode content = root.path("output").path("choices").path(0).path("message").path("content");
        if (!content.isArray()) {
            return "";
        }
        for (JsonNode part : content) {
            String imageUrl = part.path("image").asText();
            if (!blank(imageUrl)) {
                return imageUrl;
            }
        }
        return "";
    }

    private Duration safeTimeout(Duration timeout) {
        return timeout == null || timeout.isNegative() || timeout.isZero() ? Duration.ofSeconds(600) : timeout;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String concise(String value) {
        return value.length() > 500 ? value.substring(0, 500) : value;
    }

    public record GeneratedImage(String imageUrl, String text) {
    }
}
