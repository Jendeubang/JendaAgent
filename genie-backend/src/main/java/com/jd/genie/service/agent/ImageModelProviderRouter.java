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
        return invoke(request, false);
    }

    public ImageModelResult edit(AgentToolGatewayRequest request) {
        return invoke(request, true);
    }

    /**
     * Preserve an explicit provider choice for normal calls, but fail over to the configured
     * default provider when the selected remote gateway has a transient transport failure.
     */
    private ImageModelResult invoke(AgentToolGatewayRequest request, boolean editing) {
        ImageModelProvider primary = select(request);
        try {
            return editing ? primary.edit(request) : primary.generate(request);
        } catch (RuntimeException primaryFailure) {
            ImageModelProvider fallback = fallbackFor(request, primary);
            if (fallback == null || !isRetryableGatewayFailure(primaryFailure)) {
                throw primaryFailure;
            }
            try {
                ImageModelResult recovered = editing ? fallback.edit(request) : fallback.generate(request);
                return new ImageModelResult(recovered.imageUrl(),
                        "Fallback from " + primary.id() + " after transient gateway failure; " + recovered.text(),
                        recovered.provider(), recovered.archivedToCos());
            } catch (RuntimeException fallbackFailure) {
                primaryFailure.addSuppressed(fallbackFailure);
                throw primaryFailure;
            }
        }
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

    private ImageModelProvider fallbackFor(AgentToolGatewayRequest request, ImageModelProvider primary) {
        if (blank(request.model_provider())) {
            return null;
        }
        String configured = properties.getDefaultProvider();
        if (blank(configured)) {
            return null;
        }
        ImageModelProvider fallback = providers.get(configured.trim().toLowerCase(Locale.ROOT));
        return fallback == null || fallback.id().equalsIgnoreCase(primary.id()) ? null : fallback;
    }

    private boolean isRetryableGatewayFailure(RuntimeException error) {
        String message = error.getMessage() == null ? "" : error.getMessage().toLowerCase(Locale.ROOT);
        return message.contains("timeout")
                || message.contains("handshake")
                || message.contains("connection")
                || message.contains("remote host")
                || message.contains("ssl")
                || message.contains("eof")
                || message.matches(".*http 5\\d{2}.*");
    }
    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}