package com.jd.genie.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.config.SeedDreamImageGatewayProperties;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Volcano Ark SeedDream adapter for text-to-image and reference-image workflows. */
@Component
@RequiredArgsConstructor
public class SeedDreamImageModelProvider implements ImageModelProvider {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final SeedDreamImageGatewayProperties properties;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<CosSignedUrlService> cosSignedUrlService;

    @Override
    public String id() {
        return "seedream";
    }

    @Override
    public ImageModelResult generate(AgentToolGatewayRequest request) {
        return invoke(request, false);
    }

    @Override
    public ImageModelResult edit(AgentToolGatewayRequest request) {
        if (request.image_urls() == null || request.image_urls().isEmpty()) {
            throw new IllegalArgumentException("SeedDream image edit requires at least one image_url");
        }
        return invoke(request, true);
    }

    private ImageModelResult invoke(AgentToolGatewayRequest request, boolean editing) {
        validateConfiguration();
        try {
            Request httpRequest = new Request.Builder()
                    .url(properties.getEndpoint())
                    .header("Authorization", "Bearer " + properties.getApiKey().trim())
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(objectMapper.writeValueAsString(payload(request)), JSON))
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
                    throw new IllegalStateException("SeedDream HTTP " + response.code() + ": " + concise(responseBody));
                }
                String imageUrl = firstImage(objectMapper.readTree(responseBody));
                if (blank(imageUrl)) {
                    throw new IllegalStateException("SeedDream response is missing data[0].url; configure response_format=url");
                }
                return new ImageModelResult(imageUrl,
                        editing ? "SeedDream image edit completed successfully" : "SeedDream image generated successfully", id(), false);
            }
        } catch (IOException error) {
            throw new IllegalStateException("SeedDream request failed: " + error.getMessage(), error);
        }
    }

    private Map<String, Object> payload(AgentToolGatewayRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", properties.getModel());
        payload.put("prompt", request.prompt());
        payload.put("size", properties.getSize());
        payload.put("response_format", "url");
        payload.put("watermark", properties.isWatermark());
        List<String> imageUrls = request.image_urls() == null ? List.of() : request.image_urls().stream()
                .filter(url -> !blank(url))
                .map(this::imageUrlForModel)
                .toList();
        if (!imageUrls.isEmpty()) {
            payload.put("image", imageUrls);
        }
        return payload;
    }

    private void validateConfiguration() {
        if (!enabled()) {
            throw new IllegalStateException("SeedDream gateway is disabled; set AGENT_GATEWAY_SEEDDREAM_ENABLED=true");
        }
        if (blank(properties.getEndpoint()) || blank(properties.getModel()) || blank(properties.getApiKey())) {
            throw new IllegalStateException("SeedDream requires endpoint, model endpoint ID, and AGENT_GATEWAY_SEEDDREAM_API_KEY");
        }
    }

    private String imageUrlForModel(String imageUrl) {
        CosSignedUrlService signer = cosSignedUrlService.getIfAvailable();
        return signer == null ? imageUrl : signer.createGetUrlIfOwned(imageUrl);
    }

    private String firstImage(JsonNode root) {
        return root.path("data").path(0).path("url").asText("");
    }

    private Duration safeTimeout(Duration timeout) {
        return timeout == null || timeout.isNegative() || timeout.isZero() ? Duration.ofSeconds(600) : timeout;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
    /** Docker environment variables are authoritative for production feature flags. */
    private boolean enabled() {
        String environmentValue = System.getenv("AGENT_GATEWAY_SEEDDREAM_ENABLED");
        return properties.isEnabled() || "true".equalsIgnoreCase(environmentValue == null ? "" : environmentValue.trim());
    }

    private String concise(String value) {
        return value.length() > 500 ? value.substring(0, 500) : value;
    }
}