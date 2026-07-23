package com.jd.genie.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.config.AgentToolWorkflowProperties;
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

/** Generic JSON adapter for image processing APIs such as upscalers and SeedVR2. */
@Component
@RequiredArgsConstructor
public class ConfiguredImageToolProvider {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final ObjectMapper objectMapper;

    public ImageToolProviderResult execute(String toolId, AgentToolWorkflowProperties.Endpoint config,
                                           String prompt, List<String> imageUrls, Map<String, Object> parameters) {
        validate(config, toolId);
        Map<String, Object> payload = new LinkedHashMap<>();
        if (config.getModel() != null && !config.getModel().isBlank()) payload.put("model", config.getModel());
        payload.put("prompt", prompt == null ? "" : prompt);
        payload.put("image_url", imageUrls.isEmpty() ? null : imageUrls.get(0));
        payload.put("image_urls", imageUrls);
        payload.put("parameters", parameters == null ? Map.of() : parameters);
        payload.put("response_format", "url");
        try {
            Duration timeout = safeTimeout(config.getTimeout());
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(Duration.ofSeconds(30)).readTimeout(timeout).writeTimeout(timeout)
                    .callTimeout(timeout.plusSeconds(30)).retryOnConnectionFailure(true).build();
            Request.Builder builder = new Request.Builder().url(config.getEndpoint())
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(objectMapper.writeValueAsString(payload), JSON));
            if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
                builder.header("Authorization", "Bearer " + config.getApiKey().trim());
            }
            try (Response response = client.newCall(builder.build()).execute()) {
                String body = response.body() == null ? "" : response.body().string();
                if (!response.isSuccessful()) throw new IllegalStateException(toolId + " HTTP " + response.code() + ": " + concise(body));
                JsonNode root = objectMapper.readTree(body);
                ImageToolProviderResult result = parse(root, config.getEndpoint());
                if (!result.hasImage()) throw new IllegalStateException(toolId + " response contains no image URL or base64 data");
                return result;
            }
        } catch (IOException error) {
            throw new IllegalStateException(toolId + " request failed: " + error.getMessage(), error);
        }
    }

    private ImageToolProviderResult parse(JsonNode root, String provider) {
        String dataUrl = findText(root, "data_url", "image_data_url", "output_image");
        if (dataUrl.startsWith("data:image/")) {
            int comma = dataUrl.indexOf(',');
            String mime = dataUrl.substring(5, dataUrl.indexOf(';', 5));
            return new ImageToolProviderResult(null, comma < 0 ? dataUrl : dataUrl.substring(comma + 1), mime, findText(root, "text", "message", "result"), provider);
        }
        String base64 = findText(root, "base64", "image_base64", "b64_json");
        String url = firstUrl(root);
        String mime = findText(root, "mime_type", "media_type", "content_type");
        return new ImageToolProviderResult(url, base64, mime.isBlank() ? "image/png" : mime, findText(root, "text", "message", "result"), provider);
    }

    private String firstUrl(JsonNode root) {
        String value = findText(root, "image_url", "imageUrl", "url", "output.url", "data.0.url", "output.0", "data.0");
        return value.startsWith("http://") || value.startsWith("https://") ? value : "";
    }

    private String findText(JsonNode root, String... paths) {
        for (String path : paths) {
            JsonNode node = root;
            for (String part : path.split("\\.")) node = part.matches("\\d+") ? node.path(Integer.parseInt(part)) : node.path(part);
            if (node.isTextual() && !node.asText().isBlank()) return node.asText();
        }
        return "";
    }

    private void validate(AgentToolWorkflowProperties.Endpoint config, String toolId) {
        if (!config.isEnabled() || config.getEndpoint() == null || config.getEndpoint().isBlank()) {
            throw new IllegalStateException(toolId + " is not configured; set agent.tools." + toolId.replace('-', '.') + ".endpoint and enabled=true");
        }
    }

    private Duration safeTimeout(Duration value) {
        return value == null || value.isZero() || value.isNegative() ? Duration.ofMinutes(10) : value;
    }

    private String concise(String value) {
        return value == null ? "" : value.length() > 500 ? value.substring(0, 500) : value;
    }
}
