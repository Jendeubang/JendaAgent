package com.jd.genie.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.config.GeminiImageGatewayProperties;
import com.jd.genie.config.GeminiImageQuotaProperties;
import com.jd.genie.model.agent.AgentToolGatewayRequest;
import com.jd.genie.model.agent.StoredAgentImage;
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
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Gemini Interactions API adapter for Nano Banana 2 and Nano Banana Pro. */
@Component
@RequiredArgsConstructor
public class GeminiImageProvider implements ImageModelProvider {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String NB2 = "gemini-nano-banana-2";
    private static final String PRO = "gemini-nano-banana-pro";

    private final GeminiImageGatewayProperties properties;
    private final GeminiImageQuotaProperties quotaProperties;
    private final GeminiImageQuotaStore quotaStore;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<CosAgentImageStorage> cosStorageProvider;
    private final ObjectProvider<CosSignedUrlService> signedUrlServiceProvider;
    private final OkHttpClient mediaClient = new OkHttpClient.Builder().callTimeout(Duration.ofSeconds(90)).build();

    @Override public String id() { return "gemini"; }
    @Override public ImageModelResult generate(AgentToolGatewayRequest request) { return execute(request, false); }
    @Override public ImageModelResult edit(AgentToolGatewayRequest request) {
        if (request.image_urls() == null || request.image_urls().isEmpty()) throw new IllegalArgumentException("Gemini image edit requires at least one image_url");
        return execute(request, true);
    }

    private ImageModelResult execute(AgentToolGatewayRequest request, boolean edit) {
        boolean pro = PRO.equals(normalize(request.model_provider()));
        if (pro && !properties.isProEnabled()) throw new IllegalStateException("NanoBanana Pro is disabled; set AGENT_GATEWAY_GEMINI_PRO_ENABLED=true after enabling billing");
        String selected = pro ? PRO : NB2;
        GeminiImageQuotaStore.Reservation reservation = quotaStore.reserve(request.owner_user_id(), selected, pro ? quotaProperties.getNanoBananaProDailyLimit() : quotaProperties.getNanoBanana2DailyLimit());
        try {
            return invoke(request, edit, pro ? properties.getNanoBananaProModel() : properties.getNanoBanana2Model(), selected);
        } catch (GeminiHttpException error) {
            quotaStore.release(reservation);
            if (pro && properties.isProFallbackToNanoBanana2() && error.retryable()) {
                GeminiImageQuotaStore.Reservation fallbackReservation = quotaStore.reserve(request.owner_user_id(), NB2, quotaProperties.getNanoBanana2DailyLimit());
                try { return invoke(request, edit, properties.getNanoBanana2Model(), NB2 + "-fallback"); }
                catch (RuntimeException fallbackError) { quotaStore.release(fallbackReservation); throw fallbackError; }
            }
            throw error;
        } catch (RuntimeException error) {
            quotaStore.release(reservation);
            throw error;
        }
    }

    private ImageModelResult invoke(AgentToolGatewayRequest request, boolean edit, String model, String provider) {
        validateConfiguration(model);
        try {
            Request httpRequest = new Request.Builder().url(properties.getEndpoint())
                    .header("x-goog-api-key", properties.getApiKey().trim()).header("Content-Type", "application/json")
                    .post(RequestBody.create(objectMapper.writeValueAsString(payload(request, model)), JSON)).build();
            Duration timeout = safeTimeout(properties.getTimeout());
            OkHttpClient client = new OkHttpClient.Builder().connectTimeout(Duration.ofSeconds(30)).readTimeout(timeout).writeTimeout(timeout).callTimeout(timeout.plusSeconds(30)).retryOnConnectionFailure(true).build();
            try (Response response = client.newCall(httpRequest).execute()) {
                String body = response.body() == null ? "" : response.body().string();
                if (!response.isSuccessful()) throw new GeminiHttpException(response.code(), concise(body));
                GeminiImage image = image(objectMapper.readTree(body));
                String storedUrl = archive(image);
                return new ImageModelResult(storedUrl, edit ? "Gemini image edit completed successfully" : "Gemini image generated successfully", provider, true);
            }
        } catch (IOException error) {
            throw new GeminiHttpException(0, error.getMessage());
        }
    }

    private Map<String, Object> payload(AgentToolGatewayRequest request, String model) {
        List<Map<String, String>> input = new ArrayList<>();
        input.add(Map.of("type", "text", "text", request.prompt()));
        for (GeminiImage reference : references(request.image_urls())) input.add(Map.of("type", "image", "mime_type", reference.mimeType(), "data", reference.data()));
        return Map.of("model", model, "input", input, "response_format", Map.of("type", "image", "mime_type", outputMimeType(), "image_size", properties.getImageSize()));
    }
    private List<GeminiImage> references(List<String> urls) {
        if (urls == null || urls.isEmpty()) return List.of();
        if (urls.size() > properties.getMaxReferenceImages()) throw new IllegalArgumentException("Gemini reference image limit is " + properties.getMaxReferenceImages());
        List<GeminiImage> images = new ArrayList<>();
        for (String url : urls) images.add(downloadReference(url));
        return images;
    }
    private GeminiImage downloadReference(String sourceUrl) {
        CosAgentImageStorage storage = cosStorageProvider.getIfAvailable();
        CosSignedUrlService signer = signedUrlServiceProvider.getIfAvailable();
        if (storage == null || signer == null) throw new IllegalStateException("Gemini reference images require configured COS storage");
        storage.resolveManagedObjectKey(sourceUrl); // reject arbitrary URLs and SSRF targets
        String signed = signer.createGetUrlIfOwned(sourceUrl);
        try (Response response = mediaClient.newCall(new Request.Builder().url(signed).get().build()).execute()) {
            if (!response.isSuccessful()) throw new IllegalStateException("Unable to download COS reference image: HTTP " + response.code());
            String mime = normalizeMime(response.header("Content-Type"));
            byte[] bytes = response.body() == null ? new byte[0] : response.body().bytes();
            if (bytes.length == 0 || bytes.length > properties.getMaxReferenceImageBytes()) throw new IllegalArgumentException("Gemini reference image exceeds configured size limit");
            return new GeminiImage(Base64.getEncoder().encodeToString(bytes), mime);
        } catch (IOException error) { throw new IllegalStateException("Unable to download COS reference image: " + error.getMessage(), error); }
    }
    private GeminiImage image(JsonNode root) {
        JsonNode direct = root.path("output_image");
        if (direct.hasNonNull("data")) return new GeminiImage(direct.path("data").asText(), normalizeMime(direct.path("mime_type").asText(outputMimeType())));
        GeminiImage found = findImage(root);
        if (found == null || found.data().isBlank()) throw new IllegalStateException("Gemini response contains no image data");
        return found;
    }
    private GeminiImage findImage(JsonNode node) {
        if (node == null) return null;
        if (node.isObject()) {
            if ("image".equals(node.path("type").asText()) && node.hasNonNull("data")) return new GeminiImage(node.path("data").asText(), normalizeMime(node.path("mime_type").asText(outputMimeType())));
            java.util.Iterator<JsonNode> values = node.elements(); while (values.hasNext()) { GeminiImage found = findImage(values.next()); if (found != null) return found; }
        } else if (node.isArray()) for (JsonNode item : node) { GeminiImage found = findImage(item); if (found != null) return found; }
        return null;
    }
    private String archive(GeminiImage image) {
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(image.data()); } catch (IllegalArgumentException error) { throw new IllegalStateException("Gemini returned invalid image base64", error); }
        if (bytes.length == 0 || bytes.length > 10L * 1024 * 1024) throw new IllegalStateException("Gemini generated image exceeds 10 MB COS archive limit");
        CosAgentImageStorage storage = cosStorageProvider.getIfAvailable();
        if (storage == null) throw new IllegalStateException("Gemini image output requires configured COS storage");
        String extension = "image/jpeg".equals(image.mimeType()) ? "jpg" : "png";
        StoredAgentImage stored = storage.store(new InMemoryAgentImageMultipartFile("gemini-" + UUID.randomUUID() + "." + extension, image.mimeType(), bytes));
        CosSignedUrlService signer = signedUrlServiceProvider.getIfAvailable();
        return signer == null ? storage.imageUrl(stored) : signer.createGetUrl(stored.storedFileName());
    }
    private void validateConfiguration(String model) { if (!properties.isEnabled()) throw new IllegalStateException("Gemini image gateway is disabled; set AGENT_GATEWAY_GEMINI_ENABLED=true"); if (blank(properties.getApiKey()) || blank(properties.getEndpoint()) || blank(model)) throw new IllegalStateException("Gemini requires endpoint, API key, and model configuration"); }
    private String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
    private String normalizeMime(String value) { String mime = value == null ? "" : value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT); return "image/jpeg".equals(mime) || "image/webp".equals(mime) ? mime : "image/png"; }
    /** Gemini Interactions image output currently accepts JPEG only. */
    private String outputMimeType() { return "image/jpeg"; }
    private Duration safeTimeout(Duration timeout) { return timeout == null || timeout.isZero() || timeout.isNegative() ? Duration.ofSeconds(600) : timeout; }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String concise(String value) { return value == null ? "unknown error" : value.substring(0, Math.min(500, value.length())); }
    private record GeminiImage(String data, String mimeType) { }
    private static final class GeminiHttpException extends IllegalStateException { private final int status; GeminiHttpException(int status, String detail) { super("Gemini HTTP " + status + ": " + detail); this.status = status; } boolean retryable() { return status == 0 || status == 408 || status == 429 || status >= 500; } }
}