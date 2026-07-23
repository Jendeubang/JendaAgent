package com.jd.genie.service.agent;

import com.jd.genie.config.ImageProviderProperties;
import com.jd.genie.model.agent.AgentToolGatewayRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Selects a configured image provider without exposing provider credentials to browser clients. */
@Slf4j
@Component
public class ImageModelProviderRouter {
    private final ImageProviderProperties properties;
    private final Map<String, ImageModelProvider> providers;

    public ImageModelProviderRouter(ImageProviderProperties properties, List<ImageModelProvider> providers) {
        this.properties = properties;
        this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(
                provider -> provider.id().toLowerCase(Locale.ROOT), Function.identity()));
    }

    public ImageModelResult generate(AgentToolGatewayRequest request) {
        return select(request).generate(request);
    }

    public ImageModelResult edit(AgentToolGatewayRequest request) {
        return select(request).edit(request);
    }

    private ImageModelProvider select(AgentToolGatewayRequest request) {
        String configured = blank(request.model_provider()) ? properties.getDefaultProvider() : request.model_provider();
        String providerId = configured == null ? "qwen" : configured.trim().toLowerCase(Locale.ROOT);
        ImageModelProvider provider = providers.get(providerId);
        if (provider == null && ("gemini-nano-banana-2".equals(providerId)
                || "gemini-nano-banana-pro".equals(providerId)
                || "nano-banana-2".equals(providerId)
                || "nano-banana-pro".equals(providerId))) {
            provider = providers.get("gemini");
        }
        if (provider == null) {
            throw new IllegalArgumentException("Unsupported image model provider '" + providerId
                    + "'. Supported providers: " + String.join(", ", providers.keySet()));
        }
        log.debug("Selected image model provider {}", providerId);
        return provider;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}