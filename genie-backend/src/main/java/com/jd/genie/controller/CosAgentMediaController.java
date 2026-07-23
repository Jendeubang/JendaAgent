package com.jd.genie.controller;

import com.jd.genie.model.agent.AgentImageUploadResponse;
import com.jd.genie.model.agent.StoredAgentImage;
import com.jd.genie.service.agent.AgentImageStorage;
import com.jd.genie.service.agent.CosAgentImageStorage;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * COS-specific response adapter. It returns the actual HTTPS URL instead of the local static URL.
 */
@RestController
@RequestMapping("/api/v1/agent/media")
@CrossOrigin(origins = {"http://localhost:3000", "http://127.0.0.1:3000"})
public class CosAgentMediaController extends AgentMediaController {
    private final CosAgentImageStorage cosStorage;

    public CosAgentMediaController(AgentImageStorage imageStorage, CosAgentImageStorage cosStorage) {
        super(imageStorage);
        this.cosStorage = cosStorage;
    }

    @Override
    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AgentImageUploadResponse uploadImage(@RequestPart("file") MultipartFile file, HttpServletRequest request) {
        StoredAgentImage storedImage = cosStorage.store(file);
        return new AgentImageUploadResponse(storedImage.assetId(), storedImage.originalFileName(), cosStorage.imageUrl(storedImage),
                storedImage.mediaType(), storedImage.size(), true, "cos");
    }
}
