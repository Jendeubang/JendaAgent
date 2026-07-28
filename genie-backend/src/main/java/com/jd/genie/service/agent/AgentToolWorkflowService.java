package com.jd.genie.service.agent;

import com.jd.genie.config.AgentToolWorkflowProperties;
import com.jd.genie.model.agent.AgentAssetMetadata;
import com.jd.genie.model.agent.AgentEvent;
import com.jd.genie.model.agent.AgentEventStatus;
import com.jd.genie.model.agent.AgentEventType;
import com.jd.genie.model.agent.AgentRunMode;
import com.jd.genie.model.agent.AgentRunRequest;
import com.jd.genie.model.agent.AgentToolGatewayRequest;
import com.jd.genie.model.agent.AgentToolWorkflowRequest;
import com.jd.genie.model.auth.AgentPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.ObjectProvider;
import java.util.concurrent.atomic.AtomicLong;

/** Runs every public image tool through the same SSE, history and COS lifecycle. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentToolWorkflowService {
    private static final String SCHEMA_VERSION = "v1";
    private static final Set<String> MODEL_WORKFLOWS = Set.of("product-refinement", "product-detail-image", "ecommerce-promotion-poster", "character-setting-sheet", "emoji-sticker");

    private final AgentHistoryStore historyStore;
    private final AgentAssetMetadataStore assetStore;
    private final AgentToolWorkflowProperties properties;
    private final ConfiguredImageToolProvider configuredProvider;
    private final AgentToolOutputArchiver outputArchiver;
    private final ImageModelProviderRouter imageProviderRouter;
    private final List<NativeImageToolProvider> nativeProviders;
    private final ObjectProvider<CosAgentImageStorage> cosStorageProvider;
    private final ObjectProvider<CosSignedUrlService> signedUrlServiceProvider;

    public SseEmitter start(String sessionId, String toolId, AgentToolWorkflowRequest request, AgentPrincipal principal) {
        validateToolId(toolId);
        String runId = UUID.randomUUID().toString();
        List<String> inputUrls = resolveInputs(principal.userId(), request.inputAssetIds());
        AgentRunRequest runRequest = new AgentRunRequest();
        runRequest.setPrompt(promptFor(toolId, request.prompt(), request.parameters()));
        runRequest.setMode(AgentRunMode.PLAN_SOLVE);
        runRequest.setImageUrls(inputUrls);
        historyStore.startRun(sessionId, runId, runRequest);
        SseEmitter emitter = new SseEmitter(Duration.ofMinutes(20).toMillis());
        CompletableFuture.runAsync(() -> AgentRequestUserContext.runAs(principal, () -> {
            execute(emitter, sessionId, runId, toolId, request, inputUrls, principal);
            return null;
        }));
        return emitter;
    }

    private void execute(SseEmitter emitter, String sessionId, String runId, String toolId,
                         AgentToolWorkflowRequest request, List<String> inputUrls, AgentPrincipal principal) {
        AtomicLong sequence = new AtomicLong();
        try {
            emit(emitter, event(sessionId, runId, sequence, AgentEventType.RUN_STARTED, AgentEventStatus.RUNNING,
                    "ToolWorkflow", map("toolId", toolId, "message", "tool workflow started")));
            emit(emitter, event(sessionId, runId, sequence, AgentEventType.TASK, AgentEventStatus.RUNNING,
                    "ExecutorAgent", map("tool", toolId, "title", displayName(toolId), "content", "task submitted to the image workflow")));
            emit(emitter, event(sessionId, runId, sequence, AgentEventType.TOOL_CALL, AgentEventStatus.RUNNING,
                    "ToolRouter", map("tool", toolId, "title", displayName(toolId) + " calling", "content", "started through the normalized tool protocol")));

            ImageToolProviderResult providerResult = executeProvider(toolId, request, inputUrls, principal.userId());
            AgentToolWorkflowProperties.Endpoint config = properties.forTool(toolId);
            AgentToolWorkflowProperties.Endpoint archiveConfig = archiveConfig(toolId, config, providerResult.provider());
            List<AgentToolOutputArchiver.ArchivedImage> archivedImages = new ArrayList<>();
            for (ImageToolProviderOutput output : providerResult.allOutputs()) {
                archivedImages.add(outputArchiver.archive(principal, sessionId, runId, toolId, output, archiveConfig));
            }
            if (archivedImages.isEmpty()) throw new IllegalStateException(toolId + " returned no archivable image output");
            AgentToolOutputArchiver.ArchivedImage archived = archivedImages.get(0);

            emit(emitter, event(sessionId, runId, sequence, AgentEventType.TOOL_RESULT, AgentEventStatus.COMPLETE,
                    "ToolRouter", map("tool", toolId, "title", displayName(toolId) + " completed", "content", text(providerResult, toolId), "provider", providerResult.provider())));
            for (int index = 0; index < archivedImages.size(); index++) {
                AgentToolOutputArchiver.ArchivedImage image = archivedImages.get(index);
                String title = index == 0 ? displayName(toolId) + " output" : displayName(toolId) + " layer " + (index + 1);
                emit(emitter, event(sessionId, runId, sequence, AgentEventType.IMAGE, AgentEventStatus.COMPLETE,
                        "ImageToolchain", map("assetId", image.assetId(), "title", title, "imageUrl", image.imageUrl(), "sourceTool", toolId, "content", "archived to COS")));
            }
            emit(emitter, event(sessionId, runId, sequence, AgentEventType.SUMMARY, AgentEventStatus.COMPLETE,
                    "SummaryAgent", map("title", "Tool workflow delivery", "content", displayName(toolId) + " completed and archived " + archivedImages.size() + " image asset(s) to COS", "assetId", archived.assetId())));
            emit(emitter, event(sessionId, runId, sequence, AgentEventType.RUN_COMPLETED, AgentEventStatus.COMPLETE,
                    "ToolWorkflow", map("message", "run completed", "assetId", archived.assetId())));
            historyStore.completeRun(sessionId, runId, AgentEventStatus.COMPLETE);
            emitter.complete();
        } catch (Exception error) {
            log.error("Image tool workflow {} failed", runId, error);
            historyStore.completeRun(sessionId, runId, AgentEventStatus.FAILED);
            try {
                emit(emitter, event(sessionId, runId, sequence, AgentEventType.TOOL_RESULT, AgentEventStatus.FAILED,
                        "ToolRouter", map("tool", toolId, "title", displayName(toolId) + " failed", "content", concise(error.getMessage()))));
                emit(emitter, event(sessionId, runId, sequence, AgentEventType.ERROR, AgentEventStatus.FAILED,
                        "ToolWorkflow", map("code", "IMAGE_TOOL_FAILED", "message", concise(error.getMessage()))));
                emitter.complete();
            } catch (Exception sendError) {
                emitter.completeWithError(error);
            }
        }
    }

    private ImageToolProviderResult executeProvider(String toolId, AgentToolWorkflowRequest request,
                                                    List<String> inputUrls, String ownerUserId) {
        AgentToolWorkflowProperties.Endpoint config = properties.forTool(toolId);
        String prompt = promptFor(toolId, request.prompt(), request.parameters());
        NativeImageToolProvider nativeProvider = nativeProvider(toolId, request.modelProvider());
        if (nativeProvider != null) return nativeProvider.execute(toolId, request, inputUrls);
        if (config.isEnabled() && config.getEndpoint() != null && !config.getEndpoint().isBlank()) {
            return configuredProvider.execute(toolId, config, prompt, inputUrls, request.parameters());
        }
        if (!properties.isFallbackToImageProvider() || !MODEL_WORKFLOWS.contains(toolId)) {
            throw new IllegalStateException(toolId + " provider is not configured; select a configured dedicated model in the toolbox or set its AGENT_GATEWAY_* values");
        }
        String mode = config.getMode() == null ? "edit" : config.getMode().trim().toLowerCase();
        String requestedProvider = request.modelProvider() == null || "auto".equalsIgnoreCase(request.modelProvider()) ? null : request.modelProvider();
        AgentToolGatewayRequest providerRequest = new AgentToolGatewayRequest(prompt, inputUrls,
                inputUrls.isEmpty() || "generate".equals(mode) ? "image_generate" : "image_edit", requestedProvider, ownerUserId);
        ImageModelResult result = providerRequest.task_type().equals("image_generate")
                ? imageProviderRouter.generate(providerRequest) : imageProviderRouter.edit(providerRequest);
        return new ImageToolProviderResult(result.imageUrl(), null, "image/png", result.text(), result.provider());
    }

    private NativeImageToolProvider nativeProvider(String toolId, String requestedProvider) {
        String requested = requestedProvider == null ? "" : requestedProvider.trim().toLowerCase(java.util.Locale.ROOT);
        if (!requested.isBlank() && !"auto".equals(requested)) {
            NativeImageToolProvider selected = nativeProviders.stream().filter(provider -> provider.id().equalsIgnoreCase(requested)).findFirst().orElse(null);
            if (selected != null) {
                if (!selected.supports(toolId)) throw new IllegalArgumentException("Model provider " + requested + " does not support tool " + toolId);
                if (!selected.isConfigured()) throw new IllegalStateException("Model provider " + requested + " is not configured for " + toolId);
                return selected;
            }
            return null;
        }
        if (configEnabled(toolId)) return null;
        return nativeProviders.stream().filter(provider -> provider.supports(toolId) && provider.isConfigured()).findFirst().orElse(null);
    }

    private boolean configEnabled(String toolId) {
        AgentToolWorkflowProperties.Endpoint config = properties.forTool(toolId);
        return config.isEnabled() && config.getEndpoint() != null && !config.getEndpoint().isBlank();
    }

    private AgentToolWorkflowProperties.Endpoint archiveConfig(String toolId, AgentToolWorkflowProperties.Endpoint configured, String providerId) {
        NativeImageToolProvider nativeProvider = nativeProviders.stream()
                .filter(provider -> provider.supports(toolId) && provider.isConfigured()
                        && provider.id().equalsIgnoreCase(providerId == null ? "" : providerId))
                .findFirst().orElse(null);
        AgentToolWorkflowProperties.Endpoint fallback = new AgentToolWorkflowProperties.Endpoint();
        fallback.setEnabled(true);
        if (nativeProvider != null) {
            fallback.setEndpoint(nativeProvider.endpoint());
            fallback.setResultHostSuffixes(nativeProvider.resultHostSuffixes());
            return fallback;
        }
        if (configured.isEnabled() && configured.getEndpoint() != null && !configured.getEndpoint().isBlank()) return configured;
        fallback.setEndpoint("https://dashscope.aliyuncs.com");
        fallback.setResultHostSuffixes(".aliyuncs.com");
        return fallback;
    }

    private List<String> resolveInputs(String ownerUserId, List<String> assetIds) {
        List<String> urls = new ArrayList<>();
        for (String assetId : assetIds == null ? List.<String>of() : assetIds) {
            AgentAssetMetadata asset = assetStore.findOwned(ownerUserId, assetId)
                    .orElseThrow(() -> new IllegalArgumentException("Input asset is not owned by the current user: " + assetId));
            String url = asset.imageUrl();
            CosAgentImageStorage cosStorage = cosStorageProvider.getIfAvailable();
            CosSignedUrlService signedUrlService = signedUrlServiceProvider.getIfAvailable();
            if (cosStorage != null && asset.objectKey() != null && !asset.objectKey().isBlank()) {
                url = signedUrlService == null ? cosStorage.imageUrl(new com.jd.genie.model.agent.StoredAgentImage(asset.assetId(), asset.fileName(), asset.objectKey(), asset.mediaType(), asset.size())) : signedUrlService.createGetUrl(asset.objectKey());
            }
            urls.add(url);
        }
        return urls;
    }

    private String promptFor(String toolId, String userPrompt, Map<String, Object> parameters) {
        String extra = userPrompt == null ? "" : userPrompt.trim();
        String base = switch (toolId) {
            case "image-upscale" -> "Upscale the input image, restore detail, preserve identity and original colors.";
            case "seedvr2" -> "Use SeedVR2 ultra enhancement: denoise, deblur and restore fine detail without changing the subject.";
            case "image-layered" -> "Separate the image into semantic subject and background layers with clean edges and transparent layer outputs.";
            case "product-refinement" -> "Create a clean premium e-commerce product refinement image on a pure white studio background.";
            case "product-detail-image" -> "Create a polished e-commerce product detail image with clear product presentation and consistent lighting.";
            case "ecommerce-promotion-poster" -> "Create an e-commerce promotion poster from the product image with strong visual hierarchy and room for promotional copy.";
            case "character-setting-sheet" -> "Create a consistent character setting sheet from the reference image, showing a unified design and key details.";
            case "emoji-sticker" -> "Create a fun sticker sheet based on the reference character, with multiple expressive poses and clean composition.";
            default -> "Process the input image.";
        };
        return extra.isBlank() ? base + " Parameters: " + parameters : base + " Additional requirements: " + extra + ". Parameters: " + parameters;
    }

    private AgentEvent event(String sessionId, String runId, AtomicLong sequence, AgentEventType type,
                             AgentEventStatus status, String agent, Map<String, Object> payload) {
        return new AgentEvent(SCHEMA_VERSION, UUID.randomUUID().toString(), sessionId, runId,
                sequence.incrementAndGet(), type, status, agent, Instant.now(), payload);
    }

    private void emit(SseEmitter emitter, AgentEvent event) throws IOException {
        historyStore.append(event);
        emitter.send(SseEmitter.event().id(event.eventId()).name("agent-event").data(event, MediaType.APPLICATION_JSON));
    }

    private Map<String, Object> map(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) result.put(String.valueOf(values[i]), values[i + 1]);
        return result;
    }

    private void validateToolId(String toolId) {
        properties.forTool(toolId);
    }

    private String displayName(String toolId) {
        return switch (toolId) {
            case "image-upscale" -> "Image Upscale"; case "seedvr2" -> "SeedVR2 Enhance"; case "image-layered" -> "Image Layering";
            case "product-refinement" -> "Product Refinement"; case "product-detail-image" -> "Product Detail Image";
            case "ecommerce-promotion-poster" -> "E-commerce Promotion Poster"; case "character-setting-sheet" -> "Character Setting Sheet";
            case "emoji-sticker" -> "Emoji Sticker"; default -> toolId;
        };
    }

    private String text(ImageToolProviderResult result, String toolId) {
        return result.text() == null || result.text().isBlank() ? displayName(toolId) + " completed" : result.text();
    }

    private String concise(String message) { return message == null || message.isBlank() ? "Unknown image tool error" : message.length() > 500 ? message.substring(0, 500) : message; }
}
