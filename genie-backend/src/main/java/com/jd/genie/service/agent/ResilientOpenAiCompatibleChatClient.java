package com.jd.genie.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.config.AgentRuntimeProperties;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Prevents a slow external model from terminating an otherwise valid SSE run.
 */
public class ResilientOpenAiCompatibleChatClient extends OpenAiCompatibleChatClient {
    private static final long COOLDOWN_MILLIS = Duration.ofMinutes(2).toMillis();
    private final AtomicLong unavailableUntil = new AtomicLong();

    public ResilientOpenAiCompatibleChatClient(AgentRuntimeProperties properties, ObjectMapper objectMapper) {
        super(properties, objectMapper);
    }

    @Override
    public ModelCompletion complete(String systemPrompt, String userPrompt, List<String> imageUrls) {
        if (System.currentTimeMillis() < unavailableUntil.get()) {
            return ModelCompletion.skipped("模型服务暂时不可用，已跳过本次调用并继续执行工具链");
        }
        try {
            return super.complete(systemPrompt, userPrompt, imageUrls);
        } catch (IllegalStateException error) {
            if (!containsTimeout(error)) {
                throw error;
            }
            unavailableUntil.set(System.currentTimeMillis() + COOLDOWN_MILLIS);
            return ModelCompletion.skipped("模型请求超时，已降级为规则计划并继续执行工具链");
        }
    }

    private boolean containsTimeout(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
