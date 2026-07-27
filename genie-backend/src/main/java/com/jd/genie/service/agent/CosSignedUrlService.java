package com.jd.genie.service.agent;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Locale;

/**
 * Generates short-lived COS V5 signed GET URLs for objects in a private bucket.
 */
@Service
@ConditionalOnProperty(name = "agent.storage.provider", havingValue = "cos")
public class CosSignedUrlService {
    private final String bucket;
    private final String region;
    private final String secretId;
    private final String secretKey;
    private final long expirySeconds;
    private final String baseUrl;

    public CosSignedUrlService(Environment environment) {
        this.bucket = required(environment.getProperty("agent.storage.cos.bucket"), "agent.storage.cos.bucket");
        this.region = required(environment.getProperty("agent.storage.cos.region"), "agent.storage.cos.region");
        this.secretId = required(environment.getProperty("agent.storage.cos.secret-id"), "agent.storage.cos.secret-id");
        this.secretKey = required(environment.getProperty("agent.storage.cos.secret-key"), "agent.storage.cos.secret-key");
        this.expirySeconds = environment.getProperty("agent.storage.cos.download-expiry-seconds", Long.class, 1800L);
        this.baseUrl = "https://" + bucket + ".cos." + region + ".myqcloud.com";
    }

    public String createGetUrl(String objectKey) {
        long now = Instant.now().getEpochSecond();
        String keyTime = now + ";" + (now + expirySeconds);
        String headerList = "host";
        String host = bucket + ".cos." + region + ".myqcloud.com";
        String canonicalHeaders = "host=" + encode(host.toLowerCase(Locale.ROOT));
        String httpString = "get\n/" + objectKey + "\n\n" + canonicalHeaders + "\n";
        String stringToSign = "sha1\n" + keyTime + "\n" + sha1Hex(httpString) + "\n";
        String signKey = hmacSha1Hex(secretKey, keyTime);
        String signature = hmacSha1Hex(signKey, stringToSign);
        return baseUrl + "/" + encodeObjectKey(objectKey)
                + "?q-sign-algorithm=sha1"
                + "&q-ak=" + encode(secretId)
                + "&q-sign-time=" + keyTime
                + "&q-key-time=" + keyTime
                + "&q-header-list=" + headerList
                + "&q-url-param-list="
                + "&q-signature=" + signature;
    }

    /**
     * Converts only objects owned by this private bucket to a short-lived signed GET URL.
     */
    public String createGetUrlIfOwned(String sourceUrl) {
        String objectKey = objectKeyIfOwned(sourceUrl);
        return objectKey == null ? sourceUrl : createGetUrl(objectKey);
    }

    /** Returns an object key only when the URL belongs to this configured COS bucket. */
    public String objectKeyIfOwned(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return null;
        }
        String prefix = baseUrl + "/";
        String unsignedUrl = sourceUrl.substring(0, sourceUrl.indexOf('?') < 0 ? sourceUrl.length() : sourceUrl.indexOf('?'));
        if (!unsignedUrl.startsWith(prefix)) {
            return null;
        }
        String encodedKey = unsignedUrl.substring(prefix.length());
        return URLDecoder.decode(encodedKey, StandardCharsets.UTF_8);
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
}
