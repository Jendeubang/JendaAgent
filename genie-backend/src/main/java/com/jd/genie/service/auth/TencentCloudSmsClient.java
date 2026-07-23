package com.jd.genie.service.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.config.AgentAuthProperties;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Direct Tencent Cloud SMS SendSms client using the TC3-HMAC-SHA256 signing scheme. */
@Component
public class TencentCloudSmsClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String HOST = "sms.tencentcloudapi.com";
    private final AgentAuthProperties properties;
    private final ObjectMapper objectMapper;
    private final OkHttpClient client = new OkHttpClient();

    public TencentCloudSmsClient(AgentAuthProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public void send(String phone, String code, long minutes) {
        requireConfiguration();
        try {
            Map<String, Object> payloadMap = new LinkedHashMap<>();
            payloadMap.put("PhoneNumberSet", List.of("+86" + phone));
            payloadMap.put("SmsSdkAppId", properties.getSmsSdkAppId());
            payloadMap.put("SignName", properties.getSmsSignName());
            payloadMap.put("TemplateId", properties.getSmsTemplateId());
            payloadMap.put("TemplateParamSet", List.of(code, String.valueOf(minutes)));
            String payload = objectMapper.writeValueAsString(payloadMap);
            long timestamp = Instant.now().getEpochSecond();
            Request request = new Request.Builder()
                    .url("https://" + HOST)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Host", HOST)
                    .header("X-TC-Action", "SendSms")
                    .header("X-TC-Version", "2021-01-11")
                    .header("X-TC-Region", properties.getSmsRegion())
                    .header("X-TC-Timestamp", String.valueOf(timestamp))
                    .header("Authorization", authorization(payload, timestamp))
                    .post(RequestBody.create(payload, JSON))
                    .build();
            try (Response response = client.newCall(request).execute()) {
                String body = response.body() == null ? "" : response.body().string();
                if (!response.isSuccessful()) throw new IllegalStateException("Tencent SMS HTTP " + response.code());
                JsonNode result = objectMapper.readTree(body).path("Response");
                String errorCode = result.path("Error").path("Code").asText();
                if (!errorCode.isBlank()) throw new IllegalStateException("Tencent SMS rejected request: " + errorCode);
                JsonNode status = result.path("SendStatusSet").path(0);
                if (!"Ok".equalsIgnoreCase(status.path("Code").asText())) throw new IllegalStateException("Tencent SMS delivery failed: " + status.path("Code").asText());
            }
        } catch (IllegalStateException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException("Tencent SMS request failed", error);
        }
    }

    private String authorization(String payload, long timestamp) {
        String date = Instant.ofEpochSecond(timestamp).toString().substring(0, 10);
        String canonicalHeaders = "content-type:application/json; charset=utf-8\nhost:" + HOST + "\n";
        String signedHeaders = "content-type;host";
        String canonicalRequest = "POST\n/\n\n" + canonicalHeaders + "\n" + signedHeaders + "\n" + sha256(payload);
        String credentialScope = date + "/sms/tc3_request";
        String stringToSign = "TC3-HMAC-SHA256\n" + timestamp + "\n" + credentialScope + "\n" + sha256(canonicalRequest);
        byte[] secretDate = hmac(("TC3" + properties.getSmsSecretKey()).getBytes(StandardCharsets.UTF_8), date);
        byte[] secretService = hmac(secretDate, "sms");
        byte[] secretSigning = hmac(secretService, "tc3_request");
        String signature = HexFormat.of().formatHex(hmac(secretSigning, stringToSign));
        return "TC3-HMAC-SHA256 Credential=" + properties.getSmsSecretId() + "/" + credentialScope + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature;
    }

    private byte[] hmac(byte[] key, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception error) {
            throw new IllegalStateException("Unable to sign Tencent SMS request", error);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("Unable to hash Tencent SMS request", error);
        }
    }

    private void requireConfiguration() {
        if (blank(properties.getSmsSecretId()) || blank(properties.getSmsSecretKey()) || blank(properties.getSmsSdkAppId()) || blank(properties.getSmsSignName()) || blank(properties.getSmsTemplateId())) {
            throw new IllegalStateException("Tencent SMS is enabled but its credentials, SDK app ID, sign name, or template ID is missing");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
