package com.jd.genie.service.agent;

import com.jd.genie.config.AgentToolWorkflowProperties;
import com.jd.genie.model.agent.StoredAgentImage;
import com.jd.genie.model.auth.AgentPrincipal;
import lombok.RequiredArgsConstructor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Archives every workflow image into the configured storage and writes ownership metadata. */
@Component
@RequiredArgsConstructor
public class AgentToolOutputArchiver {
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private final ObjectProvider<AgentImageStorage> storageProvider;
    private final ObjectProvider<CosAgentImageStorage> cosStorageProvider;
    private final ObjectProvider<CosSignedUrlService> signedUrlServiceProvider;
    private final AgentAssetMetadataStore assetStore;
    private final AgentToolWorkflowProperties properties;
    private final OkHttpClient client = new OkHttpClient.Builder().connectTimeout(Duration.ofSeconds(30))
            .readTimeout(Duration.ofMinutes(5)).callTimeout(Duration.ofMinutes(6)).followRedirects(false).build();

    public ArchivedImage archive(AgentPrincipal owner, String sessionId, String runId, String toolId,
                                 ImageToolProviderResult result, AgentToolWorkflowProperties.Endpoint config) {
        return archive(owner, sessionId, runId, toolId,
                new ImageToolProviderOutput(result.imageUrl(), result.base64Data(), result.mediaType(), "result"), config);
    }

    public ArchivedImage archive(AgentPrincipal owner, String sessionId, String runId, String toolId,
                                 ImageToolProviderOutput result, AgentToolWorkflowProperties.Endpoint config) {
        CosAgentImageStorage cos = cosStorageProvider.getIfAvailable();
        String mediaType = normalizeMime(result.mediaType());
        if (cos != null && result.imageUrl() != null && !result.imageUrl().isBlank()) {
            try {
                String objectKey = cos.resolveManagedObjectKey(result.imageUrl());
                CosSignedUrlService signer = signedUrlServiceProvider.getIfAvailable();
                String freshUrl = signer == null ? result.imageUrl() : signer.createGetUrl(objectKey);
                StoredAgentImage existing = new StoredAgentImage(UUID.randomUUID().toString(), toolId + "-existing.png", objectKey, mediaType, 0L);
                assetStore.recordGenerated(owner, sessionId, runId, existing, freshUrl, toolId);
                return new ArchivedImage(existing.assetId(), freshUrl, objectKey, mediaType, 0L);
            } catch (RuntimeException ignored) {
                // The provider URL is not a managed COS object; archive it below.
            }
        }
        byte[] bytes;
        if (result.base64Data() != null && !result.base64Data().isBlank()) {
            try { bytes = Base64.getDecoder().decode(result.base64Data()); }
            catch (IllegalArgumentException error) { throw new IllegalStateException(toolId + " returned invalid base64 image", error); }
        } else {
            bytes = download(result.imageUrl(), config);
            mediaType = mediaTypeFromUrl(result.imageUrl(), mediaType);
        }
        if (bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES) throw new IllegalStateException(toolId + " output image exceeds 10 MB");
        AgentImageStorage storage = storageProvider.getIfAvailable();
        if (storage == null) throw new IllegalStateException("Image storage is not configured");
        String extension = extension(mediaType);
        StoredAgentImage stored = storage.store(new InMemoryAgentImageMultipartFile(toolId + "-" + UUID.randomUUID() + "." + extension, mediaType, bytes));
        String imageUrl = publicUrl(stored);
        assetStore.recordGenerated(owner, sessionId, runId, stored, imageUrl, toolId);
        return new ArchivedImage(stored.assetId(), imageUrl, stored.storedFileName(), mediaType, stored.size());
    }

    private byte[] download(String imageUrl, AgentToolWorkflowProperties.Endpoint config) {
        if (imageUrl == null || imageUrl.isBlank()) throw new IllegalStateException("Provider returned no image URL");
        URI uri = URI.create(imageUrl);
        if (!"https".equalsIgnoreCase(uri.getScheme())) throw new IllegalStateException("Only HTTPS provider image URLs can be archived");
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!allowedHost(host, config)) throw new IllegalStateException("Provider image host is not in resultHostSuffixes: " + host);
        try (Response response = client.newCall(new Request.Builder().url(uri.toString()).get().build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) throw new IllegalStateException("Provider image download HTTP " + response.code());
            byte[] bytes = response.body().bytes();
            if (bytes.length > MAX_IMAGE_BYTES) throw new IllegalStateException("Provider image exceeds 10 MB");
            return bytes;
        } catch (Exception error) { throw new IllegalStateException("Provider image download failed: " + error.getMessage(), error); }
    }

    private boolean allowedHost(String host, AgentToolWorkflowProperties.Endpoint config) {
        String endpointHost = URI.create(config.getEndpoint()).getHost();
        if (endpointHost != null && endpointHost.equalsIgnoreCase(host)) return true;
        String suffixes = config.getResultHostSuffixes();
        if (suffixes == null || suffixes.isBlank()) return false;
        for (String suffix : suffixes.split(",")) if (!suffix.isBlank() && host.endsWith(suffix.trim().toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    private String publicUrl(StoredAgentImage stored) {
        CosAgentImageStorage cos = cosStorageProvider.getIfAvailable();
        if (cos != null) {
            CosSignedUrlService signer = signedUrlServiceProvider.getIfAvailable();
            return signer == null ? cos.imageUrl(stored) : signer.createGetUrl(stored.storedFileName());
        }
        String base = properties.getLocalPublicBaseUrl();
        if (base == null || base.isBlank()) return "/uploads/agent/" + stored.storedFileName();
        return base.replaceAll("/+$", "") + "/uploads/agent/" + stored.storedFileName();
    }

    private String normalizeMime(String value) {
        if (value == null || value.isBlank()) return "image/png";
        String mime = value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return Set.of("image/png", "image/jpeg", "image/webp", "image/gif").contains(mime) ? mime : "image/png";
    }

    private String mediaTypeFromUrl(String url, String fallback) {
        if (url == null) return fallback;
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.contains(".jpg") || lower.contains(".jpeg")) return "image/jpeg";
        if (lower.contains(".webp")) return "image/webp";
        if (lower.contains(".gif")) return "image/gif";
        return fallback;
    }

    private String extension(String mime) {
        return switch (mime) { case "image/jpeg" -> "jpg"; case "image/webp" -> "webp"; case "image/gif" -> "gif"; default -> "png"; };
    }

    public record ArchivedImage(String assetId, String imageUrl, String objectKey, String mediaType, long size) { }
}
