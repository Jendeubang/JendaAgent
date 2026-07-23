package com.jd.genie.controller;

import com.jd.genie.config.QwenImageGenerateGatewayProperties;
import com.jd.genie.model.agent.AgentToolGatewayRequest;
import com.jd.genie.service.agent.GeneratedImageCosArchiver;
import com.jd.genie.service.agent.ImageModelProviderRouter;
import com.jd.genie.service.agent.ImageModelResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/** Internal-only Qwen image-generation adapter consumed by HttpAgentToolClient. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/agent-tools")
public class QwenImageGenerateToolGatewayController {
    private final QwenImageGenerateGatewayProperties properties;
    private final ImageModelProviderRouter providerRouter;
    private final GeneratedImageCosArchiver imageCosArchiver;

    @PostMapping("/qwen-image-generate")
    public Map<String, String> generate(@RequestHeader(value = "Authorization", required = false) String authorization,
                                        @Valid @RequestBody AgentToolGatewayRequest request) {
        requireInternalKey(authorization);
        if (!"image_generate".equalsIgnoreCase(request.task_type())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "task_type must be image_generate");
        }
        ImageModelResult result = providerRouter.generate(request);
        if (result.archivedToCos()) {
            return Map.of(
                    "image_url", result.imageUrl(),
                    "text", result.text() + "; archived to COS",
                    "provider", result.provider() + "-cos");
        }
        GeneratedImageCosArchiver.ArchiveResult archived = imageCosArchiver.archive(result.imageUrl());
        String text = archived.archived()
                ? result.text() + "; archived to COS"
                : result.text() + "; COS archive fallback: " + archived.detail();
        return Map.of(
                "image_url", archived.imageUrl(),
                "text", text,
                "provider", archived.archived() ? result.provider() + "-cos" : result.provider());
    }

    private void requireInternalKey(String authorization) {
        if (properties.getInternalKey() == null || properties.getInternalKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Image generation internal key is not configured");
        }
        if (!("Bearer " + properties.getInternalKey()).equals(authorization)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid image generation internal key");
        }
    }
}
