package com.jd.genie.controller;

import com.jd.genie.config.QwenImageEditGatewayProperties;
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

/** Internal-only image-edit adapter for background, style, and local image edits. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/agent-tools")
public class QwenImageEditToolGatewayController {
    private final QwenImageEditGatewayProperties properties;
    private final ImageModelProviderRouter providerRouter;
    private final GeneratedImageCosArchiver imageCosArchiver;

    @PostMapping("/qwen-image-edit")
    public Map<String, String> edit(@RequestHeader(value = "Authorization", required = false) String authorization,
                                    @Valid @RequestBody AgentToolGatewayRequest request) {
        requireInternalKey(authorization);
        if (!"image_edit".equalsIgnoreCase(request.task_type())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "task_type must be image_edit");
        }
        if (request.image_urls() == null || request.image_urls().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "image_edit requires at least one image_url");
        }
        ImageModelResult result = providerRouter.edit(request);
        if (result.archivedToCos()) {
            return Map.of(
                    "image_url", result.imageUrl(),
                    "text", result.text() + "; archived to COS",
                    "provider", result.provider() + "-image-edit-cos");
        }
        GeneratedImageCosArchiver.ArchiveResult archived = imageCosArchiver.archive(result.imageUrl());
        String text = archived.archived()
                ? result.text() + "; archived to COS"
                : result.text() + "; COS archive fallback: " + archived.detail();
        return Map.of(
                "image_url", archived.imageUrl(),
                "text", text,
                "provider", archived.archived() ? result.provider() + "-image-edit-cos" : result.provider() + "-image-edit");
    }

    private void requireInternalKey(String authorization) {
        if (properties.getInternalKey() == null || properties.getInternalKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Image edit internal key is not configured");
        }
        if (!("Bearer " + properties.getInternalKey()).equals(authorization)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid image edit internal key");
        }
    }
}
