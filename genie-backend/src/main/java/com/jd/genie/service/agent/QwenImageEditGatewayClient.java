package com.jd.genie.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.config.AgentRuntimeProperties;
import com.jd.genie.config.QwenImageEditGatewayProperties;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts a normalized image-edit request into the Model Studio Qwen Image edit protocol. */
@Component
@RequiredArgsConstructor
public class QwenImageEditGatewayClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final AgentRuntimeProperties runtimeProperties;
    private final QwenImageEditGatewayProperties properties;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<CosSignedUrlService> cosSignedUrlService;

    public EditedImage edit(AgentToolGatewayRequest request) {
        validateConfiguration(request);
        try {
            String body = objectMapper.writeValueAsString(payload(request));
            Request httpRequest = new Request.Builder()
                    .url(properties.getEndpoint())
                    .header("Authorization", "Bearer " + apiKey())
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
                    throw new IllegalStateException("Qwen image edit HTTP " + response.code() + ": " + concise(responseBody));
                }
                String imageUrl = firstImage(objectMapper.readTree(responseBody));
                if (blank(imageUrl)) {
                    throw new IllegalStateException("Qwen image edit response is missing output.choices[0].message.content[].image");
                }
                return new EditedImage(imageUrl, "Qwen image edit completed successfully");
            }
        } catch (IOException error) {
            throw new IllegalStateException("Qwen image edit request failed: " + error.getMessage(), error);
        }
    }

    private void validateConfiguration(AgentToolGatewayRequest request) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("Qwen image edit gateway is disabled");
        }
        if (blank(properties.getEndpoint())) {
            throw new IllegalStateException("Qwen image edit requires AGENT_GATEWAY_IMAGE_EDIT_ENDPOINT");
        }
        if (blank(apiKey())) {
            throw new IllegalStateException("Qwen image edit requires AGENT_GATEWAY_IMAGE_EDIT_API_KEY or AGENT_RUNTIME_MODEL_API_KEY");
        }
        if (request.image_urls() == null || request.image_urls().isEmpty()) {
            throw new IllegalArgumentException("Qwen image edit requires at least one image_url");
        }
        if (request.image_urls().size() > 3) {
            throw new IllegalArgumentException("Qwen image edit supports at most three image_urls");
        }
    }

    private Map<String, Object> payload(AgentToolGatewayRequest request) {
        List<Map<String, String>> content = new ArrayList<>();
        for (String imageUrl : request.image_urls()) {
            if (!blank(imageUrl)) {
                content.add(Map.of("image", imageUrlForModel(imageUrl)));
            }
        }
        content.add(Map.of("text", request.prompt()));

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("n", 1);
        parameters.put("size", properties.getSize());
        parameters.put("prompt_extend", properties.isPromptExtend());
        parameters.put("watermark", properties.isWatermark());
        parameters.put("negative_prompt", " ");
        return Map.of(
                "model", properties.getModel(),
                "input", Map.of("messages", List.of(Map.of("role", "user", "content", content))),
                "parameters", parameters);
    }

    /**
     * Model Studio must be able to fetch private COS references. Keep external URLs unchanged.
     */
    private String imageUrlForModel(String imageUrl) {
        CosSignedUrlService signer = cosSignedUrlService.getIfAvailable();
        return signer == null ? imageUrl : signer.createGetUrlIfOwned(imageUrl);
    }
    private String apiKey() {
        return blank(properties.getApiKey()) ? runtimeProperties.getModel().getApiKey() : properties.getApiKey();
    }

    private String firstImage(JsonNode root) {
        JsonNode content = root.path("output").path("choices").path(0).path("message").path("content");
        if (!content.isArray()) {
            return "";
        }
        for (JsonNode item : content) {
            String imageUrl = item.path("image").asText();
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

    public record EditedImage(String imageUrl, String text) {
    }
}
