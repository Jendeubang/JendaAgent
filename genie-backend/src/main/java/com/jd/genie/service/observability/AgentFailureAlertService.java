package com.jd.genie.service.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.config.AgentAlertProperties;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Sends compact 5xx notifications only when an HTTPS webhook is explicitly enabled. */
@Slf4j
@Service
public class AgentFailureAlertService {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final AgentAlertProperties properties;
    private final ObjectMapper objectMapper;

    public AgentFailureAlertService(AgentAlertProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public void reportServerFailure(String requestId, String method, String path, int status, long durationMs) {
        if (!properties.isEnabled() || !isHttpsWebhookConfigured()) return;
        CompletableFuture.runAsync(() -> send(requestId, method, path, status, durationMs));
    }

    private boolean isHttpsWebhookConfigured() {
        return properties.getWebhookUrl() != null && properties.getWebhookUrl().startsWith("https://");
    }

    private void send(String requestId, String method, String path, int status, long durationMs) {
        try {
            Duration timeout = properties.getTimeout();
            OkHttpClient client = new OkHttpClient.Builder().callTimeout(timeout).build();
            String body = objectMapper.writeValueAsString(Map.of(
                    "event", "jenda.agent.server_error",
                    "requestId", requestId,
                    "method", method,
                    "path", path,
                    "status", status,
                    "durationMs", durationMs
            ));
            Request request = new Request.Builder().url(properties.getWebhookUrl())
                    .post(RequestBody.create(body, JSON)).build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) log.warn("Failure alert webhook rejected status={} requestId={}", response.code(), requestId);
            }
        } catch (Exception error) {
            log.warn("Failure alert webhook unavailable requestId={} reason={}", requestId, error.getClass().getSimpleName());
        }
    }
}