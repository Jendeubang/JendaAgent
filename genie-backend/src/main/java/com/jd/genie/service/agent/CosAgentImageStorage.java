package com.jd.genie.service.agent;

import com.jd.genie.model.agent.StoredAgentImage;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * COS XML API adapter using the COS V5 signing scheme.
 * Credentials stay server-side; browsers only receive the resulting object URL.
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "agent.storage.provider", havingValue = "cos")
public class CosAgentImageStorage implements AgentImageStorage {
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");
    private static final int MAX_UPLOAD_ATTEMPTS = 5;
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ofSeconds(60))
            .writeTimeout(Duration.ofSeconds(60))
            .callTimeout(Duration.ofSeconds(90))
            // COS intermittently closes TLS negotiation when HTTP/2 is attempted from Docker Desktop.
            .protocols(List.of(Protocol.HTTP_1_1))
            .retryOnConnectionFailure(true)
            .build();
    private final String bucket;
    private final String region;
    private final String secretId;
    private final String secretKey;
    private final String prefix;
    private final String baseUrl;

    public CosAgentImageStorage(Environment environment) {
        this.bucket = required(environment.getProperty("agent.storage.cos.bucket"), "agent.storage.cos.bucket");
        this.region = required(environment.getProperty("agent.storage.cos.region"), "agent.storage.cos.region");
        this.secretId = required(environment.getProperty("agent.storage.cos.secret-id"), "agent.storage.cos.secret-id");
        this.secretKey = required(environment.getProperty("agent.storage.cos.secret-key"), "agent.storage.cos.secret-key");
        this.prefix = normalizePrefix(environment.getProperty("agent.storage.cos.prefix", "agent/"));
        this.baseUrl = "https://" + this.bucket + ".cos." + this.region + ".myqcloud.com";
    }

    @Override
    public StoredAgentImage store(MultipartFile file) {
        validate(file);
        String originalFileName = StringUtils.cleanPath(file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename());
        String extension = StringUtils.getFilenameExtension(originalFileName).toLowerCase(Locale.ROOT);
        String assetId = UUID.randomUUID().toString();
        String objectKey = prefix + Instant.now().toString().substring(0, 10).replace("-", "/") + "/" + assetId + "." + extension;
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException error) {
            throw new IllegalStateException("Unable to read uploaded image", error);
        }
        putObject(objectKey, file.getContentType(), bytes);
        return new StoredAgentImage(assetId, originalFileName, objectKey, file.getContentType(), file.getSize());
    }

    public String imageUrl(StoredAgentImage image) {
        return baseUrl + "/" + encodeObjectKey(image.storedFileName());
    }

    public void deleteObject(String objectKey) {
        if (objectKey == null || objectKey.isBlank() || !objectKey.startsWith(prefix)) {
            throw new IllegalArgumentException("Refusing to delete an object outside the configured COS prefix");
        }
        URI uri = URI.create(baseUrl + "/" + encodeObjectKey(objectKey));
        String contentType = "application/octet-stream";
        Request request = new Request.Builder()
                .url(uri.toString())
                .header("Host", uri.getHost())
                .header("Content-Type", contentType)
                .header("Authorization", authorization("DELETE", "/" + objectKey, uri.getHost(), contentType))
                .delete()
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() && response.code() != 404) {
                throw new IllegalStateException("COS delete failed with HTTP " + response.code());
            }
        } catch (IOException error) {
            throw new IllegalStateException("COS delete request failed: " + error.getMessage(), error);
        }
    }

    public void deleteObjectFromUrl(String imageUrl) {
        deleteObject(resolveManagedObjectKey(imageUrl));
    }

    /** True only for assets stored in this configured COS bucket and prefix. */
    public boolean managesUrl(String imageUrl) {
        try {
            URI imageUri = URI.create(imageUrl);
            URI storageUri = URI.create(baseUrl);
            return storageUri.getHost().equalsIgnoreCase(imageUri.getHost())
                    && resolveManagedObjectKey(imageUrl).startsWith(prefix);
        } catch (Exception ignored) {
            return false;
        }
    }

    /** Reads a managed private COS object for server-side model adapters. */
    public byte[] readObjectFromUrl(String imageUrl) {
        if (!managesUrl(imageUrl)) {
            throw new IllegalArgumentException("COS URL is outside the configured bucket or prefix");
        }
        return readObject(resolveManagedObjectKey(imageUrl));
    }

    public byte[] readObject(String objectKey) {
        if (objectKey == null || objectKey.isBlank() || !objectKey.startsWith(prefix)) {
            throw new IllegalArgumentException("Refusing to read an object outside the configured COS prefix");
        }
        URI uri = URI.create(baseUrl + "/" + encodeObjectKey(objectKey));
        String contentType = "application/octet-stream";
        Request request = new Request.Builder()
                .url(uri.toString())
                .header("Host", uri.getHost())
                .header("Content-Type", contentType)
                .header("Authorization", authorization("GET", "/" + objectKey, uri.getHost(), contentType))
                .get()
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IllegalStateException("COS read failed with HTTP " + response.code());
            }
            return response.body() == null ? new byte[0] : response.body().bytes();
        } catch (IOException error) {
            throw new IllegalStateException("COS read request failed: " + error.getMessage(), error);
        }
    }

    public String resolveManagedObjectKey(String imageUrl) {
        try {
            URI uri = URI.create(imageUrl);
            String objectKey = java.net.URLDecoder.decode(uri.getRawPath().replaceFirst("^/", ""), StandardCharsets.UTF_8);
            if (objectKey.isBlank() || !objectKey.startsWith(prefix)) {
                throw new IllegalArgumentException("COS URL is outside the configured prefix");
            }
            return objectKey;
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException("Unable to resolve COS object key from asset URL", error);
        }
    }
    private void putObject(String objectKey, String contentType, byte[] bytes) {
        URI uri = URI.create(baseUrl + "/" + encodeObjectKey(objectKey));
        String host = uri.getHost();
        for (int attempt = 1; attempt <= MAX_UPLOAD_ATTEMPTS; attempt++) {
            String authorization = authorization("PUT", "/" + objectKey, host, contentType);
            Request request = new Request.Builder()
                    .url(uri.toString())
                    .header("Host", host)
                    .header("Content-Type", contentType)
                    .header("Authorization", authorization)
                    .header("Connection", "close")
                    .put(RequestBody.create(bytes, MediaType.get(contentType)))
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    return;
                }
                String body = response.body() == null ? "" : response.body().string();
                if (response.code() >= 500 && attempt < MAX_UPLOAD_ATTEMPTS) {
                    log.warn("COS upload returned HTTP {} on attempt {}/{}; retrying", response.code(), attempt, MAX_UPLOAD_ATTEMPTS);
                    pauseBeforeRetry(attempt);
                    continue;
                }
                throw new IllegalStateException("COS upload failed with HTTP " + response.code() + ": " + concise(body));
            } catch (IOException error) {
                if (attempt == MAX_UPLOAD_ATTEMPTS) {
                    throw new IllegalStateException("COS upload request failed after " + MAX_UPLOAD_ATTEMPTS + " attempts: " + error.getMessage(), error);
                }
                log.warn("COS upload attempt {}/{} failed: {}; retrying", attempt, MAX_UPLOAD_ATTEMPTS, error.getMessage());
                pauseBeforeRetry(attempt);
            }
        }
        throw new IllegalStateException("COS upload failed without a response");
    }

    private void pauseBeforeRetry(int attempt) {
        try {
            Thread.sleep(400L * attempt);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("COS upload retry was interrupted", error);
        }
    }

    private String authorization(String method, String path, String host, String contentType) {
        long now = Instant.now().getEpochSecond();
        String keyTime = now + ";" + (now + 900);
        String headerList = "content-type;host";
        String canonicalHeaders = "content-type=" + encode(contentType.trim().toLowerCase(Locale.ROOT))
                + "&host=" + encode(host.toLowerCase(Locale.ROOT));
        String httpString = method.toLowerCase(Locale.ROOT) + "\n" + path + "\n\n" + canonicalHeaders + "\n";
        String stringToSign = "sha1\n" + keyTime + "\n" + sha1Hex(httpString) + "\n";
        String signKey = hmacSha1Hex(secretKey, keyTime);
        String signature = hmacSha1Hex(signKey, stringToSign);
        return "q-sign-algorithm=sha1"
                + "&q-ak=" + encode(secretId)
                + "&q-sign-time=" + keyTime
                + "&q-key-time=" + keyTime
                + "&q-header-list=" + headerList
                + "&q-url-param-list="
                + "&q-signature=" + signature;
    }

    private void validate(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Image file must not be empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Image file must not exceed 10 MB");
        }
        String extension = StringUtils.getFilenameExtension(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        if (extension == null || !ALLOWED_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Only jpg, jpeg, png, webp and gif images are supported");
        }
        if (file.getContentType() == null || !file.getContentType().startsWith("image/")) {
            throw new IllegalArgumentException("Uploaded content must be an image");
        }
    }

    private String normalizePrefix(String value) {
        String normalized = value == null ? "agent/" : value.strip().replaceAll("^/+", "");
        return normalized.endsWith("/") ? normalized : normalized + "/";
    }

    private String encodeObjectKey(String objectKey) {
        String[] segments = objectKey.split("/");
        for (int index = 0; index < segments.length; index++) {
            segments[index] = encode(segments[index]);
        }
        return String.join("/", segments);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20").replace("*", "%2A").replace("%7E", "~");
    }

    private String sha1Hex(String value) {
        try {
            return hex(MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("Unable to calculate COS SHA-1 digest", error);
        }
    }

    private String hmacSha1Hex(String key, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return hex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("Unable to calculate COS HMAC", error);
        }
    }

    private String hex(byte[] value) {
        StringBuilder builder = new StringBuilder(value.length * 2);
        for (byte item : value) {
            builder.append(String.format("%02x", item));
        }
        return builder.toString();
    }

    private String required(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required property: " + property);
        }
        return value;
    }

    private String concise(String body) {
        return body.length() > 500 ? body.substring(0, 500) : body;
    }
}
