package com.jd.genie.controller;

import com.jd.genie.config.QwenOcrGatewayProperties;
import com.jd.genie.model.agent.AgentToolGatewayRequest;
import com.jd.genie.service.agent.QwenOcrGatewayClient;
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

/** Internal-only adapter endpoint consumed by HttpAgentToolClient. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/agent-tools")
public class QwenOcrToolGatewayController {
    private final QwenOcrGatewayProperties properties;
    private final QwenOcrGatewayClient client;

    @PostMapping("/qwen-ocr")
    public Map<String, String> recognize(@RequestHeader(value = "Authorization", required = false) String authorization,
                                         @Valid @RequestBody AgentToolGatewayRequest request) {
        requireInternalKey(authorization);
        if (!"ocr".equalsIgnoreCase(request.task_type())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "task_type must be ocr");
        }
        if (request.image_urls() == null || request.image_urls().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OCR requires at least one image_url");
        }
        return Map.of("text", client.recognize(request), "provider", "qwen-ocr");
    }

    private void requireInternalKey(String authorization) {
        if (properties.getInternalKey() == null || properties.getInternalKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "OCR internal key is not configured");
        }
        if (!authorization.equals("Bearer " + properties.getInternalKey())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid OCR internal key");
        }
    }
}
