package com.jd.genie.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.config.SemanticLayerImageGatewayProperties;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Native semantic-layer adapter. The remote endpoint returns subject/background layer images, which are archived independently. */
@Component
@RequiredArgsConstructor
public class SemanticLayerImageToolProvider implements NativeImageToolProvider {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final SemanticLayerImageGatewayProperties properties;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<CosSignedUrlService> cosSignedUrlService;

    @Override public String id() { return "semantic-layer"; }
    @Override public boolean supports(String toolId) { return "image-layered".equals(toolId); }
    @Override public boolean isConfigured() { return enabled() && !blank(setting(properties.getEndpoint(), "AGENT_GATEWAY_SEMANTIC_LAYER_ENDPOINT")) && !blank(setting(properties.getModel(), "AGENT_GATEWAY_SEMANTIC_LAYER_MODEL")) && !blank(setting(properties.getApiKey(), "AGENT_GATEWAY_SEMANTIC_LAYER_API_KEY")); }
    @Override public String endpoint() { return setting(properties.getEndpoint(), "AGENT_GATEWAY_SEMANTIC_LAYER_ENDPOINT"); }
    @Override public String resultHostSuffixes() { return setting(properties.getResultHostSuffixes(), "AGENT_GATEWAY_SEMANTIC_LAYER_RESULT_HOST_SUFFIXES"); }

    @Override
    public ImageToolProviderResult execute(String toolId, AgentToolWorkflowRequest request, List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) throw new IllegalArgumentException("Semantic layering requires an input image");
        String endpoint = setting(properties.getEndpoint(), "AGENT_GATEWAY_SEMANTIC_LAYER_ENDPOINT");
        String model = setting(properties.getModel(), "AGENT_GATEWAY_SEMANTIC_LAYER_MODEL");
        String apiKey = setting(properties.getApiKey(), "AGENT_GATEWAY_SEMANTIC_LAYER_API_KEY");
        if (!isConfigured()) throw new IllegalStateException("Semantic layer provider is not configured; set AGENT_GATEWAY_SEMANTIC_LAYER_ENABLED, ENDPOINT, MODEL and API_KEY");
        try {
            Request httpRequest = new Request.Builder().url(endpoint).header("Authorization", "Bearer " + apiKey).header("Content-Type", "application/json")
                    .post(RequestBody.create(objectMapper.writeValueAsString(payload(request, imageUrls, model)), JSON)).build();
            Duration timeout = safeTimeout(properties.getTimeout());
            OkHttpClient client = new OkHttpClient.Builder().connectTimeout(Duration.ofSeconds(30)).readTimeout(timeout).writeTimeout(timeout).callTimeout(timeout.plusSeconds(30)).retryOnConnectionFailure(true).build();
            try (Response response = client.newCall(httpRequest).execute()) {
                String body = response.body() == null ? "" : response.body().string();
                if (!response.isSuccessful()) throw new IllegalStateException("Semantic layer HTTP " + response.code() + ": " + concise(body));
                List<ImageToolProviderOutput> outputs = layers(objectMapper.readTree(body));
                if (outputs.isEmpty()) throw new IllegalStateException("Semantic layer response contains no layer image URL or base64 output");
                ImageToolProviderOutput primary = outputs.get(0);
                return new ImageToolProviderResult(primary.imageUrl(), primary.base64Data(), primary.mediaType(), "Semantic layers created successfully", id(), outputs.subList(1, outputs.size()));
            }
        } catch (IOException error) {
            throw new IllegalStateException("Semantic layer request failed: " + error.getMessage(), error);
        }
    }

    private Map<String, Object> payload(AgentToolWorkflowRequest request, List<String> input, String model) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("image_url", modelUrl(input.get(0)));
        payload.put("image_urls", input.stream().map(this::modelUrl).toList());
        payload.put("prompt", request.prompt().isBlank() ? "Separate the main subject and background into clean semantic layers with transparent PNG subject output." : request.prompt());
        payload.put("num_layers", request.parameters().getOrDefault("numLayers", 2));
        payload.put("output_format", setting(properties.getOutputFormat(), "AGENT_GATEWAY_SEMANTIC_LAYER_OUTPUT_FORMAT"));
        payload.put("response_format", "url");
        payload.put("parameters", request.parameters());
        return payload;
    }

    private List<ImageToolProviderOutput> layers(JsonNode root) {
        List<ImageToolProviderOutput> result = new ArrayList<>();
        collect(root.path("layers"), result);
        collect(root.path("data").path("layers"), result);
        collect(root.path("output").path("layers"), result);
        if (result.isEmpty()) collect(root.path("data"), result);
        if (result.isEmpty()) collect(root.path("output"), result);
        if (result.isEmpty()) collect(root, result);
        return result.stream().filter(ImageToolProviderOutput::hasImage).distinct().toList();
    }
    private void collect(JsonNode node, List<ImageToolProviderOutput> target) {
        if (node == null || node.isMissingNode() || node.isNull()) return;
        if (node.isArray()) { for (JsonNode item : node) collect(item, target); return; }
        if (!node.isObject()) return;
        String url = text(node, "image_url", "imageUrl", "url", "output.url");
        String base64 = text(node, "base64", "image_base64", "b64_json", "output.b64_json");
        if (!blank(url) || !blank(base64)) target.add(new ImageToolProviderOutput(url, base64, text(node, "mime_type", "media_type", "content_type"), text(node, "name", "label", "type")));
    }
    private String text(JsonNode root, String... paths) { for (String path : paths) { JsonNode node = root; for (String part : path.split("\\.")) node = part.matches("\\d+") ? node.path(Integer.parseInt(part)) : node.path(part); if (node.isTextual() && !node.asText().isBlank()) return node.asText(); } return ""; }
    private String modelUrl(String imageUrl) { CosSignedUrlService signer = cosSignedUrlService.getIfAvailable(); return signer == null ? imageUrl : signer.createGetUrlIfOwned(imageUrl); }
    private String setting(String configured, String environment) { String value = System.getenv(environment); return blank(value) ? configured : value.trim(); }
    private boolean enabled() { String value = System.getenv("AGENT_GATEWAY_SEMANTIC_LAYER_ENABLED"); return properties.isEnabled() || "true".equalsIgnoreCase(value == null ? "" : value.trim()); }
    private Duration safeTimeout(Duration value) { return value == null || value.isNegative() || value.isZero() ? Duration.ofSeconds(600) : value; }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String concise(String value) { return value == null ? "" : value.substring(0, Math.min(500, value.length())); }
}