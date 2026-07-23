package com.jd.genie.controller;

import com.jd.genie.model.agent.AgentImageUploadResponse;
import com.jd.genie.model.agent.StoredAgentImage;
import com.jd.genie.service.agent.AgentImageStorage;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Uploads an image and returns the URL to pass as AgentRunRequest.imageUrls.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/agent/media")
@CrossOrigin(origins = {"http://localhost:3000", "http://127.0.0.1:3000"})
public class AgentMediaController {
    private final AgentImageStorage imageStorage;

    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AgentImageUploadResponse uploadImage(@RequestPart("file") MultipartFile file, HttpServletRequest request) {
        StoredAgentImage storedImage = imageStorage.store(file);
        String imageUrl = ServletUriComponentsBuilder.fromRequestUri(request)
                .replacePath(request.getContextPath() + "/uploads/agent/" + storedImage.storedFileName())
                .replaceQuery(null)
                .build()
                .toUriString();
        return new AgentImageUploadResponse(storedImage.assetId(), storedImage.originalFileName(), imageUrl,
                storedImage.mediaType(), storedImage.size(), false, "local");
    }
}
