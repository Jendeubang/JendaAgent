package com.jd.genie.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.model.agent.AgentImageUploadResponse;
import com.jd.genie.model.agent.DirectUploadTicket;
import com.jd.genie.model.agent.DirectUploadTicketRequest;
import com.jd.genie.model.auth.AgentPrincipal;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Issues STS credentials that can upload exactly one object, then verifies that object server-side.
 */
@Service
@ConditionalOnProperty(name = "agent.storage.provider", havingValue = "cos")
public class StsDirectUploadTicketService {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");
    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper objectMapper;
    private final CosSignedUrlService signedUrlService;
    private final AgentAssetMetadataStore assetStore;
    private final String bucket;
    private final String region;
    private final String appId;
    private final String secretId;
    private final String secretKey;
    private final String prefix;
    private final long durationSeconds;
    private final Map<String, TicketRecord> tickets = new ConcurrentHashMap<>();

    public StsDirectUploadTicketService(Environment environment, ObjectMapper objectMapper, CosSignedUrlService signedUrlService, AgentAssetMetadataStore assetStore) {
        this.objectMapper = objectMapper;
        this.signedUrlService = signedUrlService;
        this.assetStore = assetStore;
        this.bucket = required(environment.getProperty("agent.storage.cos.bucket"), "agent.storage.cos.bucket");
        this.region = required(environment.getProperty("agent.storage.cos.region"), "agent.storage.cos.region");
        this.appId = environment.getProperty("agent.storage.cos.app-id", bucket.substring(bucket.lastIndexOf('-') + 1));
        this.secretId = required(environment.getProperty("agent.storage.cos.secret-id"), "agent.storage.cos.secret-id");
        this.secretKey = required(environment.getProperty("agent.storage.cos.secret-key"), "agent.storage.cos.secret-key");
        this.prefix = normalizePrefix(environment.getProperty("agent.storage.cos.prefix", "agent/"));
        this.durationSeconds = environment.getProperty("agent.storage.cos.sts-duration-seconds", Long.class, 900L);
    }

    public DirectUploadTicket issue(DirectUploadTicketRequest request) {
        validate(request);
        String extension = StringUtils.getFilenameExtension(request.getFileName()).toLowerCase(Locale.ROOT);
        String uploadId = UUID.randomUUID().toString();
        String sessionSegment = request.getSessionId().replaceAll("[^A-Za-z0-9_-]", "_");
        String objectKey = prefix + "direct/" + sessionSegment + "/" + uploadId + "." + extension;
        long expiresAt = Instant.now().getEpochSecond() + durationSeconds;
        DirectUploadTicket.TemporaryCredentials credentials = requestFederationToken(objectKey);
        tickets.put(uploadId, new TicketRecord(AgentRequestUserContext.current().userId(), request.getSessionId(), request.getFileName(), request.getMediaType(), request.getSize(), objectKey, expiresAt));
        String uploadUrl = "https://" + bucket + ".cos." + region + ".myqcloud.com/" + encodeObjectKey(objectKey);
        return new DirectUploadTicket(uploadId, uploadUrl, objectKey, bucket, region, expiresAt, credentials);
    }

    public AgentImageUploadResponse complete(String uploadId) {
        TicketRecord record = tickets.get(uploadId);
        if (record == null || record.expiresAt < Instant.now().getEpochSecond()) {
            throw new IllegalArgumentException("Direct upload ticket is missing or expired");
        }
        if (!record.ownerUserId.equals(AgentRequestUserContext.current().userId())) { throw new AgentSessionAccessDeniedException(); }
        long uploadedSize = headObject(record.objectKey);
        if (uploadedSize < 1 || uploadedSize > 10 * 1024 * 1024 || uploadedSize != record.size) {
            throw new IllegalStateException("Direct upload verification failed: unexpected object size");
        }
        AgentImageUploadResponse response = new AgentImageUploadResponse(uploadId, record.fileName, signedUrlService.createGetUrl(record.objectKey),
                record.mediaType, uploadedSize, true, "cos-sts");
        assetStore.recordUpload(new AgentPrincipal(record.ownerUserId, "stored-owner"), record.sessionId, response, record.objectKey);
        return response;
    }

    private DirectUploadTicket.TemporaryCredentials requestFederationToken(String objectKey) {
        try {
            String resource = "qcs::cos:" + region + ":uid/" + appId + ":" + bucket + "/" + objectKey;
            String policy = objectMapper.writeValueAsString(Map.of(
                    "version", "2.0",
                    "statement", List.of(Map.of(
                            "effect", "allow",
                            "action", List.of("name/cos:PutObject"),
                            "resource", List.of(resource)))));
            String payload = objectMapper.writeValueAsString(Map.of(
                    "Name", "jenda-agent-upload",
                    "Policy", policy,
                    "DurationSeconds", durationSeconds));
            long timestamp = Instant.now().getEpochSecond();
            String authorization = tc3Authorization("GetFederationToken", payload, timestamp);
            Request request = new Request.Builder()
                    .url("https://sts.tencentcloudapi.com")
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Host", "sts.tencentcloudapi.com")
                    .header("X-TC-Action", "GetFederationToken")
                    .header("X-TC-Version", "2018-08-13")
                    .header("X-TC-Timestamp", String.valueOf(timestamp))
                    .header("Authorization", authorization)
                    .post(RequestBody.create(payload, JSON))
                    .build();
            try (Response response = client.newCall(request).execute()) {
                String body = response.body() == null ? "" : response.body().string();
                JsonNode root = objectMapper.readTree(body);
                JsonNode result = root.path("Response");
                if (!response.isSuccessful() || result.has("Error")) {
                    throw new IllegalStateException("STS request failed: " + concise(body));
                }
                JsonNode credentials = result.path("Credentials");
                String tmpSecretId = credentials.path("TmpSecretId").asText();
                String tmpSecretKey = credentials.path("TmpSecretKey").asText();
                String token = credentials.path("Token").asText();
                if (tmpSecretId.isBlank() || tmpSecretKey.isBlank() || token.isBlank()) {
                    throw new IllegalStateException("STS response is missing temporary credentials");
                }
                return new DirectUploadTicket.TemporaryCredentials(tmpSecretId, tmpSecretKey, token);
            }
        } catch (IOException error) {
            throw new IllegalStateException("STS request failed: " + error.getMessage(), error);
        }
    }

    private long headObject(String objectKey) {
        String host = bucket + ".cos." + region + ".myqcloud.com";
        Request request = new Request.Builder()
                .url("https://" + host + "/" + encodeObjectKey(objectKey))
                .header("Host", host)
                .header("Authorization", cosAuthorization("HEAD", "/" + objectKey, host))
                .head()
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IllegalStateException("Uploaded COS object was not found: HTTP " + response.code());
            }
            String contentLength = response.header("Content-Length");
            return contentLength == null ? -1 : Long.parseLong(contentLength);
        } catch (IOException error) {
            throw new IllegalStateException("Unable to verify direct upload: " + error.getMessage(), error);
        }
    }

    private String tc3Authorization(String action, String payload, long timestamp) {
        String date = Instant.ofEpochSecond(timestamp).toString().substring(0, 10);
        String canonicalHeaders = "content-type:application/json; charset=utf-8\nhost:sts.tencentcloudapi.com\n";
        String signedHeaders = "content-type;host";
        String canonicalRequest = "POST\n/\n\n" + canonicalHeaders + "\n" + signedHeaders + "\n" + sha256Hex(payload);
        String credentialScope = date + "/sts/tc3_request";
        String stringToSign = "TC3-HMAC-SHA256\n" + timestamp + "\n" + credentialScope + "\n" + sha256Hex(canonicalRequest);
        byte[] secretDate = hmacSha256(("TC3" + secretKey).getBytes(StandardCharsets.UTF_8), date);
        byte[] secretService = hmacSha256(secretDate, "sts");
        byte[] secretSigning = hmacSha256(secretService, "tc3_request");
        String signature = hex(hmacSha256(secretSigning, stringToSign));
        return "TC3-HMAC-SHA256 Credential=" + secretId + "/" + credentialScope
                + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature;
    }

    private String cosAuthorization(String method, String path, String host) {
        long now = Instant.now().getEpochSecond();
        String keyTime = now + ";" + (now + 900);
        String canonicalHeaders = "host=" + encode(host.toLowerCase(Locale.ROOT));
        String httpString = method.toLowerCase(Locale.ROOT) + "\n" + path + "\n\n" + canonicalHeaders + "\n";
        String stringToSign = "sha1\n" + keyTime + "\n" + sha1Hex(httpString) + "\n";
        String signKey = hmacSha1Hex(secretKey, keyTime);
        String signature = hmacSha1Hex(signKey, stringToSign);
        return "q-sign-algorithm=sha1&q-ak=" + encode(secretId)
                + "&q-sign-time=" + keyTime + "&q-key-time=" + keyTime
                + "&q-header-list=host&q-url-param-list=&q-signature=" + signature;
    }

    private void validate(DirectUploadTicketRequest request) {
        String extension = StringUtils.getFilenameExtension(request.getFileName());
        if (extension == null || !ALLOWED_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Only jpg, jpeg, png, webp and gif images are supported");
        }
        if (!request.getMediaType().startsWith("image/")) {
            throw new IllegalArgumentException("Only image uploads are supported");
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

    private String sha256Hex(String value) {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("Unable to calculate STS SHA-256 digest", error);
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

    private byte[] hmacSha256(byte[] key, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception error) {
            throw new IllegalStateException("Unable to calculate STS HMAC", error);
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

    private record TicketRecord(String ownerUserId, String sessionId, String fileName, String mediaType, long size, String objectKey, long expiresAt) {
    }
}
