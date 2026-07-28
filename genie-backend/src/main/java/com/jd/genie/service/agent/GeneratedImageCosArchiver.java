package com.jd.genie.service.agent;

import com.jd.genie.config.SeedDreamImageGatewayProperties;
import com.jd.genie.model.agent.StoredAgentImage;

import lombok.RequiredArgsConstructor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;

/** Downloads a temporary Model Studio result and persists it in the configured COS bucket. */
@Component
@RequiredArgsConstructor
public class GeneratedImageCosArchiver {
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private final ObjectProvider<CosAgentImageStorage> cosStorageProvider;
    private final ObjectProvider<CosSignedUrlService> signedUrlServiceProvider;
    private final SeedDreamImageGatewayProperties seedDreamProperties;
    private final OkHttpClient client = new OkHttpClient.Builder()
            .callTimeout(Duration.ofSeconds(45))
            .followRedirects(false)
            .build();

    public ArchiveResult archive(String sourceUrl) {
        CosAgentImageStorage cosStorage = cosStorageProvider.getIfAvailable();
        if (cosStorage == null) {
            return ArchiveResult.fallback(sourceUrl, "COS is not configured");
        }
        try {
            URI source = validateSource(sourceUrl);
            Request request = new Request.Builder().url(source.toString()).get().build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return ArchiveResult.fallback(sourceUrl, "provider image download HTTP " + response.code());
                }
                String contentType = normalizeImageContentType(response.header("Content-Type"));
                long declaredLength = response.body() == null ? 0 : response.body().contentLength();
                if (declaredLength <= 0 || declaredLength > MAX_IMAGE_BYTES) {
                    return ArchiveResult.fallback(sourceUrl, "provider image size is invalid");
                }
                byte[] content = response.body().bytes();
                if (content.length == 0 || content.length > MAX_IMAGE_BYTES) {
                    return ArchiveResult.fallback(sourceUrl, "provider image size exceeds 10 MB");
                }
                String extension = extensionFor(contentType);
                StoredAgentImage stored = cosStorage.store(new InMemoryAgentImageMultipartFile(
                        "generated-" + UUID.randomUUID() + "." + extension,
                        contentType,
                        content));
                CosSignedUrlService signedUrlService = signedUrlServiceProvider.getIfAvailable();
                String durableUrl = signedUrlService == null
                        ? cosStorage.imageUrl(stored)
                        : signedUrlService.createGetUrl(stored.storedFileName());
                return ArchiveResult.archived(durableUrl, stored.assetId());
            }
        } catch (Exception error) {
            return ArchiveResult.fallback(sourceUrl, concise(error.getMessage()));
        }
    }

    private URI validateSource(String sourceUrl) {
        URI source = URI.create(sourceUrl);
        String host = source.getHost() == null ? "" : source.getHost().toLowerCase(Locale.ROOT);
        if (!"https".equalsIgnoreCase(source.getScheme()) || !allowedResultHosts().stream().anyMatch(host::endsWith)) {
            throw new IllegalArgumentException("Provider result host is not on the configured COS archive allowlist");
        }
        return source;
    }

    private Set<String> allowedResultHosts() {
        Set<String> hosts = new LinkedHashSet<>(Set.of(".aliyuncs.com", ".aliyuncs.com.cn"));
        String configured = seedDreamProperties.getResultHostSuffixes();
        if (configured != null) {
            hosts.addAll(Arrays.stream(configured.split(","))
                    .map(String::trim)
                    .map(String::toLowerCase)
                    .filter(value -> value.startsWith(".") && value.length() > 2)
                    .collect(Collectors.toSet()));
        }
        return hosts;
    }

    private String normalizeImageContentType(String rawContentType) {
        String contentType = rawContentType == null ? "" : rawContentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (!("image/png".equals(contentType) || "image/jpeg".equals(contentType)
                || "image/webp".equals(contentType) || "image/gif".equals(contentType))) {
            throw new IllegalArgumentException("Provider result is not a supported image");
        }
        return contentType;
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "png";
        };
    }

    private String concise(String value) {
        if (value == null || value.isBlank()) {
            return "unknown archive error";
        }
        return value.length() > 180 ? value.substring(0, 180) : value;
    }

    public record ArchiveResult(String imageUrl, boolean archived, String detail, String assetId) {
        static ArchiveResult archived(String imageUrl, String assetId) {
            return new ArchiveResult(imageUrl, true, "Archived to COS", assetId);
        }

        static ArchiveResult fallback(String imageUrl, String detail) {
            return new ArchiveResult(imageUrl, false, detail, null);
        }
    }
}
