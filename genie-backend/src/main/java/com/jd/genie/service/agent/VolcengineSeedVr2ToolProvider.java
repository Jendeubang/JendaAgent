package com.jd.genie.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.config.SeedVr2ImageGatewayProperties;
import com.jd.genie.model.agent.AgentToolWorkflowRequest;
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

/** Native Volcano Ark adapter for SeedVR2 restoration and super-resolution workflows. */
@Component
@RequiredArgsConstructor
public class VolcengineSeedVr2ToolProvider implements NativeImageToolProvider {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final SeedVr2ImageGatewayProperties properties;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<CosSignedUrlService> cosSignedUrlService;

    @Override public String id() { return "seedvr2"; }
    @Override public boolean supports(String toolId) { return "image-upscale".equals(toolId) || "seedvr2".equals(toolId); }
    @Override public boolean isConfigured() { return enabled() && !blank(setting(properties.getEndpoint(), "AGENT_GATEWAY_SEEDVR2_ENDPOINT")) && !blank(setting(properties.getModel(), "AGENT_GATEWAY_SEEDVR2_MODEL")) && !blank(setting(properties.getApiKey(), "AGENT_GATEWAY_SEEDVR2_API_KEY")); }
    @Override public String endpoint() { return setting(properties.getEndpoint(), "AGENT_GATEWAY_SEEDVR2_ENDPOINT"); }
    @Override public String resultHostSuffixes() { return setting(properties.getResultHostSuffixes(), "AGENT_GATEWAY_SEEDVR2_RESULT_HOST_SUFFIXES"); }

    @Override
    public ImageToolProviderResult execute(String toolId, AgentToolWorkflowRequest request, List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) throw new IllegalArgumentException("SeedVR2 requires an input image");
        String endpoint = setting(properties.getEndpoint(), "AGENT_GATEWAY_SEEDVR2_ENDPOINT");
        String model = setting(properties.getModel(), "AGENT_GATEWAY_SEEDVR2_MODEL");
        String apiKey = setting(properties.getApiKey(), "AGENT_GATEWAY_SEEDVR2_API_KEY");
        if (!isConfigured()) throw new IllegalStateException("SeedVR2 is not configured; set AGENT_GATEWAY_SEEDVR2_ENABLED, ENDPOINT, MODEL and API_KEY");
        try {
            Request requestHttp = new Request.Builder().url(endpoint).header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(objectMapper.writeValueAsString(payload(toolId, request, imageUrls, model)), JSON)).build();
            Duration timeout = safeTimeout(properties.getTimeout());
            OkHttpClient client = new OkHttpClient.Builder().connectTimeout(Duration.ofSeconds(30)).readTimeout(timeout).writeTimeout(timeout).callTimeout(timeout.plusSeconds(30)).retryOnConnectionFailure(true).build();
            try (Response response = client.newCall(requestHttp).execute()) {
                String body = response.body() == null ? "" : response.body().string();
                if (!response.isSuccessful()) throw new IllegalStateException("SeedVR2 HTTP " + response.code() + ": " + concise(body));
                JsonNode root = objectMapper.readTree(body);
                ImageToolProviderOutput output = image(root, "enhanced");
                if (!output.hasImage()) throw new IllegalStateException("SeedVR2 response contains no image URL or base64 output");
                return new ImageToolProviderResult(output.imageUrl(), output.base64Data(), output.mediaType(), "SeedVR2 enhancement completed successfully", id());
            }
        } catch (IOException error) {
            throw new IllegalStateException("SeedVR2 request failed: " + error.getMessage(), error);
        }
    }

    private Map<String, Object> payload(String toolId, AgentToolWorkflowRequest request, List<String> input, String model) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("prompt", request.prompt().isBlank() ? "Enhance the input image, restore authentic detail, preserve original identity, composition and color." : request.prompt());
        payload.put("image", input.stream().map(this::modelUrl).toList());
        payload.put("size", request.parameters().getOrDefault("resolution", setting(properties.getSize(), "AGENT_GATEWAY_SEEDVR2_SIZE")));
        payload.put("response_format", "url");
        payload.put("watermark", booleanSetting(properties.isWatermark(), "AGENT_GATEWAY_SEEDVR2_WATERMARK"));
        payload.put("parameters", request.parameters());
        payload.put("task", toolId);
        return payload;
    }

    private ImageToolProviderOutput image(JsonNode root, String label) {
        String url = text(root, "data.0.url", "output.url", "result.url", "image_url", "url");
        String base64 = text(root, "data.0.b64_json", "output.b64_json", "result.base64", "base64", "b64_json");
        String mediaType = text(root, "data.0.mime_type", "output.mime_type", "mime_type", "media_type");
        return new ImageToolProviderOutput(url, base64, mediaType.isBlank() ? "image/png" : mediaType, label);
    }
    private String text(JsonNode root, String... paths) { for (String path : paths) { JsonNode node = root; for (String part : path.split("\\.")) node = part.matches("\\d+") ? node.path(Integer.parseInt(part)) : node.path(part); if (node.isTextual() && !node.asText().isBlank()) return node.asText(); } return ""; }
    private String modelUrl(String imageUrl) { CosSignedUrlService signer = cosSignedUrlService.getIfAvailable(); return signer == null ? imageUrl : signer.createGetUrlIfOwned(imageUrl); }
    private String setting(String configured, String environment) { String value = System.getenv(environment); return blank(value) ? configured : value.trim(); }
    private boolean booleanSetting(boolean configured, String environment) { String value = System.getenv(environment); return blank(value) ? configured : Boolean.parseBoolean(value.trim()); }
    private boolean enabled() { String value = System.getenv("AGENT_GATEWAY_SEEDVR2_ENABLED"); return properties.isEnabled() || "true".equalsIgnoreCase(value == null ? "" : value.trim()); }
    private Duration safeTimeout(Duration value) { return value == null || value.isNegative() || value.isZero() ? Duration.ofSeconds(600) : value; }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String concise(String value) { return value == null ? "" : value.substring(0, Math.min(500, value.length())); }
}